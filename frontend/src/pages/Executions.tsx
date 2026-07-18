import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import PageHeader from "../components/common/PageHeader";
import StatusChip from "../components/common/StatusChip";
import JsonViewer from "../components/common/JsonViewer";
import { useDatasets } from "../hooks/useDatasets";
import { useComponents } from "../hooks/useComponents";
import { useCreateExecution, useExecutions } from "../hooks/useExecutions";
import { useSelectionStore } from "../store/selectionStore";

export default function Executions() {
  const datasets = useDatasets();
  const components = useComponents();
  const executions = useExecutions();
  const createExecution = useCreateExecution();

  const {
    selectedDatasetId,
    selectedComponentId,
    selectedExecutionId,
    setSelectedDataset,
    setSelectedComponent,
    setSelectedExecution,
  } = useSelectionStore();

  const selected = executions.data?.find((e) => e.id === selectedExecutionId) ?? null;

  const handleExecute = () => {
    if (!selectedDatasetId || !selectedComponentId) return;
    createExecution.mutate(
      { datasetId: selectedDatasetId, componentId: selectedComponentId },
      { onSuccess: (exec) => setSelectedExecution(exec.id) },
    );
  };

  return (
    <>
      <PageHeader title="Executions" />

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Run a component
          </Typography>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ alignItems: { md: "center" } }}>
            <TextField
              select
              label="Dataset"
              value={selectedDatasetId ?? ""}
              onChange={(e) => setSelectedDataset(e.target.value || null)}
              sx={{ minWidth: 240 }}
            >
              {(datasets.data ?? []).map((d) => (
                <MenuItem key={d.id} value={d.id}>
                  {d.name} ({d.type})
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Component"
              value={selectedComponentId ?? ""}
              onChange={(e) => setSelectedComponent(e.target.value || null)}
              sx={{ minWidth: 240 }}
            >
              {(components.data ?? []).map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.name}
                </MenuItem>
              ))}
            </TextField>
            <Button
              variant="contained"
              startIcon={<PlayArrowIcon />}
              onClick={handleExecute}
              disabled={!selectedDatasetId || !selectedComponentId || createExecution.isPending}
            >
              Execute
            </Button>
          </Stack>
          {createExecution.isError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              Execution request failed.
            </Alert>
          )}
        </CardContent>
      </Card>

      <Typography variant="h6" gutterBottom>
        History
      </Typography>
      {executions.isLoading && <CircularProgress />}
      {executions.data && (
        <Paper sx={{ mb: 3 }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Created</TableCell>
                <TableCell>Finished</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {executions.data.map((e) => (
                <TableRow
                  key={e.id}
                  hover
                  selected={e.id === selectedExecutionId}
                  onClick={() => setSelectedExecution(e.id)}
                  sx={{ cursor: "pointer" }}
                >
                  <TableCell>{e.id.slice(0, 8)}…</TableCell>
                  <TableCell>
                    <StatusChip status={e.status} />
                  </TableCell>
                  <TableCell>{new Date(e.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{e.finishedAt ? new Date(e.finishedAt).toLocaleString() : "—"}</TableCell>
                </TableRow>
              ))}
              {executions.data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    No executions yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}

      {selected && (
        <Box>
          <Typography variant="h6" gutterBottom>
            Execution {selected.id.slice(0, 8)}… <StatusChip status={selected.status} />
          </Typography>
          {selected.status === "FAILED" && selected.errorMessage && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {selected.errorMessage}
            </Alert>
          )}
          <JsonViewer json={selected.result} />
        </Box>
      )}
    </>
  );
}
