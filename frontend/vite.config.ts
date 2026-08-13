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
      // injectManifest (not generateSW) is required to add custom push/notificationclick
      // listeners — generateSW auto-generates the whole service worker with no room for custom
      // event handlers. src/sw.ts now explicitly re-implements everything generateSW used to do
      // implicitly (precaching, skipWaiting/clientsClaim, the NetworkFirst navigation rule
      // documented below) before adding the push handlers — see that file for the full picture.
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
