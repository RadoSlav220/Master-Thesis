import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { Box, Typography } from "@mui/material";
import type { GeoJsonFeatureCollection } from "../types";
import { collectValues } from "../utils/resultParser";

interface GeoJsonMapProps {
  data: GeoJsonFeatureCollection | null;
  /** Feature property used for color scaling and point labels. */
  valueProperty?: string | null;
}

const SOURCE_ID = "geojson-data";
const EMPTY: GeoJsonFeatureCollection = { type: "FeatureCollection", features: [] };

/** Extends bounds with every coordinate pair found in an arbitrarily-nested array. */
function extendBounds(bounds: maplibregl.LngLatBounds, coords: unknown): void {
  if (!Array.isArray(coords)) return;
  if (typeof coords[0] === "number" && typeof coords[1] === "number") {
    bounds.extend([coords[0], coords[1]] as [number, number]);
    return;
  }
  for (const c of coords) extendBounds(bounds, c);
}

export default function GeoJsonMap({ data, valueProperty }: GeoJsonMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const readyRef = useRef(false);

  // Initialize the map once.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: "https://demotiles.maplibre.org/style.json",
      center: [23.32, 42.7],
      zoom: 10,
    });
    map.addControl(new maplibregl.NavigationControl(), "top-right");

    map.on("load", () => {
      map.addSource(SOURCE_ID, { type: "geojson", data: EMPTY });

      // Color scale shared by all layers, driven by the selected value property.
      const colorExpr = (prop: string | null | undefined): maplibregl.ExpressionSpecification | string => {
        if (!prop) return "#1976d2";
        return [
          "interpolate",
          ["linear"],
          ["to-number", ["get", prop], 0],
          0, "#2e7d32",
          50, "#f9a825",
          100, "#c62828",
        ];
      };

      map.addLayer({
        id: "gj-fill",
        type: "fill",
        source: SOURCE_ID,
        filter: ["match", ["geometry-type"], ["Polygon", "MultiPolygon"], true, false],
        paint: { "fill-color": colorExpr(valueProperty), "fill-opacity": 0.4 },
      });
      map.addLayer({
        id: "gj-line",
        type: "line",
        source: SOURCE_ID,
        filter: ["match", ["geometry-type"], ["LineString", "MultiLineString", "Polygon", "MultiPolygon"], true, false],
        paint: { "line-color": colorExpr(valueProperty), "line-width": 3 },
      });
      map.addLayer({
        id: "gj-circle",
        type: "circle",
        source: SOURCE_ID,
        filter: ["match", ["geometry-type"], ["Point", "MultiPoint"], true, false],
        paint: {
          "circle-radius": 7,
          "circle-color": colorExpr(valueProperty),
          "circle-stroke-color": "#ffffff",
          "circle-stroke-width": 2,
        },
      });
      map.addLayer({
        id: "gj-label",
        type: "symbol",
        source: SOURCE_ID,
        filter: ["match", ["geometry-type"], ["Point", "MultiPoint"], true, false],
        layout: {
          "text-field": valueProperty ? ["to-string", ["get", valueProperty]] : "",
          "text-size": 12,
          "text-offset": [0, 1.2],
          "text-anchor": "top",
        },
        paint: { "text-color": "#111", "text-halo-color": "#fff", "text-halo-width": 1.5 },
      });

      readyRef.current = true;
      updateData();
    });

    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      readyRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Push data + refresh paint when data or the value property changes.
  const updateData = () => {
    const map = mapRef.current;
    if (!map || !readyRef.current) return;
    const fc = data ?? EMPTY;
    (map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined)?.setData(fc);

    if (fc.features.length > 0) {
      const bounds = new maplibregl.LngLatBounds();
      for (const f of fc.features) extendBounds(bounds, f.geometry?.coordinates);
      if (!bounds.isEmpty()) map.fitBounds(bounds, { padding: 60, maxZoom: 14, duration: 500 });
    }
  };

  useEffect(() => {
    updateData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, valueProperty]);

  const isEmpty = !data || data.features.length === 0;
  const hasValues = valueProperty ? collectValues(data, valueProperty).length > 0 : false;

  return (
    <Box>
      <div ref={containerRef} style={{ width: "100%", height: 480, borderRadius: 8 }} />
      {isEmpty && (
        <Typography sx={{ color: "text.secondary", mt: 1 }}>No geometry to display.</Typography>
      )}
      {!isEmpty && valueProperty && hasValues && (
        <Typography sx={{ color: "text.secondary", mt: 1 }}>
          Colored by <strong>{valueProperty}</strong> (green → red).
        </Typography>
      )}
    </Box>
  );
}
