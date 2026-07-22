import {
  Alert,
  Box,
  Card,
  CardContent,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import { useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import PageHeader from "../components/common/PageHeader";
import GeoJsonMap from "../map/GeoJsonMap";
import { useDatasets, useDatasetGeoJson } from "../hooks/useDatasets";
import { useExecutions } from "../hooks/useExecutions";
import { useSelectionStore } from "../store/selectionStore";
import {
  collectValues,
  detectValueProperty,
  parseFeatureCollection,
} from "../utils/resultParser";

type Mode = "dataset" | "result";

export default function MapView() {
  const [mode, setMode] = useState<Mode>("dataset");
  const datasets = useDatasets();
  const executions = useExecutions();
  const { selectedDatasetId, setSelectedDataset, selectedExecutionId, setSelectedExecution } =
    useSelectionStore();

  // Dataset mode data.
  const geoDatasets = (datasets.data ?? []).filter((d) => d.hasGeoJson);
  const datasetGeo = useDatasetGeoJson(
    mode === "dataset" && selectedDatasetId ? selectedDatasetId : null,
  );

  // Result mode data.
  const completed = (executions.data ?? []).filter((e) => e.status === "COMPLETED");
  const selectedExecution = completed.find((e) => e.id === selectedExecutionId) ?? null;
  const resultFc = useMemo(() => parseFeatureCollection(selectedExecution), [selectedExecution]);

  const fc = mode === "dataset" ? (datasetGeo.data ?? null) : resultFc;
  const valueProperty = useMemo(() => detectValueProperty(fc), [fc]);

  const chartData = useMemo(() => {
    if (!valueProperty) return [];
    return collectValues(fc, valueProperty).map((v, i) => ({ name: `#${i + 1}`, value: v }));
  }, [fc, valueProperty]);

  return (
    <>
      <PageHeader title="Map Visualization" />

      <ToggleButtonGroup
        exclusive
        value={mode}
        onChange={(_, v: Mode | null) => v && setMode(v)}
        sx={{ mb: 3 }}
      >
        <ToggleButton value="dataset">Dataset</ToggleButton>
        <ToggleButton value="result">Result</ToggleButton>
      </ToggleButtonGroup>

      {mode === "dataset" ? (
        <TextField
          select
          label="Dataset (with GeoJSON)"
          value={selectedDatasetId && geoDatasets.some((d) => d.id === selectedDatasetId) ? selectedDatasetId : ""}
          onChange={(e) => setSelectedDataset(e.target.value || null)}
          sx={{ minWidth: 320, mb: 3, display: "block" }}
        >
          {geoDatasets.map((d) => (
            <MenuItem key={d.id} value={d.id}>
              {d.name}
            </MenuItem>
          ))}
          {geoDatasets.length === 0 && (
            <MenuItem value="" disabled>
              No GeoJSON datasets — upload one first
            </MenuItem>
          )}
        </TextField>
      ) : (
        <TextField
          select
          label="Completed execution"
          value={selectedExecutionId && selectedExecution ? selectedExecutionId : ""}
          onChange={(e) => setSelectedExecution(e.target.value || null)}
          sx={{ minWidth: 320, mb: 3, display: "block" }}
        >
          {completed.map((e) => (
            <MenuItem key={e.id} value={e.id}>
              {e.id.slice(0, 8)}… — {new Date(e.createdAt).toLocaleString()}
            </MenuItem>
          ))}
          {completed.length === 0 && (
            <MenuItem value="" disabled>
              No completed executions
            </MenuItem>
          )}
        </TextField>
      )}

      {!fc && (
        <Alert severity="info">
          {mode === "dataset"
            ? "Select a GeoJSON dataset to preview its geometry."
            : "Select a completed execution to visualize its result."}
        </Alert>
      )}

      {fc && (
        <Stack spacing={3} sx={{}}>
          <GeoJsonMap data={fc} valueProperty={valueProperty} />
          {chartData.length > 0 && (
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Values ({valueProperty})
                </Typography>
                <Box sx={{ width: "100%", height: 300 }}>
                  <ResponsiveContainer>
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="name" />
                      <YAxis />
                      <Tooltip />
                      <Bar dataKey="value" fill="#1976d2" />
                    </BarChart>
                  </ResponsiveContainer>
                </Box>
              </CardContent>
            </Card>
          )}
        </Stack>
      )}
    </>
  );
}
