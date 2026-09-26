package com.rescue.mesh.routing;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.PacketValidator;
import com.rescue.mesh.model.ValidationResult;
import com.rescue.mesh.network.SocketClient;
import com.rescue.mesh.util.PacketFactory;

import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates, de-duplicates, delivers, and forwards canonical mesh packets.
 * Routing is deliberately fixed/configured for the desktop demonstration; this
 * class does not implement route discovery or infer routes from node-ID names.
 */
public class RoutingEngine {

    public interface RoutingCallback {
        void onPacketArrived(MeshPacket packet);
        void onPacketRelayed(MeshPacket packet, int nextHop);
        void onPacketDropped(String packetId, String reason);
        void onForwardError(int nextHop, String errorMessage);
        void onDispatchReceived(MeshPacket packet);
        default void onAckReceived(MeshPacket packet) {}
        default void onRelayHeartbeat(MeshPacket packet, String remoteHost) {}
    }

    @FunctionalInterface
    public interface PacketSender {
        boolean send(String host, int port, MeshPacket packet);
    }

    /** A configured, usable TCP endpoint. */
    public record RouteEndpoint(String host, int port) {
        public RouteEndpoint {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("route host must not be blank");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("route port must be from 1 to 65535: " + port);
            }
            host = host.trim();
        }

