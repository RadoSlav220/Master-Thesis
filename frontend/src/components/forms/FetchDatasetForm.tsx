import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useFetchDataset } from "../../hooks/useDataSources";
import type { DataSource } from "../../types";

interface FetchDatasetFormProps {
  source: DataSource | null;
  open: boolean;
  onClose: () => void;
}

export default function FetchDatasetForm({ source, open, onClose }: FetchDatasetFormProps) {
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const fetchDataset = useFetchDataset();
  const navigate = useNavigate();

  const reset = () => {
    setName("");
    setStartDate("");
    setEndDate("");
  };

  const validRange = !!startDate && !!endDate && startDate <= endDate;
  const canSubmit = !!name && validRange && !fetchDataset.isPending && !!source;

  const handleSubmit = () => {
    if (!source || !canSubmit) return;
    fetchDataset.mutate(
      { id: source.id, payload: { name, startDate, endDate } },
      {
        onSuccess: (dataset) => {
          reset();
          onClose();
          navigate(`/datasets/${dataset.id}`);
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Fetch Dataset{source ? ` from ${source.name}` : ""}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Dataset Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
          />
          <TextField
            label="Start Date"
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          <TextField
            label="End Date"
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          {startDate && endDate && !validRange && (
            <Alert severity="warning">End date must be on or after the start date.</Alert>
          )}
          {fetchDataset.isError && (
            <Alert severity="error">Fetch failed — please try again.</Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!canSubmit}>
          Fetch
        </Button>
      </DialogActions>
    </Dialog>
  );
}
