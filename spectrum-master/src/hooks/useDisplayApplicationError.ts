//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { notification } from 'antd';
import { delay } from 'lodash';
import { useCallback, useEffect } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { selectError } from 'selectors/appSelectors';
import { clearErrorMessage } from 'store/app/actions';

const useDisplayApplicationError = () => {
  const dispatch = useEnhancedDispatch();

  const error = useEnhancedSelector(selectError);
  const previousError = usePreviousValue(error);

  const showError = useCallback(
    (errorTitle: string, errorMessage: string) => {
      notification.error({
        message: errorTitle || 'Error',
        description: errorMessage,
      });

      // Reseting the error message so when the next error happens it will
      // appear even if the message is the same.
      delay(() => dispatch(clearErrorMessage()), 0);
    },
    [dispatch]
  );

  useEffect(() => {
    // Currently, the backend returns a 500 error for 'Access is denied' messages,
    // so we must filter by the value returned by the backend.
    // NOTE: a better fix is coming with SYN-10384 (UI) & SYN-10385 (BE)
    if (error?.message && error.message !== previousError?.message) {
      showError(error.title as string, error.message);
    }
  }, [error, previousError?.message, showError]);
};

export default useDisplayApplicationError;
