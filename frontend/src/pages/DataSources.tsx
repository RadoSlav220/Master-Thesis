import {
  Alert,
  Box,
  Button,
  Collapse,
  CircularProgress,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import CloudDownloadIcon from "@mui/icons-material/CloudDownload";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import { useState } from "react";
import PageHeader from "../components/common/PageHeader";
import DataSourceForm from "../components/forms/DataSourceForm";
import FetchDatasetForm from "../components/forms/FetchDatasetForm";
import { useDataSources } from "../hooks/useDataSources";
import type { DataSource } from "../types";

const COLUMN_COUNT = 7;

function DataSourceRow({ source, onFetch }: { source: DataSource; onFetch: () => void }) {
  const [open, setOpen] = useState(false);
  const params = source.queryParameters ?? [];

  return (
    <>
      <TableRow>
        <TableCell padding="checkbox">
          <IconButton size="small" aria-label="toggle query parameters" onClick={() => setOpen((o) => !o)}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>{source.name}</TableCell>
        <TableCell>{source.type}</TableCell>
        <TableCell>{source.outputFormat}</TableCell>
        <TableCell>{source.description ?? "—"}</TableCell>
        <TableCell>{new Date(source.createdAt).toLocaleString()}</TableCell>
        <TableCell align="right">
          <Button size="small" startIcon={<CloudDownloadIcon />} onClick={onFetch}>
            Fetch
          </Button>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell sx={{ py: 0 }} colSpan={COLUMN_COUNT}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ my: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
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
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

export default function DataSources() {
  const [open, setOpen] = useState(false);
  const [fetchSource, setFetchSource] = useState<DataSource | null>(null);
  const { data, isLoading, isError } = useDataSources();

  return (
    <>
      <PageHeader
        title="Data Sources"
        action={{ label: "Register Data Source", onClick: () => setOpen(true), icon: <AddIcon /> }}
      />
      {isLoading && <CircularProgress />}
      {isError && <Alert severity="error">Failed to load data sources.</Alert>}
      {data && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell padding="checkbox" />
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Output Format</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((s) => (
                <DataSourceRow key={s.id} source={s} onFetch={() => setFetchSource(s)} />
              ))}
              {data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={COLUMN_COUNT} align="center">
                    No data sources yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      <DataSourceForm open={open} onClose={() => setOpen(false)} />
      <FetchDatasetForm
        source={fetchSource}
        open={!!fetchSource}
        onClose={() => setFetchSource(null)}
      />
    </>
  );
}
