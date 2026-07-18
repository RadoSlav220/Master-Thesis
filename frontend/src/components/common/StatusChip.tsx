import { Chip } from "@mui/material";
import type { ExecutionStatus } from "../../types";

const COLOR: Record<ExecutionStatus, "default" | "info" | "success" | "error"> = {
  CREATED: "default",
  RUNNING: "info",
  COMPLETED: "success",
  FAILED: "error",
};

export default function StatusChip({ status }: { status: ExecutionStatus }) {
  return <Chip label={status} color={COLOR[status]} size="small" />;
}
