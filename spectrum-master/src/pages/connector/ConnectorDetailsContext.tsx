//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useContext, useState, createContext, useMemo, ReactNode, useCallback } from 'react';

import { EMPTY_OBJECT } from 'store/constants';

export interface ConnectorDetailsContextProps {
  visible: boolean;
  defaultMappingsVisible: boolean;
  metaId?: string;
  showConnectorDetails: (visible: boolean, metaId: string) => void;
  showDefaultMappings: (visible: boolean, metaId: string) => void;
}

export interface ConnectorDetailsContextProviderProps {
  children: ReactNode;
  value?: Partial<ConnectorDetailsContextProps>;
}

const ConnectorDetailsContext = createContext<ConnectorDetailsContextProps>({
  visible: false,
  defaultMappingsVisible: false,
  metaId: '',
  showConnectorDetails: (visible: boolean, metaId: string) => {},
  showDefaultMappings: (visible: boolean, metaId: string) => {},
});

export const useConnectorDetailsContext = () => useContext(ConnectorDetailsContext);

export const ConnectorDetailsContextProvider = ({ children, value }: ConnectorDetailsContextProviderProps) => {
  const [visible, setVisible] = useState(false);
  const [defaultMappingsVisible, setDefaultMappingsVisible] = useState(false);
  const [metaId, setMetaId] = useState<string>('');

  // Default visible
  const showConnectorDetails = useCallback((visible: boolean, metaId: string) => {
    setVisible(visible !== false);
    setMetaId(metaId);
  }, []);

  // Default mappings visible
  const showDefaultMappings = useCallback((visible: boolean, metaId: string) => {
    setDefaultMappingsVisible(visible !== false);
    setMetaId(metaId);
  }, []);

  const contextValue = useMemo(() => {
    return {
      ...{
        visible,
        defaultMappingsVisible,
        metaId,
        showConnectorDetails,
        showDefaultMappings,
      },
      ...(value || EMPTY_OBJECT),
    };
  }, [defaultMappingsVisible, metaId, showConnectorDetails, showDefaultMappings, value, visible]);

  return <ConnectorDetailsContext.Provider value={contextValue}>{children}</ConnectorDetailsContext.Provider>;
};
