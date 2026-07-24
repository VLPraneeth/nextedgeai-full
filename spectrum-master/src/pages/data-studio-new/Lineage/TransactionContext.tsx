import { createContext, useContext, useState } from 'react';

import { PANEL_ACTIONS, PANEL_OPTIONS } from './common';

export type TransactionState = {
  detailPanelContent: String;
};

const defaultState = {
  detailPanelContent: PANEL_OPTIONS.CHANGES,
  enableErrorsPanel: () => {},
  enableChangesPanel: () => {},
};

export type TransactionContextType = {
  detailPanelContent: String;
  enableErrorsPanel: () => void;
  enableChangesPanel: () => void;
};

export interface TransactionContextProviderProps {
  children: React.ReactNode;
  value?: TransactionContextType;
}

type ActionType = {
  type: PANEL_ACTIONS;
  payload: number;
};

export const panelContentReducer = (state: TransactionState, action: ActionType) => {
  switch (action.type) {
    case PANEL_ACTIONS.ENABLE_CHANGES_MODAL:
      return { ...state, detailPanelContent: PANEL_OPTIONS.CHANGES };
    case PANEL_ACTIONS.ENABLE_ERRORS_MODAL:
      return { ...state, detailPanelContent: PANEL_OPTIONS.ERRORS };
  }
};

const TransactionCtx = createContext<TransactionContextType>(defaultState);

export const TransactionContextProvider = ({ children }: TransactionContextProviderProps) => {
  const [detailPanelContent, setDetailPanelContent] = useState<TransactionState['detailPanelContent']>(
    defaultState.detailPanelContent
  );

  const enableErrorsPanel = () => {
    setDetailPanelContent(PANEL_OPTIONS.ERRORS);
  };

  const enableChangesPanel = () => {
    setDetailPanelContent(PANEL_OPTIONS.CHANGES);
  };

  return (
    <TransactionCtx.Provider value={{ detailPanelContent, enableChangesPanel, enableErrorsPanel }}>
      {children}
    </TransactionCtx.Provider>
  );
};

export const useTransactionContext = () => {
  const { detailPanelContent, enableChangesPanel, enableErrorsPanel } = useContext(TransactionCtx);
  return {
    detailPanelContent,
    enableChangesPanel,
    enableErrorsPanel,
  };
};
