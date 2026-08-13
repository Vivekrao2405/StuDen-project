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
      // 'prompt', not 'autoUpdate': autoUpdate makes vite-plugin-pwa's registerSW helper call
      // window.location.reload() UNCONDITIONALLY and WITHOUT WARNING the instant a new service
      // worker activates (confirmed by reading node_modules/vite-plugin-pwa/dist/client/build/
      // register.js directly) — since every deploy ships a new precache manifest, any user with
      // StuDen open when a deploy goes out gets yanked into a surprise reload mid-session. That
      // never actually logs anyone out (the refresh-token cookie survives a reload fine), but it
      // interrupts whatever they were doing and is exactly the kind of thing a user reports as
      // "I got logged out after a deployment". 'prompt' mode installs the new worker but leaves
      // it waiting until the user explicitly asks for it — see sw.ts's message listener and
      // main.tsx's update banner for the other half of this.
      registerType: 'prompt',
      // injectManifest (not generateSW) is required to add custom push/notificationclick
      // listeners — generateSW auto-generates the whole service worker with no room for custom
      // event handlers. src/sw.ts now explicitly re-implements everything generateSW used to do
      // implicitly (precaching, clientsClaim, the NetworkFirst navigation rule documented below)
      // before adding the push handlers — see that file for the full picture.
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      injectManifest: {
        // Keeps the generated manifest a reasonable size — same effective scope as generateSW's
        // defaults, just made explicit since injectManifest doesn't assume it for you.
        globPatterns: ['**/*.{js,css,html,ico,png,svg,webmanifest,woff2}'],
      },
      includeAssets: ['favicon.ico', 'favicon-32x32.png', 'favicon-16x16.png', 'apple-touch-icon-180x180.png'],
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
