import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'favicon-32x32.png', 'favicon-16x16.png', 'apple-touch-icon-180x180.png'],
      workbox: {
        // Default generateSW behavior serves every navigation from this service worker's own
        // precached index.html, forever, until the worker itself is replaced. If a user's browser
        // is still running an older worker when a new deploy ships (old hashed JS/CSS removed from
        // the server), that stale index.html points at asset files that now 404 — the app never
        // boots and the self-update-and-reload logic in main.tsx never gets a chance to run, since
        // it lives inside the very bundle that failed to load. NetworkFirst here means every launch
        // tries the network for the current index.html first (which always reflects the live
        // deployment), only falling back to the cached copy when offline — so a stale worker can no
        // longer strand a user on a blank screen after a deploy, while offline support is preserved.
        // vite-plugin-pwa defaults navigateFallback to "index.html", which makes workbox-build also
        // emit its own precache-bound NavigationRoute ahead of this rule — and since Workbox routes
        // match in registration order, that silently wins over the NetworkFirst handler below. Must
        // be explicitly unset (not just omitted) to actually suppress that default.
        navigateFallback: undefined,
        runtimeCaching: [
          {
            urlPattern: ({ request }) => request.mode === 'navigate',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'studen-navigations',
              networkTimeoutSeconds: 4,
            },
          },
        ],
        cleanupOutdatedCaches: true,
      },
      manifest: {
        name: 'StuDen',
        short_name: 'StuDen',
        description: 'Need something done? Find a student who can do it.',
        theme_color: '#2563EB',
        background_color: '#FFFFFF',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          {
            src: 'maskable-icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
