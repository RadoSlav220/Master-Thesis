import axios from "axios";

/**
 * Base URL is configurable via VITE_API_URL. In development we default to the
 * Vite dev-server proxy prefix ("/api") which forwards to the Spring Boot backend
 * (see vite.config.ts), avoiding browser CORS without any backend change.
 */
const baseURL = import.meta.env.VITE_API_URL ?? "/api";

export const apiClient = axios.create({
  baseURL,
  headers: { "Content-Type": "application/json" },
});
