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
import { useEffect, useState } from "react";
import { useCreateDataSource, useUpdateDataSource } from "../../hooks/useDataSources";
import type {
  DataSource,
  DataSourceOutputFormat,
  DataSourceType,
  QueryParameter,
} from "../../types";

const TYPES: { value: DataSourceType; label: string }[] = [
  { value: "API", label: "API" },
];

const OUTPUT_FORMATS: DataSourceOutputFormat[] = ["CSV"];

interface DataSourceFormProps {
  open: boolean;
  onClose: () => void;
  dataSource?: DataSource | null;
}

export default function DataSourceForm({ open, onClose, dataSource }: DataSourceFormProps) {
  const [name, setName] = useState("");
  const [type, setType] = useState<DataSourceType>("API");
  const [outputFormat, setOutputFormat] = useState<DataSourceOutputFormat>("CSV");
  const [endpointUrl, setEndpointUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [description, setDescription] = useState("");
  const [params, setParams] = useState<QueryParameter[]>([]);
  const create = useCreateDataSource();
  const update = useUpdateDataSource();

  const isEdit = !!dataSource;
  const pending = create.isPending || update.isPending;

  // Prefill from the entity when editing (and each time the dialog opens for it).
  useEffect(() => {
    if (open) {
      setName(dataSource?.name ?? "");
      setType((dataSource?.type as DataSourceType) ?? "API");
      setOutputFormat((dataSource?.outputFormat as DataSourceOutputFormat) ?? "CSV");
      setEndpointUrl(dataSource?.endpointUrl ?? "");
      setApiKey(dataSource?.apiKey ?? "");
      setDescription(dataSource?.description ?? "");
      setParams(
        (dataSource?.queryParameters ?? []).map((p) => ({
          name: p.name,
          required: p.required,
          defaultValue: p.defaultValue ?? "",
        })),
      );
    }
  }, [open, dataSource]);

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
    const payload = {
      name,
      type,
      outputFormat,
      endpointUrl: endpointUrl.trim() || undefined,
      apiKey: apiKey.trim() || undefined,
      description: description || undefined,
      queryParameters: queryParameters.length ? queryParameters : undefined,
    };
    const onSuccess = () => onClose();
    if (isEdit) {
      update.mutate({ id: dataSource.id, payload }, { onSuccess });
    } else {
      create.mutate(payload, { onSuccess });
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{isEdit ? "Edit Data Source" : "Register Data Source"}</DialogTitle>
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
            label="Endpoint URL"
            value={endpointUrl}
            onChange={(e) => setEndpointUrl(e.target.value)}
            placeholder="https://api.example.com/data (leave blank to use mock data)"
            fullWidth
          />
          <TextField
            label="API Key"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="Leave blank if not required"
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
        <Button variant="contained" onClick={handleSubmit} disabled={!name || pending}>
          {isEdit ? "Save" : "Register"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
