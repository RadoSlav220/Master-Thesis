import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DownloadIcon from "@mui/icons-material/Download";
import ScienceIcon from "@mui/icons-material/Science";
import { useNavigate, useParams } from "react-router-dom";
import { useMemo } from "react";
import PageHeader from "../components/common/PageHeader";
import GeoJsonMap from "../map/GeoJsonMap";
import { useAnalyzeDataset, useDataset, useDatasetGeoJson, useDownloadDataset } from "../hooks/useDatasets";
import { detectValueProperty, geometryTypes } from "../utils/resultParser";

export default function DatasetDetails() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const dataset = useDataset(id);
  const geo = useDatasetGeoJson(dataset.data?.hasGeoJson ? id : null);
  const analyze = useAnalyzeDataset();
  const download = useDownloadDataset();

  const fc = geo.data ?? null;
  const types = useMemo(() => geometryTypes(fc), [fc]);
  const valueProperty = useMemo(() => detectValueProperty(fc), [fc]);
  const featureCount = fc?.features.length ?? 0;

  // Prefer freshly re-run analysis; otherwise fall back to the persisted result.
  const persistedAnalysis = useMemo(() => {
    const raw = dataset.data?.analysisResult;
    if (!raw) return null;
    try {
      return JSON.parse(raw) as { datasetType: string; columns?: string[]; properties?: string[] };
    } catch {
      return null;
    }
  }, [dataset.data?.analysisResult]);

  const analysis = analyze.data ?? persistedAnalysis;
  const analyzedItems = analysis?.columns ?? analysis?.properties ?? [];
  const analyzedLabel = analysis?.columns ? "Columns" : "Properties";
  const canAnalyze = !!dataset.data?.hasGeoJson || dataset.data?.type === "CSV";

  const provenance = dataset.data?.provenance ?? null;
  const queryParamEntries =
    provenance?.type === "API" ? Object.entries(provenance.queryParameters ?? {}) : [];

  return (
    <>
      <PageHeader title="Dataset Details" />
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/datasets")}>
          Back to datasets
        </Button>
        <Button
          startIcon={<DownloadIcon />}
          onClick={() => download.mutate(id)}
          disabled={download.isPending}
        >
          Download
        </Button>
      </Stack>

      {download.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Download failed — the dataset may have no content.
        </Alert>
      )}

      {dataset.isLoading && <CircularProgress />}
      {dataset.isError && <Alert severity="error">Failed to load dataset.</Alert>}

      {dataset.data && (
        <Stack spacing={3} sx={{}}>
          <Card>
            <CardContent>
              <Typography variant="h5" gutterBottom>
                {dataset.data.name}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: "wrap" }}>
                <Chip label={dataset.data.type} />
                {dataset.data.datasetOrigin && (
                  <Chip label={dataset.data.datasetOrigin} variant="outlined" />
                )}
              </Stack>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
                {dataset.data.description || "No description."}
              </Typography>

              {queryParamEntries.length > 0 && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    Query Parameters
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Value</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {queryParamEntries.map(([key, value]) => (
                        <TableRow key={key}>
                          <TableCell>{key}</TableCell>
                          <TableCell>{value}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}

              <Typography variant="subtitle1">Features: {geo.isLoading ? "…" : featureCount}</Typography>
              <Typography variant="subtitle2" sx={{ mt: 1 }}>
                Geometry Types:
              </Typography>
              {types.length > 0 ? (
                <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: "wrap" }}>
                  {types.map((t) => (
                    <Chip key={t} label={t} size="small" variant="outlined" />
                  ))}
                </Stack>
              ) : (
                <Typography variant="body2" sx={{ color: "text.secondary" }}>
                  {dataset.data.hasGeoJson ? "—" : "No geometry (non-GeoJSON dataset)."}
                </Typography>
              )}
            </CardContent>
          </Card>

          {dataset.data.hasGeoJson && (
            <Box>
              <Typography variant="h6" gutterBottom>
                Geometry Preview
              </Typography>
              {geo.isLoading ? <CircularProgress /> : <GeoJsonMap data={fc} valueProperty={valueProperty} />}
            </Box>
          )}

          {canAnalyze && (
            <Card>
              <CardContent>
                <Stack
                  direction="row"
                  spacing={2}
                  sx={{ mb: 2, alignItems: "center", justifyContent: "space-between" }}
                >
                  <Typography variant="h6">Dataset Analysis</Typography>
                  <Button
                    variant="contained"
                    startIcon={<ScienceIcon />}
                    onClick={() => analyze.mutate(id)}
                    disabled={analyze.isPending}
                  >
                    {persistedAnalysis ? "Re-analyze" : "Analyze Dataset"}
                  </Button>
                </Stack>

                {analyze.isPending && <CircularProgress size={24} />}
                {analyze.isError && (
                  <Alert severity="error">
                    Analysis failed — the analysis service may be unavailable.
                  </Alert>
                )}

                {analysis && (
                  <>
                    <Typography variant="subtitle1" gutterBottom>
                      Dataset Type: <Chip label={analysis.datasetType} size="small" />
                    </Typography>
                    <Typography variant="subtitle2">{analyzedLabel}</Typography>
                    <List dense>
                      {analyzedItems.map((item) => (
                        <ListItem key={item} disableGutters>
                          <ListItemIcon sx={{ minWidth: 32 }}>
                            <CheckCircleIcon color="success" fontSize="small" />
                          </ListItemIcon>
                          <ListItemText primary={item} />
                        </ListItem>
                      ))}
                      {analyzedItems.length === 0 && (
                        <ListItem disableGutters>
                          <ListItemText primary="No columns or properties found." />
                        </ListItem>
                      )}
                    </List>
                  </>
                )}
              </CardContent>
            </Card>
          )}
        </Stack>
      )}
    </>
  );
}
