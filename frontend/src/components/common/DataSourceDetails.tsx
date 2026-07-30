import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import CloudDownloadIcon from "@mui/icons-material/CloudDownload";
import type { DataSource } from "../../types";

interface DataSourceDetailsProps {
  source: DataSource | null;
  open: boolean;
  onClose: () => void;
  onFetch: (source: DataSource) => void;
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: "space-between" }}>
      <Typography variant="body2" sx={{ color: "text.secondary" }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ textAlign: "right" }}>
        {value}
      </Typography>
    </Stack>
  );
}

export default function DataSourceDetails({ source, open, onClose, onFetch }: DataSourceDetailsProps) {
  if (!source) return null;
  const params = source.queryParameters ?? [];

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{source.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={1} sx={{ mt: 1 }}>
          <Field label="Type" value={source.type} />
          <Field label="Output Format" value={source.outputFormat} />
          <Field label="Description" value={source.description ?? "—"} />
          <Field label="Created" value={new Date(source.createdAt).toLocaleString()} />

          <Typography variant="subtitle2" sx={{ mt: 2 }}>
            Query Parameters
          </Typography>
          {params.length === 0 ? (
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              No query parameters.
            </Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Required</TableCell>
                  <TableCell>Default</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {params.map((p) => (
                  <TableRow key={p.name}>
                    <TableCell>{p.name}</TableCell>
                    <TableCell>{p.required ? "Yes" : "No"}</TableCell>
                    <TableCell>{p.defaultValue ?? "—"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          startIcon={<CloudDownloadIcon />}
          onClick={() => {
            onClose();
            onFetch(source);
          }}
        >
          Fetch
        </Button>
      </DialogActions>
    </Dialog>
  );
}
