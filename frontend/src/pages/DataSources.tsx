import {
  Alert,
  Button,
  CircularProgress,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import CloudDownloadIcon from "@mui/icons-material/CloudDownload";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import { useState } from "react";
import PageHeader from "../components/common/PageHeader";
import ConfirmDialog from "../components/common/ConfirmDialog";
import DataSourceDetails from "../components/common/DataSourceDetails";
import DataSourceForm from "../components/forms/DataSourceForm";
import FetchDatasetForm from "../components/forms/FetchDatasetForm";
import { useDataSources, useDeleteDataSource } from "../hooks/useDataSources";
import type { DataSource } from "../types";

export default function DataSources() {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<DataSource | null>(null);
  const [fetchSource, setFetchSource] = useState<DataSource | null>(null);
  const [detailsSource, setDetailsSource] = useState<DataSource | null>(null);
  const [toDelete, setToDelete] = useState<DataSource | null>(null);
  const { data, isLoading, isError } = useDataSources();
  const del = useDeleteDataSource();

  const confirmDelete = () => {
    if (!toDelete) return;
    del.mutate(toDelete.id, { onSuccess: () => setToDelete(null) });
  };

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
                <TableRow
                  key={s.id}
                  hover
                  sx={{ cursor: "pointer" }}
                  onClick={() => setDetailsSource(s)}
                >
                  <TableCell>{s.name}</TableCell>
                  <TableCell>{s.type}</TableCell>
                  <TableCell>{s.outputFormat}</TableCell>
                  <TableCell>{s.description ?? "—"}</TableCell>
                  <TableCell>{new Date(s.createdAt).toLocaleString()}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      startIcon={<CloudDownloadIcon />}
                      onClick={(e) => {
                        e.stopPropagation();
                        setFetchSource(s);
                      }}
                    >
                      Fetch
                    </Button>
                    <IconButton
                      size="small"
                      aria-label="edit data source"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditing(s);
                      }}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      aria-label="delete data source"
                      onClick={(e) => {
                        e.stopPropagation();
                        setToDelete(s);
                      }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    No data sources yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      <DataSourceForm open={open} onClose={() => setOpen(false)} />
      <DataSourceForm
        open={!!editing}
        dataSource={editing}
        onClose={() => setEditing(null)}
      />
      <DataSourceDetails
        source={detailsSource}
        open={!!detailsSource}
        onClose={() => setDetailsSource(null)}
        onFetch={(s) => setFetchSource(s)}
      />
      <FetchDatasetForm
        source={fetchSource}
        open={!!fetchSource}
        onClose={() => setFetchSource(null)}
      />
      <ConfirmDialog
        open={!!toDelete}
        title="Delete data source"
        message={`Delete "${toDelete?.name}"? Datasets already fetched from it are kept — they remain as standalone snapshots.`}
        pending={del.isPending}
        onConfirm={confirmDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  );
}
