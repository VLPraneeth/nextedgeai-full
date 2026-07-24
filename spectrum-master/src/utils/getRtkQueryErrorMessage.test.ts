import { SerializedError } from '@reduxjs/toolkit';
import { FetchBaseQueryError } from '@reduxjs/toolkit/dist/query';

import { getRtkQueryErrorMessage } from './getRtkQueryErrorMessage';

describe('getRtkQueryErrorMessage', () => {
  it('returns a message from SerializedError', () => {
    const serialError: SerializedError = {
      message: 'Something went wrong',
    };

    expect(getRtkQueryErrorMessage(serialError)).toEqual(serialError.message);
  });

  it('returns a message from network error', () => {
    const apiResponseError = {
      status: 500,
      data: {
        status: 500,
        message: 'No author config found',
      },
    };

    expect(getRtkQueryErrorMessage(apiResponseError)).toEqual(apiResponseError.data.message);
  });

  it('returns a message from FetchBaseQueryError', () => {
    const fetchError: FetchBaseQueryError = {
      status: 'FETCH_ERROR',
      error: 'fetch error',
    };
    const parseError: FetchBaseQueryError = {
      originalStatus: 200,
      status: 'PARSING_ERROR',
      data: 'parsing data',
      error: 'parsing error',
    };
    const customError: FetchBaseQueryError = {
      status: 'CUSTOM_ERROR',
      error: 'custom error',
    };

    expect(getRtkQueryErrorMessage(fetchError)).toEqual(fetchError.error);
    expect(getRtkQueryErrorMessage(parseError)).toEqual(parseError.error);
    expect(getRtkQueryErrorMessage(customError)).toEqual(customError.error);
  });
});
