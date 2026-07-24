//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { sortBy } from 'lodash';

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { ReferenceDataRecord } from './types';

const referenceDataApi = injectEndpoints({
  endpoints: (builder) => ({
    getReferenceData: builder.query<ReferenceDataRecord[], void>({
      query: () => ({
        url: DataUrlConstants.REFERENCE_DATA,
      }),
      providesTags: () => [tags.ReferenceDataList],
      transformResponse: (referenceData: ReferenceDataRecord[]) => sortBy(referenceData, ['name']),
    }),
    deleteReferenceData: builder.mutation<void, string>({
      query: (refMetaId) => ({
        url: makeUrl(DataUrlConstants.DELETE_REF_DATA, { refMetaId }),
        method: 'DELETE',
      }),
      invalidatesTags: () => [tags.ReferenceDataList],
    }),
  }),
});

export const {
  useGetReferenceDataQuery,
  useLazyGetReferenceDataQuery,
  useDeleteReferenceDataMutation,
  util: referenceDataApiUtil,
} = referenceDataApi;
