import { Card, CardContent, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface StatCardProps {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
}

export default function StatCard({ label, value, icon }: StatCardProps) {
  return (
    <Card sx={{ minWidth: 200, flex: 1 }}>
      <CardContent>
        <Typography color="text.secondary" gutterBottom sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {icon}
          {label}
        </Typography>
        <Typography variant="h3" component="div">
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}
