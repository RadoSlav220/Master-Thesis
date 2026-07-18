import { Box, Typography } from "@mui/material";
import { prettyJson } from "../../utils/resultParser";

export default function JsonViewer({ json }: { json: string | null | undefined }) {
  const text = prettyJson(json);
  if (!text) return <Typography color="text.secondary">No result.</Typography>;
  return (
    <Box
      component="pre"
      sx={{
        m: 0,
        p: 2,
        bgcolor: "grey.900",
        color: "grey.100",
        borderRadius: 1,
        overflow: "auto",
        maxHeight: 400,
        fontSize: 13,
      }}
    >
      {text}
    </Box>
  );
}
