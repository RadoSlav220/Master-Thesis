import {
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { useState } from "react";
import { useCreateDataSource } from "../../hooks/useDataSources";
import type { DataSourceOutputFormat, DataSourceType, QueryParameter } from "../../types";

const TYPES: { value: DataSourceType; label: string }[] = [
  { value: "API", label: "API" },
];

const OUTPUT_FORMATS: DataSourceOutputFormat[] = ["CSV", "GEOJSON"];

export default function DataSourceForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DataSourceType>("API");
  const [outputFormat, setOutputFormat] = useState<DataSourceOutputFormat>("GEOJSON");
  const [description, setDescription] = useState("");
  const [params, setParams] = useState<QueryParameter[]>([]);
  const create = useCreateDataSource();

  const reset = () => {
    setName("");
    setType("API");
    setOutputFormat("GEOJSON");
    setDescription("");
    setParams([]);
  };

  const addParam = () => setParams((prev) => [...prev, { name: "", required: false, defaultValue: "" }]);

  const removeParam = (index: number) =>
    setParams((prev) => prev.filter((_, i) => i !== index));

  const updateParam = (index: number, patch: Partial<QueryParameter>) =>
    setParams((prev) => prev.map((p, i) => (i === index ? { ...p, ...patch } : p)));

  const handleSubmit = () => {
    const queryParameters = params
      .filter((p) => p.name.trim() !== "")
      .map((p) => ({
        name: p.name.trim(),
        required: p.required,
        defaultValue: p.defaultValue?.trim() ? p.defaultValue.trim() : undefined,
      }));
    create.mutate(
      {
        name,
        type,
        outputFormat,
        description: description || undefined,
        queryParameters: queryParameters.length ? queryParameters : undefined,
      },
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

          <Divider sx={{ mt: 1 }} />
          <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }}>
            <Typography variant="subtitle2" sx={{ color: "text.secondary" }}>
              Query Parameters
            </Typography>
            <Button size="small" startIcon={<AddIcon />} onClick={addParam}>
              Add parameter
            </Button>
          </Stack>
          {params.length === 0 && (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              No query parameters defined.
            </Typography>
          )}
          {params.map((p, i) => (
            <Stack key={i} direction="row" spacing={1} sx={{ alignItems: "center" }}>
              <TextField
                label="Name"
                value={p.name}
                onChange={(e) => updateParam(i, { name: e.target.value })}
                size="small"
                sx={{ flex: 1 }}
              />
              <TextField
                label="Default"
                value={p.defaultValue ?? ""}
                onChange={(e) => updateParam(i, { defaultValue: e.target.value })}
                size="small"
                sx={{ flex: 1 }}
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={p.required}
                    onChange={(e) => updateParam(i, { required: e.target.checked })}
                  />
                }
                label="Required"
              />
              <IconButton aria-label="remove parameter" onClick={() => removeParam(i)}>
                <DeleteIcon />
              </IconButton>
            </Stack>
          ))}
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
