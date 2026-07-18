import { create } from "zustand";

interface SelectionState {
  selectedDatasetId: string | null;
  selectedComponentId: string | null;
  selectedExecutionId: string | null;
  setSelectedDataset: (id: string | null) => void;
  setSelectedComponent: (id: string | null) => void;
  setSelectedExecution: (id: string | null) => void;
}

export const useSelectionStore = create<SelectionState>((set) => ({
  selectedDatasetId: null,
  selectedComponentId: null,
  selectedExecutionId: null,
  setSelectedDataset: (id) => set({ selectedDatasetId: id }),
  setSelectedComponent: (id) => set({ selectedComponentId: id }),
  setSelectedExecution: (id) => set({ selectedExecutionId: id }),
}));
