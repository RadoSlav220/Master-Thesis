import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { isAxiosError } from "axios";
import { useEffect, useMemo, useState } from "react";
import { useUpdateDataset, useUploadStations } from "../../hooks/useDatasets";
import type { ColumnRole, Dataset, StationMeasurementRenames, StationUploadMapping } from "../../types";

const ROLES: ColumnRole[] = [
  "STATION_ID",
  "LATITUDE",
  "LONGITUDE",
  "TIMESTAMP",
  "STATION_ATTRIBUTE",
  "MEASUREMENT",
  "IGNORE",
];

const ROLE_LABELS: Record<ColumnRole, string> = {
  STATION_ID: "Station ID",
  LATITUDE: "Latitude",
  LONGITUDE: "Longitude",
  TIMESTAMP: "Timestamp",
  STATION_ATTRIBUTE: "Station attribute",
  MEASUREMENT: "Measurement",
  IGNORE: "Ignore",
};

const FALLBACK_UPLOAD_ERROR =
  "Upload failed — a file may be malformed or the column mapping may be invalid.";

function uploadErrorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: string } | undefined)?.detail;
    if (detail) return `Upload failed — ${detail}`;
  }
  return FALLBACK_UPLOAD_ERROR;
}

/** Stable key for a picked file so mappings survive add/remove/reorder. */
function fileKey(file: File): string {
  return `${file.name}\u0000${file.size}`;
}

/** Guesses a column's role from its name (the user can override). */
function guessRole(column: string): ColumnRole {
  const c = column.trim().toLowerCase();
  if (/(^|[^a-z])(station|site|sensor).*id|^id$|stationid/.test(c)) return "STATION_ID";
  if (/^lat|latitude/.test(c)) return "LATITUDE";
  if (/^lon|^lng|longitude/.test(c)) return "LONGITUDE";
  if (/time|date|timestamp/.test(c)) return "TIMESTAMP";
  return "MEASUREMENT";
}

/** Parses a CSV header row (naive comma split — adequate for typical station headers). */
function parseHeader(text: string): string[] {
  const firstLine = text.split(/\r?\n/, 1)[0] ?? "";
  return firstLine
    .split(",")
    .map((h) => h.trim())
    .filter((h) => h.length > 0);
}

interface DatasetFormProps {
  open: boolean;
  onClose: () => void;
  dataset?: Dataset | null;
  /** Called after a new dataset is successfully created (create mode only). */
  onCreated?: (name: string) => void;
}

