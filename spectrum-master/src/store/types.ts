//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { AxiosPromise } from 'axios';
import { Action } from 'redux';
import { ThunkAction } from 'redux-thunk';

import { PaginationDirection } from 'components/AgTable/Pagination';
import AppConstants from 'utils/AppConstants';
import { ResponseError } from 'utils/AppUtil';
import { ValuesOf } from 'utils/TypeUtils';

// TODO: move RootState here
import { RootState } from '../reducers';

export type FetchStatus = ValuesOf<typeof AppConstants.FETCH_STATUS>;
export type AuthTypes = ValuesOf<typeof AppConstants.AUTH_TYPES>;

export type PromiseThunkAction<T = any> = ThunkAction<Promise<T>, RootState, unknown, Action<string>>;
export type AxiosPromiseThunkAction<T = any> = ThunkAction<AxiosPromise<T>, RootState, unknown, Action<string>>;

export interface PaginatedApi {
  /** results cursor to start from */
  cursor?: string;

  /** max records to retrieve */
  count?: number;

  /** from cursor, which direction to fetch */
  direction?: PaginationDirection;
}

export type { ResponseError, RootState };
