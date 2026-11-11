import json
import socket
import time
import uuid
import hashlib

def sha256_payload(payload_dict):
    # Chuẩn hóa JSON theo Gson formatting (không khoảng trắng sau dấu hai chấm và phẩy)
    json_str = json.dumps(payload_dict, separators=(',', ':'), ensure_ascii=False)
    return hashlib.sha256(json_str.encode('utf-8')).hexdigest()

def test_mobile_emulator():
    host = '127.0.0.1'
    port = 8888
    node_id = "NODE_" + uuid.uuid4().hex[:8].upper()
    packet_id = str(uuid.uuid4())

    print(f"==================================================")
    print(f"📱 MOBILE EMULATOR (Giả lập điện thoại Android)")
    print(f"Node ID: {node_id}")
    print(f"SOS Packet ID: {packet_id}")
    print(f"==================================================")

    payload = {
        "senderName": "Trần Mobile Test",
        "alertType": "FLOOD_TRAPPED",
        "message": "Nước dâng cao, giả lập Mobile kiểm tra 2 chiều",
        "victimCount": 2,
        "severity": "CRITICAL",
        "location": {
            "latitude": 16.0678,
            "longitude": 108.2145,
            "hasFix": True,
            "accuracyM": 10,
            "fixAgeMs": 500
        },
        "phoneNumber": "0905123456",
        "bloodType": "O+",
        "medicalNotes": "Giả lập từ emulator.py"
    }

    checksum = sha256_payload(payload)

    sos_packet = {
        "packetId": packet_id,
        "packetType": "SOS_DATA",
        "sourceNodeId": node_id,
        "destinationNodeId": "BASE_STATION",
        "senderHopId": node_id,
        "ttl": 5,
        "hopCount": 0,
        "timestamp": int(time.time() * 1000),
        "payload": payload,
        "routeHistory": [node_id],
        "checksum": checksum
    }

    print("\n[1] Đang kết nối tới Base Station qua TCP 8888...")
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5.0)
    s.connect((host, port))

    packet_json_line = json.dumps(sos_packet, separators=(',', ':'), ensure_ascii=False) + "\n"
    s.sendall(packet_json_line.encode('utf-8'))
    print(" -> Đã gửi gói tin SOS_DATA thành công!")

    print("\n[2] Đang đọc phản hồi trên cùng kết nối TCP (Two-Way Handshake)...")
    buffer = ""
    start_time = time.time()
    ack_received = False
    dispatches = []

    try:
        while time.time() - start_time < 3.0:
            data = s.recv(4096)
            if not data:
                break
            buffer += data.decode('utf-8')
            lines = buffer.split("\n")
            buffer = lines[-1]
            for line in lines[:-1]:
                if not line.strip():
                    continue
                resp_pkt = json.loads(line)
                pkt_type = resp_pkt.get("packetType")
                print(f" -> Nhận phản hồi từ Base: packetType={pkt_type}")
                if pkt_type == "ACK":
                    p = resp_pkt.get("payload", {})
                    if p.get("ackedPacketId") == packet_id and p.get("ackType") == "RECEIVED":
                        ack_received = True
                        print(f"    ✅ XÁC NHẬN: Base Station đã nhận được SOS (ACK RECEIVED)")
                elif pkt_type == "DISPATCH_CMD":
                    dispatches.append(resp_pkt)
                    print(f"    🚨 NHẬN LỆNH ĐIỀU PHỐI: {resp_pkt.get('payload', {}).get('message')}")
    except socket.timeout:
        pass
    finally:
        s.close()

    print("\n[3] Tổng kết kết quả thử nghiệm:")
    if ack_received:
        print(" -> ✅ PASS: Base Station phản hồi ACK RECEIVED trên cùng socket!")
    else:
        print(" -> ❌ FAIL: Chưa nhận được ACK RECEIVED từ Base Station!")

if __name__ == '__main__':
    test_mobile_emulator()
