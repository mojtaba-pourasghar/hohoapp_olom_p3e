import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// `npm start` builds into dist/ and the Express server serves it, so the whole studio is one
// process on one port. `npm run dev` keeps Vite in front with the API proxied through.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { "/api": "http://127.0.0.1:5174" } },
  build: { outDir: "dist", emptyOutDir: true },
});
