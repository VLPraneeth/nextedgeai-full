//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Dispatch, ReactNode, SetStateAction } from 'react';
import { useContext, useState, createContext, useMemo } from 'react';

export interface PipelineLogsContextProps {
  jsonData: string | null;
  setJsonData: Dispatch<SetStateAction<string | null>>;
  nodeMetadata: Record<string, string>;
  setNodeMetadata: Dispatch<SetStateAction<Record<string, string>>>;
}

export interface PipelineLogsContextProviderProps {
  children: ReactNode;
}

const PipelineLogsContext = createContext<PipelineLogsContextProps>({
  jsonData: null,
  setJsonData: () => {},
  nodeMetadata: {},
  setNodeMetadata: () => {},
});

export const usePipelineLogsContext = () => useContext(PipelineLogsContext);

export const PipelineLogsContextProvider = ({ children }: PipelineLogsContextProviderProps) => {
  const [jsonData, setJsonData] = useState<PipelineLogsContextProps['jsonData']>(null);
  const [nodeMetadata, setNodeMetadata] = useState<PipelineLogsContextProps['nodeMetadata']>({});

  const contextValue = useMemo(() => {
    return {
      ...{
        jsonData,
        setJsonData,
        nodeMetadata,
        setNodeMetadata,
      },
    };
  }, [jsonData, nodeMetadata]);

  return <PipelineLogsContext.Provider value={contextValue}>{children}</PipelineLogsContext.Provider>;
};
