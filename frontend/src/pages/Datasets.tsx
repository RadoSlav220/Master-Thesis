import {
  Alert,
  CircularProgress,
  IconButton,
  Paper,
  Slide,
  Snackbar,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import DownloadIcon from "@mui/icons-material/Download";
import EditIcon from "@mui/icons-material/Edit";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import PageHeader from "../components/common/PageHeader";
import ConfirmDialog from "../components/common/ConfirmDialog";
import DatasetForm from "../components/forms/DatasetForm";
import { useDatasets, useDeleteDataset, useDownloadDataset } from "../hooks/useDatasets";
import { isDownloadable } from "../utils/datasetHelpers";
import type { Dataset } from "../types";

export default function Datasets() {
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Dataset | null>(null);
  const [toDelete, setToDelete] = useState<Dataset | null>(null);
  const [createdName, setCreatedName] = useState<string | null>(null);
  const { data, isLoading, isError } = useDatasets();
  const del = useDeleteDataset();
  const download = useDownloadDataset();
  const navigate = useNavigate();

  const confirmDelete = () => {
    if (!toDelete) return;
    del.mutate(toDelete.id, { onSuccess: () => setToDelete(null) });
  };

  return (
    <>
      <PageHeader
        title="Datasets"
        action={{ label: "Create Dataset", onClick: () => setCreateOpen(true), icon: <AddIcon /> }}
      />

      {isLoading && <CircularProgress />}
      {isError && <Alert severity="error">Failed to load datasets.</Alert>}
      {data && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Origin</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((d) => (
                <TableRow
                  key={d.id}
                  hover
                  onClick={() => navigate(`/datasets/${d.id}`)}
                  sx={{ cursor: "pointer" }}
                >
                  <TableCell>{d.name}</TableCell>
                  <TableCell>{d.type}</TableCell>
                  <TableCell>{d.datasetOrigin}</TableCell>
                  <TableCell>{d.description ?? "—"}</TableCell>
                  <TableCell>{new Date(d.createdAt).toLocaleString()}</TableCell>
                  <TableCell align="right">
                    {isDownloadable(d) && (
                      <IconButton
                        size="small"
                        aria-label="download dataset"
                        disabled={download.isPending}
                        onClick={(e) => {
                          e.stopPropagation();
                          download.mutate(d.id);
                        }}
                      >
                        <DownloadIcon fontSize="small" />
                      </IconButton>
                    )}
                    <IconButton
                      size="small"
                      aria-label="edit dataset"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditing(d);
                      }}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      aria-label="delete dataset"
                      onClick={(e) => {
                        e.stopPropagation();
                        setToDelete(d);
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
                    No datasets yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      <DatasetForm
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(name) => setCreatedName(name)}
      />
      <DatasetForm open={!!editing} dataset={editing} onClose={() => setEditing(null)} />
      <ConfirmDialog
        open={!!toDelete}
        title="Delete dataset"
        message={`Delete "${toDelete?.name}"? Existing executions that used it are kept but will reference a dataset that no longer exists.`}
        pending={del.isPending}
        onConfirm={confirmDelete}
        onClose={() => setToDelete(null)}
      />
      <Snackbar
        open={!!createdName}
        autoHideDuration={4000}
        onClose={() => setCreatedName(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        slots={{ transition: Slide }}
      >
        <Alert
          severity="success"
          variant="filled"
          onClose={() => setCreatedName(null)}
          sx={{ width: "100%", boxShadow: 6, borderRadius: 2 }}
        >
          Dataset “{createdName}” created successfully.
        </Alert>
      </Snackbar>
    </>
  );
}
