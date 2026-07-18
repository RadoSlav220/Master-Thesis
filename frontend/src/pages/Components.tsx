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
import ComponentForm from "../components/forms/ComponentForm";
import { useComponents } from "../hooks/useComponents";

export default function Components() {
  const [open, setOpen] = useState(false);
  const { data, isLoading, isError } = useComponents();

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
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{c.name}</TableCell>
                  <TableCell>{c.endpointUrl}</TableCell>
                  <TableCell>{c.description ?? "—"}</TableCell>
                </TableRow>
              ))}
              {data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    No components yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
      )}
      <ComponentForm open={open} onClose={() => setOpen(false)} />
    </>
  );
}
