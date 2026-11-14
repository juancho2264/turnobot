import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// base: './' → las rutas de los archivos son relativas, así funciona en GitHub Pages
// (que publica en /turnobot/ y no en la raíz del dominio)
export default defineConfig({
  plugins: [react()],
  base: './',
});
