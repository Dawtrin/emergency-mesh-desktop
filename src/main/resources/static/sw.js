/**
 * RINNSAN SCOUT — Tactical Service Worker (PWA Offline Engine)
 * Cache-First for static avionics UI assets, Network-First/Bypass for live telemetry
 */
const CACHE_NAME = 'c2-tactical-v1';

const STATIC_ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './images/c2-icon.svg',
  './css/tactical-c2.css',
  './vendor/leaflet.css',
  './vendor/leaflet.js',
  './vendor/chart.umd.js',
  './vendor/sockjs.min.js',
  './vendor/stomp.umd.js',
  './vendor/qrcode.min.js',
  './vendor/fonts/SpaceGrotesk.woff2',
  './vendor/fonts/jetbrains-mono-400.woff2',
  './vendor/fonts/jetbrains-mono-500.woff2',
  './vendor/fonts/jetbrains-mono-600.woff2',
  './vendor/fonts/jetbrains-mono-700.woff2',
  './vendor/fonts/space-grotesk-400.woff2',
  './vendor/fonts/space-grotesk-500.woff2',
  './vendor/fonts/space-grotesk-600.woff2',
  './vendor/fonts/space-grotesk-700.woff2',
  './vendor/fonts/space-grotesk-800.woff2',
  './vendor/fonts/space-grotesk.woff2',
  './js/c2-audio.js',
  './js/c2-charts.js',
  './js/c2-map.js',
  './js/c2-interagency.js',
  './js/c2-noc.js',
  './js/c2-triage.js',
  './js/c2-topology.js',
  './js/c2-audit.js',
  './js/c2-config.js',
  './js/c2-home.js',
  './js/c2-simulator.js',
  './js/c2-palette.js',
  './js/c2-app.js'
];

// Install: Cache all mission-critical static assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[SW] Pre-caching all tactical C2 assets for offline mission readiness');
      return cache.addAll(STATIC_ASSETS);
    }).then(() => self.skipWaiting())
  );
});

// Activate: Purge obsolete caches and claim clients immediately
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            console.log('[SW] Purging old tactical cache:', key);
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch: Tactical Cache-First strategy with Network fallback
self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // 1. Chỉ cache các request HTTP/HTTPS GET
  if (req.method !== 'GET') {
    return;
  }

  // 2. Không can thiệp vào telemetry real-time (API REST và WebSocket/SockJS)
  if (url.pathname.startsWith('/api/') || 
      url.pathname.startsWith('/ws-mesh') || 
      url.pathname.includes('/info')) {
    return;
  }

  // 3. Cache-First cho UI, Script, Style, Font, Icons
  event.respondWith(
    caches.match(req).then((cachedResponse) => {
      if (cachedResponse) {
        // Tải ngầm cập nhật nếu có mạng (Stale-While-Revalidate nhẹ)
        fetch(req).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            caches.open(CACHE_NAME).then((cache) => cache.put(req, networkResponse));
          }
        }).catch(() => {
          // Bỏ qua lỗi mạng khi chạy dã chiến offline
        });
        return cachedResponse;
      }

      // Chưa có trong cache -> Fetch từ mạng và nạp vào cache
      return fetch(req).then((networkResponse) => {
        if (!networkResponse || networkResponse.status !== 200 || networkResponse.type !== 'basic') {
          return networkResponse;
        }
        const responseToCache = networkResponse.clone();
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(req, responseToCache);
        });
        return networkResponse;
      }).catch(() => {
        // Nếu mất mạng hoàn toàn và yêu cầu là trang điều hướng (navigation)
        if (req.mode === 'navigate') {
          return caches.match('./index.html');
        }
      });
    })
  );
});
