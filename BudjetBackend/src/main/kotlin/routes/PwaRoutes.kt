package com.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.registerPwaRoutes() {
    get("/service-worker.js") {
        call.respondText(
            text = """
            'use strict';

            const CACHE_NAME = "budget-pwa-v4";
            const OFFLINE_URL = "/offline.html";

            const CORE_ASSETS = [
              "/",
              "/index.html",
              "/flutter_bootstrap.js",
              "/flutter.js",
              "/main.dart.js",
              "/manifest.json",
              "/favicon.png",
              "/version.json",
              "/canvaskit/canvaskit.js",
              "/canvaskit/canvaskit.wasm",
              "/assets/AssetManifest.bin",
              "/assets/AssetManifest.bin.json",
              "/assets/FontManifest.json",
              "/assets/fonts/MaterialIcons-Regular.otf",
              "/assets/packages/cupertino_icons/assets/CupertinoIcons.ttf",
              "/icons/Icon-192.png",
              "/icons/Icon-512.png",
              OFFLINE_URL
            ];

            self.addEventListener("install", (event) => {
              self.skipWaiting();
              event.waitUntil(
                caches.open(CACHE_NAME).then((cache) =>
                  Promise.allSettled(CORE_ASSETS.map((url) => cache.add(url)))
                )
              );
            });

            self.addEventListener("activate", (event) => {
              event.waitUntil((async () => {
                const keys = await caches.keys();
                await Promise.all(
                  keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
                );
                await self.clients.claim();
              })());
            });

            self.addEventListener("fetch", (event) => {
              const request = event.request;
              if (request.method !== "GET") return;

              const url = new URL(request.url);

              if (url.origin !== self.location.origin) return;

              if (url.pathname.startsWith("/api/")) return;

              if (request.mode === "navigate") {
                event.respondWith(
                  fetch(request).catch(() =>
                    caches.match("/index.html").then((r) => r || caches.match(OFFLINE_URL))
                  )
                );
                return;
              }

              event.respondWith(
                caches.match(request).then((cached) => {
                  if (cached) return cached;
                  return fetch(request)
                    .then((response) => {
                      if (response && response.status === 200 && response.type === "basic") {
                        const clone = response.clone();
                        caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
                      }
                      return response;
                    })
                    .catch(() => caches.match(OFFLINE_URL));
                })
              );
            });
            """.trimIndent(),
            contentType = ContentType.parse("application/javascript")
        )
    }

    get("/offline.html") {
        call.respondText(
            text = """
                <!doctype html>
                <html lang="ru">
                <head>
                  <meta charset="utf-8" />
                  <title>Budget Offline</title>
                </head>
                <body>
                  <h1>Вы офлайн</h1>
                  <p>Проверьте подключение к интернету и повторите запрос.</p>
                </body>
                </html>
            """.trimIndent(),
            contentType = ContentType.Text.Html
        )
    }
}
