import { createContext, Dispatch, SetStateAction, useCallback, useContext, useMemo, useState } from 'react';

import { Header } from 'components/custom-action/ActionHeader';
import { SelectTextValue } from 'components/inputs/select-text/SelectText';
import { ErrorNotificationConfig, NotificationCadence, NotificationStatus } from 'store/error-notifications-v2/types';
import useSetState from 'utils/useSetState';

export interface ErrorNotificationFormContextValue {
  errorNotificationFormState: ErrorNotificationState;
  setErrorNotificationFormState: (state: Partial<ErrorNotificationState>) => void;
  errorNotificationServerState: ErrorNotificationState;
  setErrorNotificationServerState: (state: Partial<ErrorNotificationState>) => void;
  currentNotificationConfig: ErrorNotificationConfig | undefined;
  setCurrentNotificationConfig: Dispatch<SetStateAction<ErrorNotificationConfig | undefined>>;
  reset: () => void;
}

export interface ErrorNotificationState {
  name: string;
  description: string;
  cadence: NotificationCadence;
  emails: string[];
  endpoint: SelectTextValue;
  headers: Header[];
  status: NotificationStatus;
  notificationTypeIds: string[];
}

export const initialErrorNotificationState: ErrorNotificationState = {
  name: '',
  description: '',
  cadence: 'IMMEDIATE',
  emails: [],
  endpoint: {
    selectValue: 'POST',
    textValue: '',
  },
  headers: [],
  status: 'Active',
  notificationTypeIds: [],
};

const ErrorNotificationFormContext = createContext<ErrorNotificationFormContextValue>({
  errorNotificationFormState: initialErrorNotificationState,
  setErrorNotificationFormState: () => {},
  errorNotificationServerState: initialErrorNotificationState,
  setErrorNotificationServerState: () => {},
  currentNotificationConfig: undefined,
  reset: () => {},
  setCurrentNotificationConfig: () => {},
});

export const useErrorNotificationContext = () => useContext(ErrorNotificationFormContext);

export const ErrorNotificationFormContextProvider = ({ children }: { children?: React.ReactNode }) => {
  const [errorNotificationFormState, setErrorNotificationFormState] = useSetState(initialErrorNotificationState);
  const [errorNotificationServerState, setErrorNotificationServerState] = useSetState(initialErrorNotificationState);
  const [currentNotificationConfig, setCurrentNotificationConfig] = useState<ErrorNotificationConfig>();

  const reset = useCallback(() => {
    setErrorNotificationFormState(initialErrorNotificationState);
    setCurrentNotificationConfig(undefined);
    setErrorNotificationServerState(initialErrorNotificationState);
  }, [setErrorNotificationFormState, setErrorNotificationServerState]);

  const value: ErrorNotificationFormContextValue = useMemo(() => {
    return {
      errorNotificationFormState,
      setErrorNotificationFormState,
      errorNotificationServerState,
      setErrorNotificationServerState,
      currentNotificationConfig,
      reset,
      setCurrentNotificationConfig,
    };
  }, [
    errorNotificationFormState,
    errorNotificationServerState,
    setErrorNotificationServerState,
    setErrorNotificationFormState,
    currentNotificationConfig,
    setCurrentNotificationConfig,
    reset,
  ]);

  return <ErrorNotificationFormContext.Provider value={value}>{children}</ErrorNotificationFormContext.Provider>;
};
