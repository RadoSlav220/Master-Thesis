import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  CircularProgress,
  Divider,
  FormControlLabel,
  FormGroup,
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
import { useMemo, useState } from "react";
import PageHeader from "../components/common/PageHeader";
import StatusChip from "../components/common/StatusChip";
import JsonViewer from "../components/common/JsonViewer";
import { useDatasets } from "../hooks/useDatasets";
import { useComponents } from "../hooks/useComponents";
import { useCreateExecution, useExecutions } from "../hooks/useExecutions";
import { useSelectionStore } from "../store/selectionStore";
import type { FilterSpec } from "../types";

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

  // Filter state: which columns/properties are selected, and an optional row limit.
  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);
  const [limit, setLimit] = useState<string>("");

  const selected = executions.data?.find((e) => e.id === selectedExecutionId) ?? null;
  const selectedDataset = datasets.data?.find((d) => d.id === selectedDatasetId) ?? null;

  // Available filter fields come from the dataset's persisted analysis.
  const availableFields = useMemo<string[]>(() => {
    const raw = selectedDataset?.analysisResult;
    if (!raw) return [];
    try {
      const a = JSON.parse(raw) as { columns?: string[]; properties?: string[] };
      return a.columns ?? a.properties ?? [];
    } catch {
      return [];
    }
  }, [selectedDataset?.analysisResult]);

  const toggleColumn = (field: string) => {
    setSelectedColumns((prev) =>
      prev.includes(field) ? prev.filter((c) => c !== field) : [...prev, field],
    );
  };

  const buildFilter = (): FilterSpec | undefined => {
    const limitNum = limit ? Number(limit) : undefined;
    const hasColumns = selectedColumns.length > 0;
    const hasLimit = limitNum !== undefined && !Number.isNaN(limitNum) && limitNum > 0;
    if (!hasColumns && !hasLimit) return undefined;
    return {
      columns: hasColumns ? selectedColumns : [],
      ...(hasLimit ? { limit: limitNum } : {}),
    };
  };

  const handleExecute = () => {
    if (!selectedDatasetId || !selectedComponentId) return;
    createExecution.mutate(
      { datasetId: selectedDatasetId, componentId: selectedComponentId, filter: buildFilter() },
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
              onChange={(e) => {
                setSelectedDataset(e.target.value || null);
                setSelectedColumns([]);
                setLimit("");
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

          {selectedDataset && (
            <Box sx={{ mt: 2 }}>
              <Divider sx={{ mb: 2 }} />
              <Typography variant="subtitle1" gutterBottom>
                Filter (optional)
              </Typography>
              {availableFields.length > 0 ? (
                <>
                  <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                    Select columns/properties to include (none selected = all).
                  </Typography>
                  <FormGroup row>
                    {availableFields.map((field) => (
                      <FormControlLabel
                        key={field}
                        control={
                          <Checkbox
                            checked={selectedColumns.includes(field)}
                            onChange={() => toggleColumn(field)}
                          />
                        }
                        label={field}
                      />
                    ))}
                  </FormGroup>
                  <TextField
                    label="Row / feature limit"
                    type="number"
                    value={limit}
                    onChange={(e) => setLimit(e.target.value)}
                    sx={{ mt: 1, width: 220 }}
                    slotProps={{ htmlInput: { min: 1 } }}
                  />
                </>
              ) : (
                <Typography variant="body2" sx={{ color: "text.secondary" }}>
                  This dataset has no analysis yet — fetch or analyze it to enable filtering.
                  The run will use the full dataset.
                </Typography>
              )}
            </Box>
          )}

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
          {selected.filterSpec && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Applied filter: {selected.filterSpec}
            </Alert>
          )}
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