        @Override
        public String toString() {
            return host + ':' + port;
        }
    }

    private final String myNodeId;
    private final RouteEndpoint upstreamEndpoint;
    private final SeenPacketCache seenPacketCache;
    private final RoutingCallback callback;
    private final PacketSender packetSender;
    private final Gson gson;
    private final Map<String, RouteEndpoint> destinationRoutes = new ConcurrentHashMap<>();
    private volatile boolean baseStation;
    private volatile int listenPort;
    private final java.util.concurrent.atomic.AtomicInteger activeSends = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger completedSends = new java.util.concurrent.atomic.AtomicInteger();

    public int getActiveSends() { return activeSends.get(); }
    public int getCompletedSends() { return completedSends.get(); }
    public void setBaseStation(boolean value) { baseStation = value; }
    public void setListenPort(int value) { listenPort = value; }

    public RoutingEngine(String myNodeId,
                         String nextHopHost,
                         int nextHopPort,
                         RoutingCallback callback) {
        this(myNodeId, nextHopHost, nextHopPort, callback, SocketClient::send);
    }

    public RoutingEngine(String myNodeId,
                         String nextHopHost,
                         int nextHopPort,
                         RoutingCallback callback,
                         PacketSender packetSender) {
        if (myNodeId == null || myNodeId.isBlank()) {
            throw new IllegalArgumentException("myNodeId must not be blank");
        }
        this.myNodeId = myNodeId.trim();
        this.upstreamEndpoint = nextHopPort > 0
                ? new RouteEndpoint(nextHopHost, nextHopPort)
                : null;
        this.callback = Objects.requireNonNull(callback, "callback");
        this.packetSender = Objects.requireNonNull(packetSender, "packetSender");
        this.seenPacketCache = new SeenPacketCache();
        this.gson = new Gson();
    }

    /**
     * Adds an explicit reverse/downstream route owned by this engine instance.
     */
    public void recordRoute(String nodeId, String host, int port) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("route nodeId must not be blank");
        }
        destinationRoutes.put(nodeId.trim(), new RouteEndpoint(host, port));
    }

    /** Compatibility overload using the configured upstream host. */
    public void recordRoute(String nodeId, int port) {
        String host = upstreamEndpoint != null ? upstreamEndpoint.host() : "127.0.0.1";
        recordRoute(nodeId, host, port);
    }

    public void removeRoute(String nodeId) {
        if (nodeId != null) {
            destinationRoutes.remove(nodeId);
        }
    }

    public RouteEndpoint getRoute(String nodeId) {
        return nodeId == null ? null : destinationRoutes.get(nodeId);
    }

    public void processPacket(MeshPacket packet) {
        processPacket(packet, null);
    }

    public void processPacket(MeshPacket packet, String remoteHost) {
        if (packet == null) {
            callback.onPacketDropped("UNKNOWN", "PARSE_ERROR: packet is null");
            return;
        }

        String packetId = packet.getPacketId();
        if (packetId == null || packetId.isBlank()) {
            callback.onPacketDropped("UNKNOWN", "PARSE_ERROR: packetId is blank");
            return;
        }

        try {
            ValidationResult validation = PacketValidator.validate(packet);
            if (!validation.isValid()) {
                drop(packetId, "VALIDATION_FAILED: " + validation.getFirstViolation());
                return;
            }

            if (!packet.verifyChecksum(gson)) {
                drop(packetId, "CHECKSUM_FAIL");
                return;
            }

            if (isDestination(packet) && MeshPacket.TYPE_DISPATCH_COMMAND.equals(packet.getPacketType())
                    && remoteHost != null && packet.getPayload().getDiscoveryInfo() != null
                    && packet.getPayload().getDiscoveryInfo().isJsonObject()) {
                com.google.gson.JsonObject reply = packet.getPayload().getDiscoveryInfo().getAsJsonObject();
                if (reply.has("reply_port")) {
                    int port = reply.get("reply_port").getAsBigDecimal().intValueExact();
                    recordRoute(packet.getSourceNodeId(), remoteHost, port);
                }
            }

            if (seenPacketCache.checkAndMark(packetId)) {
                if (MeshPacket.TYPE_DISPATCH_COMMAND.equals(packet.getPacketType())) {
                    if (isDestination(packet)) sendAckForDispatch(packet);
                    else forwardPacket(packet);
                    return;
                }
                drop(packetId, "DUPLICATE");
                return;
            }

            System.out.println("[RECV] [" + myNodeId + "] " + shortId(packetId)
                    + " type=" + packet.getPacketType()
                    + " from=" + packet.getSenderHopId()
                    + " TTL=" + packet.getTtl()
                    + " hops=" + packet.getHopCount());

            switch (packet.getPacketType()) {
                case MeshPacket.TYPE_SOS_BROADCAST, MeshPacket.TYPE_SOS_DATA -> processSosPacket(packet);
                case MeshPacket.TYPE_DISPATCH_COMMAND, MeshPacket.TYPE_DISPATCH_CMD -> processDispatchPacket(packet);
                case MeshPacket.TYPE_ACK -> processAckPacket(packet);
                case MeshPacket.TYPE_HEARTBEAT -> {
                    if (isDestination(packet) && remoteHost != null) {
                        callback.onRelayHeartbeat(packet, remoteHost);
                    }
                    processHeartbeat(packet);
                }
                case MeshPacket.TYPE_ROUTE_DISCOVERY -> {
                    if (isDestination(packet)) {
                        callback.onPacketArrived(packet);
                    } else {
                        forwardPacket(packet);
                    }
                }
                default -> drop(packetId, "UNKNOWN_TYPE");
            }
        } catch (RuntimeException e) {
            drop(packetId, "MALFORMED_PACKET: " + safeMessage(e));
        }
    }

    private void processSosPacket(MeshPacket packet) {
        if (isDestination(packet)) {
            callback.onPacketArrived(packet);
        } else {
            forwardPacket(packet);
        }
    }

    private void processDispatchPacket(MeshPacket packet) {
        if (isDestination(packet)) {
            callback.onDispatchReceived(packet);
            sendAckForDispatch(packet);
        } else {
            forwardPacket(packet);
        }
    }

    private void processAckPacket(MeshPacket packet) {
        if (isDestination(packet)) {
            callback.onAckReceived(packet);
        } else {
            forwardPacket(packet);
        }
    }

    private void processHeartbeat(MeshPacket packet) {
        if (isDestination(packet)) {
            callback.onPacketArrived(packet);
        } else if (upstreamEndpoint != null) {
            forwardPacket(packet);
        }
    }

    private boolean isDestination(MeshPacket packet) {
        return myNodeId.equals(packet.getDestinationNodeId())
                || (baseStation && MeshPacket.NODE_BASE_STATION.equals(packet.getDestinationNodeId()));
    }

    private void sendAckForDispatch(MeshPacket dispatchPacket) {
        String ackDestination = dispatchPacket.getSourceNodeId();
        if (ackDestination == null || ackDestination.isBlank()) {
            drop(dispatchPacket.getPacketId(), "MISSING_ACK_DESTINATION");
            return;
        }
        RouteEndpoint learned = destinationRoutes.get(ackDestination);
        if (learned == null && upstreamEndpoint == null) {
            drop(dispatchPacket.getPacketId(), "NO_NEXT_HOP_FOR_ACK");
            return;
        }
        MeshPacket ack = PacketFactory.createAck(myNodeId, dispatchPacket.getPacketId(), ackDestination);
        sendUnmodified(ack, learned != null ? learned : upstreamEndpoint);
    }

    /**
     * Resolves the endpoint before mutation, then creates one forwarding copy.
     * The caller-owned packet remains unchanged. The forwarding copy receives
     * exactly one TTL decrement, hop increment, route append, sender-hop update,
     * and checksum recomputation regardless of transport retries.
     */
    private void forwardPacket(MeshPacket packet) {
        if (packet.getTtl() <= 1) {
            drop(packet.getPacketId(), "TTL_EXPIRED");
            return;
        }

        RouteEndpoint target = resolveEndpoint(packet);
        if (target == null) {
            drop(packet.getPacketId(), "NO_NEXT_HOP: " + packet.getDestinationNodeId());
            return;
        }

        MeshPacket forwarded = MeshPacket.fromJson(packet.toJson(gson), gson);
        if (forwarded == null) {
            drop(packet.getPacketId(), "FORWARD_COPY_FAILED");
            return;
        }
        forwarded.setTtl(forwarded.getTtl() - 1);
        forwarded.setHopCount(forwarded.getHopCount() + 1);
        forwarded.addToRouteHistory(myNodeId);
        forwarded.setSenderHopId(myNodeId);
        if (listenPort > 0 && MeshPacket.TYPE_DISPATCH_COMMAND.equals(forwarded.getPacketType())) {
            com.google.gson.JsonObject reply = forwarded.getPayload().getDiscoveryInfo() != null
                    && forwarded.getPayload().getDiscoveryInfo().isJsonObject()
                    ? forwarded.getPayload().getDiscoveryInfo().getAsJsonObject().deepCopy()
                    : new com.google.gson.JsonObject();
            reply.addProperty("reply_port", listenPort);
            forwarded.getPayload().setDiscoveryInfo(reply);
        }
        forwarded.computeAndSetChecksum(gson);

        send(forwarded, target);
    }

    private RouteEndpoint resolveEndpoint(MeshPacket packet) {
        RouteEndpoint explicit = destinationRoutes.get(packet.getDestinationNodeId());
        if (explicit != null) {
            return explicit;
        }
        if (MeshPacket.TYPE_DISPATCH_COMMAND.equals(packet.getPacketType())
                || MeshPacket.TYPE_DISPATCH_CMD.equals(packet.getPacketType())) {
            return null;
        }
        return upstreamEndpoint;
    }

    private void sendUnmodified(MeshPacket packet, RouteEndpoint target) {
        send(packet, target);
    }

    private void send(MeshPacket packet, RouteEndpoint target) {
        logTransport(packet, target, "FORWARDING");
        boolean sent;
        activeSends.incrementAndGet();
        try {
            sent = packetSender.send(target.host(), target.port(), packet);
        } catch (RuntimeException e) {
            callback.onForwardError(target.port(), target + ": " + safeMessage(e));
            return;
        } finally {
            activeSends.decrementAndGet();
        }
        if (sent) {
            completedSends.incrementAndGet();
            logTransport(packet, target, MeshPacket.TYPE_DISPATCH_COMMAND.equals(packet.getPacketType())
                    || MeshPacket.TYPE_DISPATCH_CMD.equals(packet.getPacketType()) ? "WAITING_FOR_ACK" : "SENT");
            callback.onPacketRelayed(packet, target.port());
        } else {
            logTransport(packet, target, "FAILED");
            callback.onForwardError(target.port(), "Send failed to " + target);
        }
    }

    private void logTransport(MeshPacket packet, RouteEndpoint target, String state) {
        // Never include SOS payload/body here: packet metadata is sufficient for diagnosis.
        System.out.println("[NET] ts=" + Instant.now() + " node=" + myNodeId
                + " packet=" + shortId(packet.getPacketId()) + " direction=OUT endpoint="
                + target + " state=" + state);
    }

    private void drop(String packetId, String reason) {
        System.out.println("[DROP] [" + myNodeId + "] " + shortId(packetId) + " — " + reason);
        callback.onPacketDropped(packetId, reason);
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    private String shortId(String packetId) {
        if (packetId == null) return "null";
        return packetId.length() > 8 ? packetId.substring(0, 8) : packetId;
    }

    public void shutdown() {
        seenPacketCache.shutdown();
        destinationRoutes.clear();
        System.out.println("[INFO] [" + myNodeId + "] RoutingEngine đã shutdown.");
    }

    public String getMyNodeId() { return myNodeId; }
    public int getNextHopPort() { return upstreamEndpoint != null ? upstreamEndpoint.port() : -1; }
    public String getNextHopHost() { return upstreamEndpoint != null ? upstreamEndpoint.host() : null; }
    public int getCacheSize() { return seenPacketCache.size(); }
    public RoutingCallback getCallback() { return callback; }
}