export default function DatasetForm({ open, onClose, dataset, onCreated }: DatasetFormProps) {
  const isEdit = !!dataset;

  // Shared metadata.
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  // Wizard state (create mode).
  const [step, setStep] = useState(0);
  const [files, setFiles] = useState<File[]>([]);
  const [headers, setHeaders] = useState<Record<string, string[]>>({});
  const [roles, setRoles] = useState<Record<string, Record<string, ColumnRole>>>({});
  // Optional canonical names for MEASUREMENT columns: fileKey -> column -> name.
  const [renames, setRenames] = useState<Record<string, Record<string, string>>>({});
  const [activeTab, setActiveTab] = useState(0);
  const [headerError, setHeaderError] = useState<string | null>(null);

  const upload = useUploadStations();
  const update = useUpdateDataset();
  const pending = upload.isPending || update.isPending;

  useEffect(() => {
    if (open) {
      setName(dataset?.name ?? "");
      setDescription(dataset?.description ?? "");
      setStep(0);
      setFiles([]);
      setHeaders({});
      setRoles({});
      setRenames({});
      setActiveTab(0);
      setHeaderError(null);
    }
  }, [open, dataset]);

  const addFiles = (picked: FileList | null) => {
    if (!picked) return;
    const existing = new Set(files.map(fileKey));
    const next = [...files];
    for (const f of Array.from(picked)) {
      if (!existing.has(fileKey(f))) next.push(f);
    }
    setFiles(next);
  };

  const removeFile = (file: File) => {
    setFiles(files.filter((f) => fileKey(f) !== fileKey(file)));
  };

  // On entering step 2, parse headers for any files not yet parsed and seed role guesses.
  const goToMapping = async () => {
    setHeaderError(null);
    try {
      const nextHeaders = { ...headers };
      const nextRoles = { ...roles };
      for (const f of files) {
        const key = fileKey(f);
        if (!nextHeaders[key]) {
          const cols = parseHeader(await f.text());
          nextHeaders[key] = cols;
        }
        if (!nextRoles[key]) {
          const guessed: Record<string, ColumnRole> = {};
          for (const col of nextHeaders[key]) guessed[col] = guessRole(col);
          nextRoles[key] = guessed;
        }
      }
      setHeaders(nextHeaders);
      setRoles(nextRoles);
      setActiveTab(0);
      setStep(1);
    } catch {
      setHeaderError("Could not read one of the files. Make sure they are valid CSV files.");
    }
  };

  const setRole = (key: string, column: string, role: ColumnRole) => {
    setRoles((prev) => ({
      ...prev,
      [key]: { ...prev[key], [column]: role },
    }));
    // A rename only applies to a MEASUREMENT column; drop it if the role changes away.
    if (role !== "MEASUREMENT") {
      setRenames((prev) => {
        if (!prev[key] || !(column in prev[key])) return prev;
        const next = { ...prev[key] };
        delete next[column];
        return { ...prev, [key]: next };
      });
    }
  };

  const setRename = (key: string, column: string, name: string) => {
    setRenames((prev) => ({
      ...prev,
      [key]: { ...prev[key], [column]: name },
    }));
  };

  // Files are joined by station id: every file needs exactly one STATION_ID,
  // Latitude/Longitude must be mapped together (both or neither) per file, and
  // at least one file in the dataset must provide coordinates. The backend
  // re-validates and additionally checks every station id gets coordinates.
  const validationErrors = useMemo(() => {
    const errors: string[] = [];
    let datasetHasCoords = false;
    for (const f of files) {
      const key = fileKey(f);
      const fileRoles = roles[key] ?? {};
      const counts = { STATION_ID: 0, LATITUDE: 0, LONGITUDE: 0 } as Record<string, number>;
      for (const r of Object.values(fileRoles)) {
        if (r in counts) counts[r] += 1;
      }
      const hasMeasurement = Object.values(fileRoles).includes("MEASUREMENT");
      const hasTimestamp = Object.values(fileRoles).includes("TIMESTAMP");
      if (counts.STATION_ID !== 1) {
        errors.push(`${f.name}: needs exactly one ${ROLE_LABELS.STATION_ID} column.`);
      }
      if (counts.LATITUDE > 1 || counts.LONGITUDE > 1) {
        errors.push(
          `${f.name}: at most one ${ROLE_LABELS.LATITUDE} and one ${ROLE_LABELS.LONGITUDE} column.`,
        );
      } else if (counts.LATITUDE !== counts.LONGITUDE) {
        errors.push(
          `${f.name}: ${ROLE_LABELS.LATITUDE} and ${ROLE_LABELS.LONGITUDE} must be mapped together.`,
        );
      } else if (counts.LATITUDE === 1) {
        datasetHasCoords = true;
      }
      if (hasMeasurement && !hasTimestamp) {
        errors.push(`${f.name}: a Timestamp column is required when there are measurements.`);
      }
    }
    if (files.length > 0 && !datasetHasCoords) {
      errors.push(
        `At least one file must provide ${ROLE_LABELS.LATITUDE} and ${ROLE_LABELS.LONGITUDE} columns.`,
      );
    }
    return errors;
  }, [files, roles]);

  const buildMapping = (): StationUploadMapping => {
    const mapping: StationUploadMapping = {};
    for (const f of files) {
      mapping[f.name] = roles[fileKey(f)] ?? {};
    }
    return mapping;
  };

  // Only measurement columns whose canonical name is non-empty and differs from the header.
  const buildRenames = (): StationMeasurementRenames | undefined => {
    const result: StationMeasurementRenames = {};
    for (const f of files) {
      const key = fileKey(f);
      const fileRoles = roles[key] ?? {};
      const fileRenames = renames[key] ?? {};
      const perFile: Record<string, string> = {};
      for (const [col, name] of Object.entries(fileRenames)) {
        const trimmed = name.trim();
        if (fileRoles[col] === "MEASUREMENT" && trimmed && trimmed !== col) {
          perFile[col] = trimmed;
        }
      }
      if (Object.keys(perFile).length > 0) result[f.name] = perFile;
    }
    return Object.keys(result).length > 0 ? result : undefined;
  };

  const handleUpdate = () => {
    if (!name || !dataset) return;
    update.mutate(
      { id: dataset.id, payload: { name, description: description || undefined } },
      { onSuccess: onClose },
    );
  };

  const handleSubmit = () => {
    if (!name || files.length === 0 || validationErrors.length > 0) return;
    upload.mutate(
      {
        files,
        name,
        mapping: buildMapping(),
        description: description || undefined,
        renames: buildRenames(),
      },
      {
        onSuccess: () => {
          onCreated?.(name);
          onClose();
        },
      },
    );
  };

  // ---- Edit mode: simple metadata form (unchanged behavior) ----
  if (isEdit) {
    return (
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
        <DialogTitle>Edit Dataset</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              fullWidth
            />
            <TextField
              label="Description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              minRows={2}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="contained" onClick={handleUpdate} disabled={!name || pending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  // ---- Create mode: 2-step station upload wizard ----
  const canGoNext = !!name && files.length > 0;
  const canSubmit = canGoNext && validationErrors.length === 0 && !pending;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Create Dataset</DialogTitle>
      <DialogContent>
        <Stepper activeStep={step} sx={{ mt: 1, mb: 3 }}>
          <Step>
            <StepLabel>Upload files</StepLabel>
          </Step>
          <Step>
            <StepLabel>Map columns</StepLabel>
          </Step>
        </Stepper>

        {step === 0 && (
          <Stack spacing={2} sx={{}}>
            <TextField
              label="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              fullWidth
            />
            <TextField
              label="Description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              minRows={2}
              fullWidth
            />
            <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
              Choose .csv file(s)
              <input
                type="file"
                accept=".csv,text/csv"
                hidden
                multiple
                onChange={(e) => addFiles(e.target.files)}
              />
            </Button>
            {files.length > 0 && (
              <Stack spacing={1} sx={{}}>
                {files.map((f) => (
                  <Stack
                    key={fileKey(f)}
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: "center", justifyContent: "space-between" }}
                  >
                    <Typography variant="body2" sx={{ color: "text.secondary" }}>
                      {f.name} ({Math.round(f.size / 1024)} KB)
                    </Typography>
                    <IconButton
                      size="small"
                      aria-label="remove file"
                      onClick={() => removeFile(f)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}
              </Stack>
            )}
            {headerError && <Alert severity="error">{headerError}</Alert>}
          </Stack>
        )}

        {step === 1 && (
          <Box sx={{}}>
            <Tabs
              value={activeTab}
              onChange={(_, v) => setActiveTab(v)}
              variant="scrollable"
              scrollButtons="auto"
            >
              {files.map((f) => (
                <Tab key={fileKey(f)} label={f.name} />
              ))}
            </Tabs>
            {files.map((f, idx) => {
              const key = fileKey(f);
              if (idx !== activeTab) return null;
              const cols = headers[key] ?? [];
              return (
                <Table key={key} size="small" sx={{ mt: 2 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ width: "40%" }}>Column</TableCell>
                      <TableCell sx={{ width: "30%" }}>Type</TableCell>
                      <TableCell>Rename measurement</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {cols.map((col) => (
                      <TableRow key={col}>
                        <TableCell sx={{ width: "40%" }}>{col}</TableCell>
                        <TableCell sx={{ width: "30%" }}>
                          <TextField
                            select
                            size="small"
                            fullWidth
                            value={roles[key]?.[col] ?? "IGNORE"}
                            onChange={(e) => setRole(key, col, e.target.value as ColumnRole)}
                          >
                            {ROLES.map((r) => (
                              <MenuItem key={r} value={r}>
                                {ROLE_LABELS[r]}
                              </MenuItem>
                            ))}
                          </TextField>
                        </TableCell>
                        <TableCell>
                          {roles[key]?.[col] === "MEASUREMENT" && (
                            <TextField
                              size="small"
                              fullWidth
                              placeholder={`Default: ${col}`}
                              value={renames[key]?.[col] ?? ""}
                              onChange={(e) => setRename(key, col, e.target.value)}
                            />
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              );
            })}
            {validationErrors.length > 0 && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                <Stack spacing={0.5} sx={{}}>
                  {validationErrors.map((err) => (
                    <span key={err}>{err}</span>
                  ))}
                </Stack>
              </Alert>
            )}
            {upload.isError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {uploadErrorMessage(upload.error)}
              </Alert>
            )}
            <Box sx={{ mt: 2 }}>
              <Chip label={`${files.length} file(s)`} size="small" />
            </Box>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        {step === 1 && <Button onClick={() => setStep(0)}>Back</Button>}
        {step === 0 && (
          <Button variant="contained" onClick={goToMapping} disabled={!canGoNext}>
            Next
          </Button>
        )}
        {step === 1 && (
          <Button variant="contained" onClick={handleSubmit} disabled={!canSubmit}>
            Create
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
