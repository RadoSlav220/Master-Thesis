import {
  Alert,
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
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import { useState } from "react";
import PageHeader from "../components/common/PageHeader";
import ConfirmDialog from "../components/common/ConfirmDialog";
import ComponentForm from "../components/forms/ComponentForm";
import { useComponents, useDeleteComponent } from "../hooks/useComponents";
import type { Component } from "../types";

export default function Components() {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Component | null>(null);
  const [toDelete, setToDelete] = useState<Component | null>(null);
  const { data, isLoading, isError } = useComponents();
  const del = useDeleteComponent();

  const confirmDelete = () => {
    if (!toDelete) return;
    del.mutate(toDelete.id, { onSuccess: () => setToDelete(null) });
  };

  return (
    <>
      <PageHeader
        title="Components"
        action={{ label: "Register Component", onClick: () => setOpen(true), icon: <AddIcon /> }}
      />
      {isLoading && <CircularProgress />}
      {isError && <Alert severity="error">Failed to load components.</Alert>}
      {data && (
        <Paper>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Endpoint URL</TableCell>
                <TableCell>Description</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{c.name}</TableCell>
                  <TableCell>{c.endpointUrl}</TableCell>
                  <TableCell>{c.description ?? "—"}</TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      aria-label="edit component"
                      onClick={() => setEditing(c)}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      aria-label="delete component"
                      onClick={() => setToDelete(c)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    No components yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      <ComponentForm open={open} onClose={() => setOpen(false)} />
      <ComponentForm
        open={!!editing}
        component={editing}
        onClose={() => setEditing(null)}
      />
      <ConfirmDialog
        open={!!toDelete}
        title="Delete component"
        message={`Delete "${toDelete?.name}"? Existing executions that used it are kept but will reference a component that no longer exists.`}
        pending={del.isPending}
        onConfirm={confirmDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  );
}
