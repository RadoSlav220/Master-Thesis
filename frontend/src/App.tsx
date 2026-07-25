import { CssBaseline, ThemeProvider, createTheme } from "@mui/material";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/layout/AppLayout";
import Dashboard from "./pages/Dashboard";
import DataSources from "./pages/DataSources";
import Datasets from "./pages/Datasets";
import DatasetDetails from "./pages/DatasetDetails";
import Components from "./pages/Components";
import Executions from "./pages/Executions";
import MapView from "./pages/MapView";

const queryClient = new QueryClient();
const theme = createTheme({ palette: { mode: "light" } });

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <Routes>
            <Route element={<AppLayout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/data-sources" element={<DataSources />} />
              <Route path="/datasets" element={<Datasets />} />
              <Route path="/datasets/:id" element={<DatasetDetails />} />
              <Route path="/components" element={<Components />} />
              <Route path="/executions" element={<Executions />} />
              <Route path="/map" element={<MapView />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
