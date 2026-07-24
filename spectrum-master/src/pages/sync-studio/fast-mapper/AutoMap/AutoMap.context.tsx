//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Dispatch, ReactNode, SetStateAction } from 'react';
import { useContext, useState, createContext, useMemo } from 'react';

export interface AutoMapContextProps {
  visible?: boolean;
  setVisible: Dispatch<SetStateAction<boolean>>;
}

export interface AutoMapContextProviderProps {
  children: ReactNode;
}

const AutoMapContext = createContext<AutoMapContextProps>({
  visible: false,
  setVisible: () => {},
});

export const useAutoMapContext = () => useContext(AutoMapContext);

export const AutoMapContextProvider = ({ children }: AutoMapContextProviderProps) => {
  const [visible, setVisible] = useState(false);

  const contextValue = useMemo(() => {
    return {
      visible,
      setVisible,
    };
  }, [visible]);

  return <AutoMapContext.Provider value={contextValue}>{children}</AutoMapContext.Provider>;
};
