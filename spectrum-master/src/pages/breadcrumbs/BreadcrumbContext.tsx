//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Dispatch, ReactNode, SetStateAction } from 'react';
import { useContext, useState, createContext, useMemo } from 'react';

import { EMPTY_OBJECT } from 'store/constants';

export interface BreadcrumbContextProps {
  urlNameMap: Record<string, string>;
  setUrlNameMap: Dispatch<SetStateAction<Record<string, string>>>;
}

export interface BreadcrumbContextProviderProps {
  children: ReactNode;
  value?: Partial<BreadcrumbContextProps>;
}

const BreadcrumbContext = createContext<BreadcrumbContextProps>({
  urlNameMap: {},
  setUrlNameMap: () => {},
});

export const useBreadcrumbContext = () => useContext(BreadcrumbContext);

export const BreadcrumbContextProvider = ({ children, value }: BreadcrumbContextProviderProps) => {
  const [urlNameMap, setUrlNameMap] = useState<Record<string, string>>(EMPTY_OBJECT);

  const contextValue = useMemo(() => {
    return {
      ...{
        urlNameMap,
        setUrlNameMap,
      },
      ...(value || EMPTY_OBJECT),
    };
  }, [urlNameMap, value]);

  return <BreadcrumbContext.Provider value={contextValue}>{children}</BreadcrumbContext.Provider>;
};
