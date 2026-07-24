import { createContext, useContext, useMemo, useState } from 'react';

export interface DatasetAuthoringContextValue {
  selectedDatasetId: string | null;
  setSelectedDatasetId: (card: string | null) => void;
  showDatasetWizard: boolean;
  setShowDatasetWizard: (show: boolean) => void;
}

const DatasetAuthoringContext = createContext<DatasetAuthoringContextValue>({
  selectedDatasetId: null,
  setSelectedDatasetId: () => {},
  showDatasetWizard: false,
  setShowDatasetWizard: () => {},
});

export const useDatasetAuthoringContext = () => useContext(DatasetAuthoringContext);

export const DatasetAuthoringContextProvider = ({ children }: { children?: React.ReactNode }) => {
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(null);
  const [showDatasetWizard, setShowDatasetWizard] = useState(false);

  const value = useMemo(() => {
    return {
      selectedDatasetId,
      setSelectedDatasetId,
      showDatasetWizard,
      setShowDatasetWizard,
    };
  }, [setSelectedDatasetId, selectedDatasetId, showDatasetWizard, setShowDatasetWizard]);

  return <DatasetAuthoringContext.Provider value={value}>{children}</DatasetAuthoringContext.Provider>;
};
