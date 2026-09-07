import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  cacheDir: '.vite-cache',
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:18000',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'happy-dom',
    restoreMocks: true,
  },
});
