import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useNavigate, useParams } from "react-router-dom";
import { useMemo } from "react";
import PageHeader from "../components/common/PageHeader";
import GeoJsonMap from "../map/GeoJsonMap";
import { useDataset, useDatasetGeoJson } from "../hooks/useDatasets";
import { detectValueProperty, geometryTypes } from "../utils/resultParser";

export default function DatasetDetails() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const dataset = useDataset(id);
  const geo = useDatasetGeoJson(dataset.data?.hasGeoJson ? id : null);

  const fc = geo.data ?? null;
  const types = useMemo(() => geometryTypes(fc), [fc]);
  const valueProperty = useMemo(() => detectValueProperty(fc), [fc]);
  const featureCount = fc?.features.length ?? 0;

  return (
    <>
      <PageHeader title="Dataset Details" />
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/datasets")} sx={{ mb: 2 }}>
        Back to datasets
      </Button>

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
                {dataset.data.hasGeoJson && <Chip label="GeoJSON" color="success" />}
              </Stack>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
                {dataset.data.description || "No description."}
              </Typography>

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
        </Stack>
      )}
    </>
  );
}
