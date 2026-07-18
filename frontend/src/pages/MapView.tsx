import {
  Alert,
  Box,
  Card,
  CardContent,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useMemo } from "react";
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
import ResultMap from "../map/ResultMap";
import { useExecutions } from "../hooks/useExecutions";
import { useSelectionStore } from "../store/selectionStore";
import { parseResultPoints } from "../utils/resultParser";

export default function MapView() {
  const executions = useExecutions();
  const { selectedExecutionId, setSelectedExecution } = useSelectionStore();

  const completed = (executions.data ?? []).filter((e) => e.status === "COMPLETED");
  const selected = completed.find((e) => e.id === selectedExecutionId) ?? null;
  const points = useMemo(() => parseResultPoints(selected), [selected]);

  const chartData = points.map((p, i) => ({ name: `#${i + 1}`, value: p.value, label: p.label }));

  return (
    <>
      <PageHeader title="Map Visualization" />

      <TextField
        select
        label="Completed execution"
        value={selectedExecutionId && selected ? selectedExecutionId : ""}
        onChange={(e) => setSelectedExecution(e.target.value || null)}
        sx={{ minWidth: 320, mb: 3 }}
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

      {!selected && <Alert severity="info">Select a completed execution to visualize its results.</Alert>}

      {selected && (
        <Stack spacing={3} sx={{}}>
          <ResultMap points={points} />
          {chartData.length > 0 && (
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Values ({points[0].label})
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
