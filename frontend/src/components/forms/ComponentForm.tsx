import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useEffect, useState } from "react";
import { useCreateComponent, useUpdateComponent } from "../../hooks/useComponents";
import type { Component } from "../../types";

interface ComponentFormProps {
  open: boolean;
  onClose: () => void;
  component?: Component | null;
}

export default function ComponentForm({ open, onClose, component }: ComponentFormProps) {
  const [name, setName] = useState("");
  const [endpointUrl, setEndpointUrl] = useState("");
  const [description, setDescription] = useState("");
  const create = useCreateComponent();
  const update = useUpdateComponent();

  const isEdit = !!component;
  const pending = create.isPending || update.isPending;

  // Prefill from the entity when editing (and each time the dialog opens for it).
  useEffect(() => {
    if (open) {
      setName(component?.name ?? "");
      setEndpointUrl(component?.endpointUrl ?? "");
      setDescription(component?.description ?? "");
    }
  }, [open, component]);

  const handleSubmit = () => {
    const payload = { name, endpointUrl, description: description || undefined };
    const onSuccess = () => onClose();
    if (isEdit) {
      update.mutate({ id: component.id, payload }, { onSuccess });
    } else {
      create.mutate(payload, { onSuccess });
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{isEdit ? "Edit Component" : "Register Component"}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
          <TextField
            label="Endpoint URL"
            value={endpointUrl}
            onChange={(e) => setEndpointUrl(e.target.value)}
            placeholder="http://localhost:8080/mock-components/air-quality"
            required
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
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={!name || !endpointUrl || pending}
        >
          {isEdit ? "Save" : "Register"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
