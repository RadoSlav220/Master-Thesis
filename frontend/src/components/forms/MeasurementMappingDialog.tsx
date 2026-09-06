import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { useDatasetMeasurements } from "../../hooks/useDatasets";
import type { Component } from "../../types";

/** Large enough to observe every distinct measurement type for typical datasets. */
const MEASUREMENT_SCAN_LIMIT = 1000;

const UNMAPPED = "";

interface MeasurementMappingDialogProps {
  open: boolean;
  datasetId: string | null;
  component: Component | null;
  onClose: () => void;
  /** Called with the confirmed mapping: expected measurement name -> dataset column. */
  onConfirm: (mapping: Record<string, string>) => void;
}

export default function MeasurementMappingDialog({
  open,
  datasetId,
  component,
  onClose,
  onConfirm,
}: MeasurementMappingDialogProps) {
  const measurements = useDatasetMeasurements(open ? datasetId : null, MEASUREMENT_SCAN_LIMIT);
  const [mapping, setMapping] = useState<Record<string, string>>({});

  const availableTypes = useMemo(() => {
    const set = new Set((measurements.data ?? []).map((m) => m.measurementType));
    return Array.from(set).sort();
  }, [measurements.data]);

  const expected = useMemo(() => component?.expectedMeasurements ?? [], [component]);

  // Prefill exact / case-insensitive name matches each time the dialog opens (or data arrives).
  useEffect(() => {
    if (!open) return;
    const next: Record<string, string> = {};
    for (const name of expected) {
      const exact = availableTypes.find((t) => t === name);
      const ci = availableTypes.find((t) => t.toLowerCase() === name.toLowerCase());
      next[name] = exact ?? ci ?? UNMAPPED;
    }
    setMapping(next);
  }, [open, expected, availableTypes]);

  const setFor = (name: string, column: string) => {
    setMapping((prev) => ({ ...prev, [name]: column }));
  };

  const unmapped = expected.filter((name) => !mapping[name]);
  const canConfirm = expected.length > 0 && unmapped.length === 0;

  const handleConfirm = () => {
    if (!canConfirm) return;
    onConfirm(mapping);
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Map measurements</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
          {component?.name} expects the measurements below. Map each one to a measurement
          column from the dataset.
        </Typography>
        {measurements.isLoading && <Typography sx={{}}>Loading dataset measurements…</Typography>}
        {measurements.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            Failed to load the dataset's measurements.
          </Alert>
        )}
        {measurements.data && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: "45%" }}>Expected measurement</TableCell>
                <TableCell>Dataset column</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {expected.map((name) => (
                <TableRow key={name}>
                  <TableCell>{name}</TableCell>
                  <TableCell>
                    <TextField
                      select
                      size="small"
                      fullWidth
                      value={mapping[name] ?? UNMAPPED}
                      onChange={(e) => setFor(name, e.target.value)}
                    >
                      <MenuItem value={UNMAPPED}>
                        <em>— unmapped —</em>
                      </MenuItem>
                      {availableTypes.map((t) => (
                        <MenuItem key={t} value={t}>
                          {t}
                        </MenuItem>
                      ))}
                    </TextField>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {measurements.data && availableTypes.length === 0 && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            This dataset has no measurement columns to map.
          </Alert>
        )}
        {unmapped.length > 0 && availableTypes.length > 0 && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            Map every expected measurement before running: {unmapped.join(", ")}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleConfirm} disabled={!canConfirm}>
          Run
        </Button>
      </DialogActions>
    </Dialog>
  );
}
