'use strict';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    self.registration.unregister().catch((e) => {
      console.warn('Failed to unregister stale service worker:', e);
    })
  );
});
