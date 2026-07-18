import {
  Alert,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useState } from "react";
import PageHeader from "../components/common/PageHeader";
import DatasetForm from "../components/forms/DatasetForm";
import { useDatasets } from "../hooks/useDatasets";

export default function Datasets() {
  const [open, setOpen] = useState(false);
  const { data, isLoading, isError } = useDatasets();

  return (
    <>
      <PageHeader
        title="Datasets"
        action={{ label: "Create Dataset", onClick: () => setOpen(true), icon: <AddIcon /> }}
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
                <TableCell>Description</TableCell>
                <TableCell>Created</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((d) => (
                <TableRow key={d.id}>
                  <TableCell>{d.name}</TableCell>
                  <TableCell>{d.type}</TableCell>
                  <TableCell>{d.description ?? "—"}</TableCell>
                  <TableCell>{new Date(d.createdAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
              {data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    No datasets yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      <DatasetForm open={open} onClose={() => setOpen(false)} />
    </>
  );
}
