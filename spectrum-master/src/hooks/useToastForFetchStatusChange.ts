import message from 'antd/lib/message';
import { useEffect, useRef } from 'react';

import usePreviousValue from 'hooks/usePreviousValue';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';

interface MessageConfig {
  content: string | JSX.Element | React.ReactNode;
  duration?: number;
  onClose?: () => void;
}

type MessageType = 'info' | 'success' | 'error' | 'warning' | 'loading';
type MessageTypeMap = Record<FetchStatus, MessageType>;

const defaultMessageTypes: MessageTypeMap = {
  [AppConstants.FETCH_STATUS.IDLE]: 'info',
  [AppConstants.FETCH_STATUS.LOADING]: 'loading',
  [AppConstants.FETCH_STATUS.SUCCESS]: 'success',
  [AppConstants.FETCH_STATUS.ERROR]: 'error',
};

type FetchStatusToastConfig = {
  [K in FetchStatus]?: MessageConfig | string | null;
};

/**
 * @returns the fetch status used to show a toast, null if status hasn't
 * changed. This can be useful to use in a useEffect to trigger other logic like
 * refetching data on success.
 */
const useToastForFetchStatusChange = (
  fetchStatus: FetchStatus,
  config: FetchStatusToastConfig,
  messageTypes: MessageTypeMap = defaultMessageTypes
) => {
  const previousValue = usePreviousValue<FetchStatus>(fetchStatus);

  let newFetchStatus: null | FetchStatus = null;

  // When the status changes we want to close nay previous toast displayed
  const closePreviousToastRef = useRef<null | (() => void)>(null);

  // SKIP toast if we don't have a previous value (first render)
  // OR if our last value is the same as the current value
  if (previousValue && fetchStatus && previousValue !== fetchStatus) {
    newFetchStatus = fetchStatus;
  }

  useEffect(() => {
    if (newFetchStatus) {
      closePreviousToastRef.current?.();
      closePreviousToastRef.current = null;
      const messageConfig = config[newFetchStatus];
      const messageType = messageTypes[newFetchStatus];

      // we must have both config and type or we'll exit
      if (!(messageConfig && messageType)) {
        return;
      }

      if (messageConfig && messageType) {
        let content, duration, onClose;

        if (typeof messageConfig === 'string') {
          content = messageConfig;
        } else {
          content = messageConfig.content;
          duration = messageConfig.duration;
          onClose = messageConfig.onClose;
        }

        closePreviousToastRef.current = message[messageType]?.(content, duration, onClose);
      }
    }
  }, [config, messageTypes, newFetchStatus]);

  return newFetchStatus;
};

export default useToastForFetchStatusChange;
