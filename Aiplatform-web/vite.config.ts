import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

const apiTarget = process.env.AI_API_TARGET || 'http://127.0.0.1:18080'
const webPort = Number(process.env.AI_WEB_PORT || 3000)

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['brand/icon-macos-default.png', 'robots.txt', 'apple-touch-icon.png'],
      manifest: {
        name: '凌点 AI - AI模型调度网关平台',
        short_name: '凌点AI',
        description: '新一代AI模型调度平台，支持多种大模型一键调用',
        theme_color: '#0F172A',
        background_color: '#0F172A',
        display: 'standalone',
        orientation: 'portrait',
        scope: '/',
        start_url: '/',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any maskable',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        // cacheId 变化会让 sw.js 内容变化，触发 Service Worker 自动更新并重建缓存命名空间，
        // 确保用户浏览器在部署后能拿到最新的静态资源（解决文件名 hash 不变时 SW 不更新的问题）
        cacheId: 'lingdianai-v17',
        // 排除后端接口和独立部署的 OpenYRWeb 游戏路径。否则根路径下的
        // AI PWA 会把 /hj666/ 的游戏 index.html 回退成 AI index.html，
        // React Router 随后将它误判为未知页面并跳转到 /welcome。
        navigateFallbackDenylist: [/^\/api\//, /^\/hj666(?:\/|$)/],
        // 注意：不要缓存 /api/* 请求！API 数据是动态的，缓存会导致：
        // 1. 部署后用户看到过期数据（通知、对话列表等）
        // 2. SSE 流式响应被错误缓存
        // 3. 用户看到的"未读数"与实际不符
        // 后端已有 Redis 缓存层，前端 PWA 只需缓存静态资源
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-cache',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'gstatic-fonts-cache',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
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
  server: {
    host: '127.0.0.1',
    port: webPort,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
      // 文件上传/解析/带文件对话接口（前端未带 /api 前缀，需 rewrite 补上）。
      // 只代理真正的接口子路径，不能代理整个 /chat，否则会覆盖 React Router 的 /chat 页面。
      '^/chat/(stream-with-files|generate-file|sync|load)(?:/|$)': {
        target: apiTarget,
        changeOrigin: true,
        rewrite: (path) => '/api' + path,
      },
      '/v1': {
        target: apiTarget,
        changeOrigin: true,
        rewrite: (path) => '/api' + path,
      },
    },
  },
})
