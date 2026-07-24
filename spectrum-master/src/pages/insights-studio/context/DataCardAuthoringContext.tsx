import { isEqual } from 'lodash';
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { Layout } from 'react-grid-layout';

import { useLazyGetDataCardQuery } from 'store/insights-studio';
import { DataCard } from 'store/insights-studio/types';

export interface AddToDashInfo {
  dashboardId: string;
  layouts?: Layout[];
  item?: Layout;
}

export type NewCardInfo = Pick<DataCard, 'displayName' | 'name' | 'tags' | 'description'>;

export interface DataCardAuthoringContextValue {
  /**
   * Takes an ID, requests data card from API and stores result in context state.
   * Passing `null` clears selected data card from state.
   */
  selectDataCardById: (dataCardId: string | null) => void;
  selectedDataCard: DataCard | null;

  /**
   * Used to control open/closed state of DataCardWizard
   */
  setShowDataCardWizard: (show: boolean) => void;
  showDataCardWizard: boolean;

  /**
   * Used to control open/closed state of UnifiedDataCardWizard
   */
  showUnifiedDataCardWizard: boolean;
  setShowUnifiedDataCardWizard: (show: boolean) => void;

  /**
   * Sets necessary state to create a data card by dragging a dataset onto a dashboard
   */
  createDataCardFromDataset: (datasetId: string, dashboardDetails: AddToDashInfo) => void;
  addToDashInfo: AddToDashInfo | null;
  addToDashAfterCreate: boolean;
  preselectedDatasetId: string;

  /**
   * Set the dataset id of a datacard from a different step of datacard authoring
   */
  setPreselectedDatasetId: (datasetId: string) => void;

  /**
   * Sets necessary state to add a card to a dashboard after creation
   */
  createCardAndAddToDashboard: (dashboardId: string) => void;

  /**
   * Captures data from BasicInfoForm for use in creating data card in DataCardConfigStep
   */
  setNewCardInfo: (card: NewCardInfo) => void;
  newCardInfo: NewCardInfo | null;

  /**
   * Resets all authoring state to defaults. Call this when completing or cancelling data card wizards
   */
  resetAuthoring: () => void;

  /**
   * Takes a data card ID and sets necessary state to edit that data card
   */
  editDataCard: (dataCardId: string) => void;
}

const DataCardAuthoringContext = createContext<DataCardAuthoringContextValue>({
  selectedDataCard: null,
  selectDataCardById: () => {},

  showDataCardWizard: false,
  setShowDataCardWizard: () => {},

  showUnifiedDataCardWizard: false,
  setShowUnifiedDataCardWizard: () => {},

  createDataCardFromDataset: () => {},
  addToDashInfo: null,
  addToDashAfterCreate: false,
  preselectedDatasetId: '',
  setPreselectedDatasetId: () => {},

  createCardAndAddToDashboard: () => {},

  newCardInfo: null,
  setNewCardInfo: () => {},

  resetAuthoring: () => {},
  editDataCard: () => {},
});

export const useDataCardAuthoringContext = () => useContext(DataCardAuthoringContext);

export const DataCardAuthoringContextProvider = ({ children }: { children?: React.ReactNode }) => {
  const [selectedDataCard, setSelectedDataCard] = useState<DataCard | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [showDataCardWizard, setShowDataCardWizard] = useState(false);
  const [showUnifiedDataCardWizard, setShowUnifiedDataCardWizard] = useState(false);
  const [preselectedDatasetId, setPreselectedDatasetId] = useState('');

  const [newCardInfo, setNewCardInfo] = useState<NewCardInfo | null>(null);
  const [addToDashInfo, setAddToDashInfo] = useState<AddToDashInfo | null>(null);
  const [addToDashAfterCreate, setAddToDashAfterCreate] = useState(false);

  const [getDataCard, result] = useLazyGetDataCardQuery();

  useEffect(() => {
    if (isEditing && !result.isFetching && result.data && !isEqual(result.data, selectedDataCard)) {
      setSelectedDataCard(result.data);
    }
  }, [result.data, result.isFetching, selectedDataCard, result.fulfilledTimeStamp, isEditing]);

  const value = useMemo<DataCardAuthoringContextValue>(() => {
    const selectDataCardById = (dataCardId: string | null) => {
      if (!dataCardId) {
        setSelectedDataCard(null);
        setIsEditing(false);
        return;
      }
      if (selectedDataCard?.id !== dataCardId) {
        getDataCard(dataCardId);
        setIsEditing(true);
      }
    };

    const createDataCardFromDataset: DataCardAuthoringContextValue['createDataCardFromDataset'] = (
      datasetId,
      dashboardDetails
    ) => {
      setAddToDashAfterCreate(true);
      setAddToDashInfo({
        ...addToDashInfo,
        ...dashboardDetails,
      });
      setPreselectedDatasetId(datasetId);
    };

    const createCardAndAddToDashboard: DataCardAuthoringContextValue['createCardAndAddToDashboard'] = (dashboardId) => {
      setAddToDashAfterCreate(true);
      setAddToDashInfo({ dashboardId });
    };

    const editDataCard = (dataCardId: string) => {
      selectDataCardById(dataCardId);
      setShowUnifiedDataCardWizard(true);
    };

    const resetAuthoring = () => {
      setSelectedDataCard(null);
      setIsEditing(false);
      setShowDataCardWizard(false);
      setShowUnifiedDataCardWizard(false);
      setPreselectedDatasetId('');
      setNewCardInfo(null);
      setAddToDashInfo(null);
      setAddToDashAfterCreate(false);
    };

    return {
      selectedDataCard,
      selectDataCardById,

      showDataCardWizard,
      setShowDataCardWizard,

      showUnifiedDataCardWizard,
      setShowUnifiedDataCardWizard,

      createDataCardFromDataset,
      setPreselectedDatasetId,
      preselectedDatasetId,
      addToDashAfterCreate,
      addToDashInfo,

      createCardAndAddToDashboard,

      newCardInfo,
      setNewCardInfo,

      resetAuthoring,

      editDataCard,
    };
  }, [
    addToDashAfterCreate,
    addToDashInfo,
    getDataCard,
    newCardInfo,
    preselectedDatasetId,
    selectedDataCard,
    showDataCardWizard,
    showUnifiedDataCardWizard,
  ]);

  return <DataCardAuthoringContext.Provider value={value}>{children}</DataCardAuthoringContext.Provider>;
};
