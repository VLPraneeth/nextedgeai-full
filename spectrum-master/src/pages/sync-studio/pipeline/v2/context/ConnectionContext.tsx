import { useConnection } from '@xyflow/react';
import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';

interface ConnectionContextType {
  connectionSource: string;
  connectionTarget: string;
}

const ConnectionContext = createContext<ConnectionContextType>({ connectionSource: '', connectionTarget: '' });

export const ConnectionProvider = ({ children }: { children: ReactNode }) => {
  const connection = useConnection();
  const [connectionSource, setConnectionSource] = useState('');
  const [connectionTarget, setConnectionTarget] = useState('');

  useEffect(() => {
    if (connection.isValid !== undefined) {
      setConnectionSource(connection.isValid ? connection.fromNode?.id || '' : '');
      setConnectionTarget(connection.isValid ? connection.toNode?.id || '' : '');
    }
  }, [connection.isValid, connection.fromNode?.id, connection.toNode?.id]);

  const value = useMemo(
    () => ({
      connectionSource,
      connectionTarget,
    }),
    [connectionSource, connectionTarget]
  );

  return <ConnectionContext.Provider value={value}>{children}</ConnectionContext.Provider>;
};

export const useConnectionContext = () => useContext(ConnectionContext);
