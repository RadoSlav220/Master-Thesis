import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  IconButton,
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
import DeleteIcon from "@mui/icons-material/Delete";
import DownloadIcon from "@mui/icons-material/Download";
import { useState } from "react";
import PageHeader from "../components/common/PageHeader";
import StatusChip from "../components/common/StatusChip";
import ConfirmDialog from "../components/common/ConfirmDialog";
import JsonViewer from "../components/common/JsonViewer";
import MeasurementMappingDialog from "../components/forms/MeasurementMappingDialog";
import { useDatasets } from "../hooks/useDatasets";
import { useComponents } from "../hooks/useComponents";
import {
  useCreateExecution,
  useDeleteExecution,
  useDownloadExecution,
  useExecutions,
} from "../hooks/useExecutions";
import { useSelectionStore } from "../store/selectionStore";
import { isAxiosError } from "axios";
import type { Execution } from "../types";

export default function Executions() {
  const datasets = useDatasets();
  const components = useComponents();
  const executions = useExecutions();
  const createExecution = useCreateExecution();
  const deleteExecution = useDeleteExecution();
  const downloadExecution = useDownloadExecution();

  const {
    selectedDatasetId,
    selectedComponentId,
    selectedExecutionId,
    setSelectedDataset,
    setSelectedComponent,
    setSelectedExecution,
  } = useSelectionStore();

  const [toDelete, setToDelete] = useState<Execution | null>(null);
  const [mappingOpen, setMappingOpen] = useState(false);

  const confirmDelete = () => {
    if (!toDelete) return;
    const deletedId = toDelete.id;
    deleteExecution.mutate(deletedId, {
      onSuccess: () => {
        // Clear the detail pane if the currently selected execution was deleted.
        if (selectedExecutionId === deletedId) setSelectedExecution(null);
        setToDelete(null);
      },
    });
  };

  const selected = executions.data?.find((e) => e.id === selectedExecutionId) ?? null;
  const selectedComponent = components.data?.find((c) => c.id === selectedComponentId) ?? null;

  const runExecution = (measurementMapping?: Record<string, string>) => {
    if (!selectedDatasetId || !selectedComponentId) return;
    createExecution.mutate(
      {
        datasetId: selectedDatasetId,
        componentId: selectedComponentId,
        ...(measurementMapping ? { measurementMapping } : {}),
      },
      { onSuccess: (exec) => setSelectedExecution(exec.id) },
    );
  };

  const handleExecute = () => {
    if (!selectedDatasetId || !selectedComponentId) return;
    // Components that declare expected measurements need a mapping step first.
    if ((selectedComponent?.expectedMeasurements?.length ?? 0) > 0) {
      setMappingOpen(true);
      return;
    }
    runExecution();
  };

  const handleMappingConfirm = (measurementMapping: Record<string, string>) => {
    setMappingOpen(false);
    runExecution(measurementMapping);
  };

  const executionErrorMessage = (error: unknown): string => {
    if (isAxiosError(error)) {
      const detail = (error.response?.data as { detail?: string } | undefined)?.detail;
      if (detail) return detail;
    }
    return "Execution request failed.";
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
              onChange={(e) => {
                setSelectedDataset(e.target.value || null);
              }}
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
              {executionErrorMessage(createExecution.error)}
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
                <TableCell align="right">Actions</TableCell>
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
                  <TableCell align="right">
                    {e.status === "COMPLETED" && (
                      <IconButton
                        size="small"
                        aria-label="download result"
                        disabled={downloadExecution.isPending}
                        onClick={(ev) => {
                          ev.stopPropagation();
                          downloadExecution.mutate(e.id);
                        }}
                      >
                        <DownloadIcon fontSize="small" />
                      </IconButton>
                    )}
                    <IconButton
                      size="small"
                      aria-label="delete execution"
                      onClick={(ev) => {
                        ev.stopPropagation();
                        setToDelete(e);
                      }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {executions.data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} align="center">
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
          <Stack
            direction="row"
            spacing={2}
            sx={{ mb: 1, alignItems: "center", justifyContent: "space-between" }}
          >
            <Typography variant="h6" sx={{}}>
              Execution {selected.id.slice(0, 8)}… <StatusChip status={selected.status} />
            </Typography>
            {selected.status === "COMPLETED" && (
              <Button
                startIcon={<DownloadIcon />}
                onClick={() => downloadExecution.mutate(selected.id)}
                disabled={downloadExecution.isPending}
              >
                Download result
              </Button>
            )}
          </Stack>
          {selected.status === "FAILED" && selected.errorMessage && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {selected.errorMessage}
            </Alert>
          )}
          {downloadExecution.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Download failed — the result may be unavailable.
            </Alert>
          )}
          <JsonViewer json={selected.result} />
        </Box>
      )}

      <ConfirmDialog
        open={!!toDelete}
        title="Delete execution"
        message={`Delete execution ${toDelete?.id.slice(0, 8)}…? This removes the run record and its result.`}
        pending={deleteExecution.isPending}
        onConfirm={confirmDelete}
        onClose={() => setToDelete(null)}
      />

      <MeasurementMappingDialog
        open={mappingOpen}
        datasetId={selectedDatasetId}
        component={selectedComponent}
        onClose={() => setMappingOpen(false)}
        onConfirm={handleMappingConfirm}
      />
    </>
  );
}
