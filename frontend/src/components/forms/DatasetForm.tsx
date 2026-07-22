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
import { useCreateDataset, useUploadDataset } from "../../hooks/useDatasets";
import type { DatasetType } from "../../types";

const TYPES: DatasetType[] = ["CSV", "GEOJSON"];

export default function DatasetForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DatasetType>("CSV");
  const [description, setDescription] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const create = useCreateDataset();
  const upload = useUploadDataset();

  const isGeoJson = type === "GEOJSON";
  const pending = create.isPending || upload.isPending;
  const canSubmit = !!name && (!isGeoJson || !!file) && !pending;

  const reset = () => {
    setName("");
    setType("CSV");
    setDescription("");
    setFile(null);
  };

  const handleSubmit = () => {
    if (!name) return;
    const onSuccess = () => {
      reset();
      onClose();
    };
    if (isGeoJson) {
      if (!file) return;
      upload.mutate({ file, name, description: description || undefined }, { onSuccess });
    } else {
      create.mutate({ name, type, description: description || undefined }, { onSuccess });
    }
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
          {isGeoJson && (
            <>
              <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
                {file ? file.name : "Choose .geojson file"}
                <input
                  type="file"
                  accept=".geojson,application/geo+json,application/json"
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
                  Upload failed — check that the file is a valid GeoJSON FeatureCollection.
                </Alert>
              )}
            </>
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
