import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    // 開発時は API（./mvnw spring-boot:run、ポート 8081）へ転送し、本番と同じく同一オリジンにする
    proxy: { '/api': 'http://localhost:8081' },
  },
  test: {
    environment: 'jsdom',
  },
})
