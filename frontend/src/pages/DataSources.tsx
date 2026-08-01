import {
  Alert,
  Button,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import CloudDownloadIcon from "@mui/icons-material/CloudDownload";
import { useState } from "react";
import PageHeader from "../components/common/PageHeader";
import DataSourceDetails from "../components/common/DataSourceDetails";
import DataSourceForm from "../components/forms/DataSourceForm";
import FetchDatasetForm from "../components/forms/FetchDatasetForm";
import { useDataSources } from "../hooks/useDataSources";
import type { DataSource } from "../types";

export default function DataSources() {
  const [open, setOpen] = useState(false);
  const [fetchSource, setFetchSource] = useState<DataSource | null>(null);
  const [detailsSource, setDetailsSource] = useState<DataSource | null>(null);
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
    </>
  );
}
