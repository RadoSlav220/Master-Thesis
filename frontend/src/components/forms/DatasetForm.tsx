import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { isAxiosError } from "axios";
import { useEffect, useState } from "react";
import { useUpdateDataset, useUploadDataset } from "../../hooks/useDatasets";
import type { Dataset, DatasetType } from "../../types";

const TYPES: DatasetType[] = ["CSV", "GEOJSON"];

const ACCEPT: Record<DatasetType, string> = {
  CSV: ".csv,text/csv",
  GEOJSON: ".geojson,application/geo+json,application/json",
};

const FALLBACK_UPLOAD_ERROR =
  "Upload failed — the file may be malformed or not match the selected type.";

/**
 * Prefers the backend's ProblemDetail message (e.g. the analysis service's reason
 * a file is malformed), falling back to a generic hint covering both likely causes.
 */
function uploadErrorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: string } | undefined)?.detail;
    if (detail) return `Upload failed — ${detail}`;
  }
  return FALLBACK_UPLOAD_ERROR;
}

interface DatasetFormProps {
  open: boolean;
  onClose: () => void;
  dataset?: Dataset | null;
}

export default function DatasetForm({ open, onClose, dataset }: DatasetFormProps) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DatasetType>("CSV");
  const [description, setDescription] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const upload = useUploadDataset();
  const update = useUpdateDataset();

  const isEdit = !!dataset;
  const pending = upload.isPending || update.isPending;
  // In edit mode only metadata (name/description) applies; creating requires a file.
  const canSubmit = !!name && (isEdit || !!file) && !pending;

  // Prefill from the entity when editing (and each time the dialog opens).
  useEffect(() => {
    if (open) {
      setName(dataset?.name ?? "");
      setType((dataset?.type as DatasetType) ?? "CSV");
      setDescription(dataset?.description ?? "");
      setFile(null);
    }
  }, [open, dataset]);

  const handleSubmit = () => {
    if (!name) return;
    const onSuccess = () => onClose();
    if (isEdit) {
      update.mutate(
        { id: dataset.id, payload: { name, description: description || undefined } },
        { onSuccess },
      );
    } else {
      if (!file) return;
      upload.mutate({ file, name, description: description || undefined }, { onSuccess });
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{isEdit ? "Edit Dataset" : "Create Dataset"}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
          {!isEdit && (
            <TextField
              select
              label="Type"
              value={type}
              onChange={(e) => {
                setType(e.target.value as DatasetType);
                setFile(null);
              }}
              fullWidth
            >
              {TYPES.map((t) => (
                <MenuItem key={t} value={t}>
                  {t}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
            fullWidth
          />
          {!isEdit && (
            <>
              <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
                {file ? file.name : `Choose ${type === "CSV" ? ".csv" : ".geojson"} file`}
                <input
                  type="file"
                  accept={ACCEPT[type]}
                  hidden
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
              </Button>
              {file && (
                <Typography variant="body2" sx={{ color: "text.secondary" }}>
                  Selected: {file.name} ({Math.round(file.size / 1024)} KB)
                </Typography>
              )}
              {upload.isError && (
                <Alert severity="error">{uploadErrorMessage(upload.error)}</Alert>
              )}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!canSubmit}>
          {isEdit ? "Save" : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
