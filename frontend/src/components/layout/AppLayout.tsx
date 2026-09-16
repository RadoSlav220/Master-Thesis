import {
  AppBar,
  Box,
  CssBaseline,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
} from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import StorageIcon from "@mui/icons-material/Storage";
import CloudIcon from "@mui/icons-material/Cloud";
import ExtensionIcon from "@mui/icons-material/Extension";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import { NavLink, Outlet } from "react-router-dom";
import type { ReactNode } from "react";

const DRAWER_WIDTH = 220;

const NAV: { label: string; to: string; icon: ReactNode }[] = [
  { label: "Dashboard", to: "/", icon: <DashboardIcon /> },
  { label: "Data Sources", to: "/data-sources", icon: <CloudIcon /> },
  { label: "Datasets", to: "/datasets", icon: <StorageIcon /> },
  { label: "Components", to: "/components", icon: <ExtensionIcon /> },
  { label: "Executions", to: "/executions", icon: <PlayArrowIcon /> },
];

export default function AppLayout() {
  return (
    <Box sx={{ display: "flex" }}>
      <CssBaseline />
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" noWrap>
            Digital Twin Orchestration Platform
          </Typography>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: "border-box" },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: "auto" }}>
          <List>
            {NAV.map((item) => (
              <ListItemButton
                key={item.to}
                component={NavLink}
                to={item.to}
                end={item.to === "/"}
                sx={{ "&.active": { bgcolor: "action.selected" } }}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
        </Box>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
