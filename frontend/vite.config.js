import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In dev, the UI calls /api/* and Vite forwards it to the API gateway.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8090", changeOrigin: true },
    },
  },
});
