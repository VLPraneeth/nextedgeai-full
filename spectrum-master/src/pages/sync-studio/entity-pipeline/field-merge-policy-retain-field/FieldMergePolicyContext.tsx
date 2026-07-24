//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Dispatch, ReactNode, SetStateAction } from 'react';
import { useContext, useState, createContext, useMemo } from 'react';

import { EMPTY_OBJECT } from 'store/constants';

export interface FieldMergePolicyContextProps {
  retainFields: string[];
  setRetainFields: Dispatch<SetStateAction<string[]>>;
}
export interface FieldMergePolicyContextProviderProps {
  children: ReactNode;
  value?: Partial<FieldMergePolicyContextProps>;
}

const FieldMergePolicyContext = createContext<FieldMergePolicyContextProps>({
  retainFields: [],
  setRetainFields: () => {},
});

export const useFieldMergePolicyContext = () => useContext(FieldMergePolicyContext);

export const FieldMergePolicyContextProvider = ({ children, value }: FieldMergePolicyContextProviderProps) => {
  const [retainFields, setRetainFields] = useState<FieldMergePolicyContextProps['retainFields']>([]);

  const contextValue = useMemo(() => {
    return {
      retainFields,
      setRetainFields,
      ...(value || EMPTY_OBJECT),
    };
  }, [retainFields, value]);

  return <FieldMergePolicyContext.Provider value={contextValue}>{children}</FieldMergePolicyContext.Provider>;
};
