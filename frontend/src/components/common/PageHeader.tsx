import { Box, Button, Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  action?: { label: string; onClick: () => void; icon?: ReactNode };
}

export default function PageHeader({ title, action }: PageHeaderProps) {
  return (
    <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 3 }}>
      <Typography variant="h4">{title}</Typography>
      <Box>
        {action && (
          <Button variant="contained" startIcon={action.icon} onClick={action.onClick}>
            {action.label}
          </Button>
        )}
      </Box>
    </Stack>
  );
}
