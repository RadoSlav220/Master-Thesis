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
import { useState } from "react";
import { useUploadDataset } from "../../hooks/useDatasets";
import type { DatasetType } from "../../types";

const TYPES: DatasetType[] = ["CSV", "GEOJSON"];

const ACCEPT: Record<DatasetType, string> = {
  CSV: ".csv,text/csv",
  GEOJSON: ".geojson,application/geo+json,application/json",
};

export default function DatasetForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DatasetType>("CSV");
  const [description, setDescription] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const upload = useUploadDataset();

  const pending = upload.isPending;
  const canSubmit = !!name && !!file && !pending;

  const reset = () => {
    setName("");
    setType("CSV");
    setDescription("");
    setFile(null);
  };

  const handleSubmit = () => {
    if (!name || !file) return;
    upload.mutate(
      { file, name, description: description || undefined },
      {
        onSuccess: () => {
          reset();
          onClose();
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Create Dataset</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
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
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
            fullWidth
          />
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
            <Alert severity="error">
              Upload failed — check that the file matches the selected type.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!canSubmit}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
