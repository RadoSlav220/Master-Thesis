import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useState } from "react";
import { useCreateDataSource } from "../../hooks/useDataSources";
import type { DataSourceOutputFormat, DataSourceType } from "../../types";

const TYPES: { value: DataSourceType; label: string }[] = [
  { value: "API", label: "API" },
];

const OUTPUT_FORMATS: DataSourceOutputFormat[] = ["CSV", "GEOJSON"];

export default function DataSourceForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DataSourceType>("API");
  const [outputFormat, setOutputFormat] = useState<DataSourceOutputFormat>("GEOJSON");
  const [description, setDescription] = useState("");
  const create = useCreateDataSource();

  const reset = () => {
    setName("");
    setType("API");
    setOutputFormat("GEOJSON");
    setDescription("");
  };

  const handleSubmit = () => {
    create.mutate(
      { name, type, outputFormat, description: description || undefined },
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
      <DialogTitle>Register Data Source</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
          <TextField
            select
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value as DataSourceType)}
            fullWidth
          >
            {TYPES.map((t) => (
              <MenuItem key={t.value} value={t.value}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Output Format"
            value={outputFormat}
            onChange={(e) => setOutputFormat(e.target.value as DataSourceOutputFormat)}
            fullWidth
          >
            {OUTPUT_FORMATS.map((f) => (
              <MenuItem key={f} value={f}>
                {f}
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
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!name || create.isPending}>
          Register
        </Button>
      </DialogActions>
    </Dialog>
  );
}
