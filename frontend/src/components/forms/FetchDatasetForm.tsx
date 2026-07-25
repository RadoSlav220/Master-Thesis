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
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import type { Dayjs } from "dayjs";
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
  const [start, setStart] = useState<Dayjs | null>(null);
  const [end, setEnd] = useState<Dayjs | null>(null);
  const fetchDataset = useFetchDataset();
  const navigate = useNavigate();

  const reset = () => {
    setName("");
    setStart(null);
    setEnd(null);
  };

  const validRange = !!start && !!end && start.isValid() && end.isValid() && !end.isBefore(start);
  const canSubmit = !!name && validRange && !fetchDataset.isPending && !!source;

  const handleSubmit = () => {
    if (!source || !canSubmit || !start || !end) return;
    // dayjs holds local time; toISOString() converts to a UTC instant so the backend
    // records the exact moment regardless of the user's timezone.
    fetchDataset.mutate(
      {
        id: source.id,
        payload: { name, startDate: start.toISOString(), endDate: end.toISOString() },
      },
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
        <LocalizationProvider dateAdapter={AdapterDayjs}>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Dataset Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              fullWidth
            />
            <DateTimePicker
              label="Start"
              value={start}
              onChange={setStart}
              slotProps={{ textField: { fullWidth: true } }}
            />
            <DateTimePicker
              label="End"
              value={end}
              onChange={setEnd}
              minDateTime={start ?? undefined}
              slotProps={{ textField: { fullWidth: true } }}
            />
            {start && end && !validRange && (
              <Alert severity="warning">End must be on or after the start.</Alert>
            )}
            {fetchDataset.isError && (
              <Alert severity="error">Fetch failed — please try again.</Alert>
            )}
          </Stack>
        </LocalizationProvider>
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
