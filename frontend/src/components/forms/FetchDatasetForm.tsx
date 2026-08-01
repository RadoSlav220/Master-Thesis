import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
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
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const fetchDataset = useFetchDataset();
  const navigate = useNavigate();

  const params = source?.queryParameters ?? [];

  // Prefill parameter fields with their registered defaults each time a source's
  // dialog opens, so the user starts from the source's declared defaults.
  useEffect(() => {
    if (source && open) {
      const defaults: Record<string, string> = {};
      for (const p of source.queryParameters ?? []) {
        defaults[p.name] = p.defaultValue ?? "";
      }
      setParamValues(defaults);
    }
  }, [source, open]);

  const reset = () => {
    setName("");
    setParamValues({});
  };

  const requiredFilled = params.every((p) => !p.required || (paramValues[p.name]?.trim() ?? "") !== "");
  const canSubmit = !!name && requiredFilled && !fetchDataset.isPending && !!source;

  const handleSubmit = () => {
    if (!source || !canSubmit) return;
    // Only send parameters that have a value; trim to drop incidental whitespace.
    const queryParameters: Record<string, string> = {};
    for (const p of params) {
      const value = paramValues[p.name]?.trim() ?? "";
      if (value !== "") {
        queryParameters[p.name] = value;
      }
    }
    fetchDataset.mutate(
      {
        id: source.id,
        payload: {
          name,
          queryParameters: Object.keys(queryParameters).length ? queryParameters : undefined,
        },
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
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Dataset Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
          />

          {params.length > 0 && (
            <>
              <Divider sx={{ mt: 1 }} />
              <Typography variant="subtitle2" sx={{ color: "text.secondary" }}>
                Query Parameters
              </Typography>
              {params.map((p) => (
                <TextField
                  key={p.name}
                  label={p.name}
                  value={paramValues[p.name] ?? ""}
                  onChange={(e) =>
                    setParamValues((prev) => ({ ...prev, [p.name]: e.target.value }))
                  }
                  required={p.required}
                  fullWidth
                />
              ))}
            </>
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
