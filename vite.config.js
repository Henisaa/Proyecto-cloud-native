import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    open: false,
    proxy: {
      '/api/audit': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        secure: false
      },
      '/api/report': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false
      }
    }
  }
});
