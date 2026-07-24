import { SerializedError } from '@reduxjs/toolkit';
import { FetchBaseQueryError } from '@reduxjs/toolkit/dist/query';

export const getRtkQueryErrorMessage = (
  error: FetchBaseQueryError | SerializedError | undefined,
  customFallbackErrorMessage?: string
): string => {
  if (!error) {
    return '';
  }

  if ('status' in error) {
    // Network request error {status, message, error, timestamp}
    if (typeof error.status === 'number') {
      if (error.data instanceof Object) {
        const data = error.data as any;
        return data.message;
      }
    }

    // FetchBaseQueryError
    switch (error.status) {
      case 'FETCH_ERROR':
        return error.error;
      case 'PARSING_ERROR':
        return error.error;
      case 'CUSTOM_ERROR':
        return error.error;
    }
  }

  // SerializedError
  if ('message' in error) {
    if (error.message) {
      return error.message;
    }
  }

  return customFallbackErrorMessage || 'Error';
};
