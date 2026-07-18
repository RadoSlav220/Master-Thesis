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
import { useCreateDataset } from "../../hooks/useDatasets";
import type { DatasetType } from "../../types";

const TYPES: DatasetType[] = ["CSV", "GEOJSON"];

export default function DatasetForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DatasetType>("CSV");
  const [description, setDescription] = useState("");
  const create = useCreateDataset();

  const reset = () => {
    setName("");
    setType("CSV");
    setDescription("");
  };

  const handleSubmit = () => {
    create.mutate(
      { name, type, description: description || undefined },
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
            onChange={(e) => setType(e.target.value as DatasetType)}
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
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!name || create.isPending}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
