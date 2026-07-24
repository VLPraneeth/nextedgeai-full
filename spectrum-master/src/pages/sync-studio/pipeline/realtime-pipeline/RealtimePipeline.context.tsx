//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createContext, Dispatch, ReactNode, SetStateAction, useContext, useMemo, useState } from 'react';

export interface RealtimePipelineContextProps {
  visible?: boolean;
  setVisible: Dispatch<SetStateAction<boolean>>;
  disabledVisible?: boolean;
  setDisabledVisible: Dispatch<SetStateAction<boolean>>;
  enabled?: boolean;
  setEnabled: Dispatch<SetStateAction<boolean>>;
  ipWhitelist?: string;
  setIpWhitelist: Dispatch<SetStateAction<string>>;
  saveChanges: () => void;
  graphVersion?: string;
}

export interface RealtimePipelineContextProviderProps {
  children: ReactNode;
  value: any;
  enabled: boolean;
  ipWhitelist?: string;
}

const RealtimePipelineContext = createContext<RealtimePipelineContextProps>({
  visible: false,
  setVisible: () => {},
  disabledVisible: false,
  setDisabledVisible: () => {},
  enabled: false,
  setEnabled: () => {},
  ipWhitelist: '',
  setIpWhitelist: () => {},
  saveChanges: () => {},
});

export const useRealtimePipelineContext = () => useContext(RealtimePipelineContext);

export const RealtimePipelineContextProvider = ({
  children,
  value,
  enabled: savedEnabled,
  ipWhitelist: savedIpWhitelist,
}: RealtimePipelineContextProviderProps) => {
  const [visible, setVisible] = useState(false);
  const [disabledVisible, setDisabledVisible] = useState(false);
  const [enabled, setEnabled] = useState(savedEnabled || false);
  const [ipWhitelist, setIpWhitelist] = useState(savedIpWhitelist || '');
  const { onSaveChanges } = value;

  const contextValue = useMemo(() => {
    return {
      visible,
      setVisible,
      disabledVisible,
      setDisabledVisible,
      setEnabled,
      ipWhitelist,
      setIpWhitelist,
      enabled,
      saveChanges: onSaveChanges,
    };
  }, [disabledVisible, enabled, ipWhitelist, onSaveChanges, visible]);

  return <RealtimePipelineContext.Provider value={contextValue}>{children}</RealtimePipelineContext.Provider>;
};
