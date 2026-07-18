import { Stack } from "@mui/material";
import StorageIcon from "@mui/icons-material/Storage";
import ExtensionIcon from "@mui/icons-material/Extension";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import StatCard from "../components/common/StatCard";
import PageHeader from "../components/common/PageHeader";
import { useDatasets } from "../hooks/useDatasets";
import { useComponents } from "../hooks/useComponents";
import { useExecutions } from "../hooks/useExecutions";

export default function Dashboard() {
  const datasets = useDatasets();
  const components = useComponents();
  const executions = useExecutions();

  const count = (n?: number, loading?: boolean) => (loading ? "…" : (n ?? 0));

  return (
    <>
      <PageHeader title="Dashboard" />
      <Stack direction={{ xs: "column", sm: "row" }} spacing={3} sx={{}}>
        <StatCard
          label="Datasets"
          icon={<StorageIcon fontSize="small" />}
          value={count(datasets.data?.length, datasets.isLoading)}
        />
        <StatCard
          label="Components"
          icon={<ExtensionIcon fontSize="small" />}
          value={count(components.data?.length, components.isLoading)}
        />
        <StatCard
          label="Executions"
          icon={<PlayArrowIcon fontSize="small" />}
          value={count(executions.data?.length, executions.isLoading)}
        />
      </Stack>
    </>
  );
}
