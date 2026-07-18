import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { Box, Typography } from "@mui/material";
import type { MapPoint } from "../types";

/** Maps a normalized value in [min,max] to a color from green -> red. */
function colorForValue(value: number, min: number, max: number): string {
  const t = max > min ? (value - min) / (max - min) : 0.5;
  const hue = 120 - Math.round(t * 120); // 120=green, 0=red
  return `hsl(${hue}, 80%, 45%)`;
}

export default function ResultMap({ points }: { points: MapPoint[] }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<maplibregl.Marker[]>([]);

  // Initialize the map once.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    mapRef.current = new maplibregl.Map({
      container: containerRef.current,
      style: "https://demotiles.maplibre.org/style.json",
      center: [23.32, 42.7],
      zoom: 10,
    });
    mapRef.current.addControl(new maplibregl.NavigationControl(), "top-right");
    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  // Re-render markers whenever points change.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];

    if (points.length === 0) return;

    const values = points.map((p) => p.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const bounds = new maplibregl.LngLatBounds();

    points.forEach((p) => {
      const el = document.createElement("div");
      el.style.width = "16px";
      el.style.height = "16px";
      el.style.borderRadius = "50%";
      el.style.border = "2px solid white";
      el.style.background = colorForValue(p.value, min, max);
      el.style.boxShadow = "0 0 4px rgba(0,0,0,0.4)";

      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([p.longitude, p.latitude])
        .setPopup(new maplibregl.Popup({ offset: 12 }).setText(`${p.label}: ${p.value}`))
        .addTo(map);
      markersRef.current.push(marker);
      bounds.extend([p.longitude, p.latitude]);
    });

    if (!bounds.isEmpty()) {
      map.fitBounds(bounds, { padding: 60, maxZoom: 13, duration: 500 });
    }
  }, [points]);

  return (
    <Box>
      <div ref={containerRef} style={{ width: "100%", height: 480, borderRadius: 8 }} />
      {points.length === 0 && (
        <Typography sx={{ color: "text.secondary", mt: 1 }}>
          No geospatial points in this result.
        </Typography>
      )}
    </Box>
  );
}
