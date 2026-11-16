import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// El CORS del gateway solo admite los orígenes localhost:3000 y localhost:5173.
export default defineConfig({ plugins: [react()], server: { port: 5173, strictPort: true } });
