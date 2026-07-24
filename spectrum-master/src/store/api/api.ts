import { BaseQueryFn, FetchArgs, FetchBaseQueryError, createApi } from '@reduxjs/toolkit/query/react';

import { makeBaseQuery, redirectToLogin } from 'utils/AjaxUtil';
import { RESPONSE_CODE } from 'utils/AppUtil';

import { EntityTag } from './types';

interface ErrorMessage {
  ' message '?: string;
  message?: string;
}
const baseQuery = makeBaseQuery('/');
const baseQueryWithErrorHandling: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (
  args,
  api,
  extraOptions
) => {
  let result = await baseQuery(args, api, extraOptions);
  if (result.error && result.error.status === RESPONSE_CODE.UNAUTHORIZED) {
    const message = (result?.error?.data as ErrorMessage)[' message '] || (result?.error?.data as ErrorMessage).message;
    redirectToLogin(message);
  }
  return result;
};

const baseApi = createApi({
  baseQuery: baseQueryWithErrorHandling,
  endpoints: () => ({}),
  tagTypes: Object.values(EntityTag),
  keepUnusedDataFor: 30,
  refetchOnMountOrArgChange: 15,
});

export const {
  // @ts-ignore Seeing a TS serialization error here. It's annoying but shouldn't affect code completion or types
  injectEndpoints,
  middleware,
  reducer,
  reducerPath,
} = baseApi;

export type ApiState = ReturnType<typeof reducer>;
