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
import PageHeader from "../components/common/PageHeader";
import { useComponents } from "../hooks/useComponents";

export default function Components() {
  const { data, isLoading, isError } = useComponents();

  return (
    <>
      <PageHeader title="Components" />
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
    </>
  );
}
