import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { Batch, BatchOperation, BatchListResponse, BatchDeleteParams, BatchUpdateParams } from './types';

const BatchDeleteList = tags.Batch('DELETE_KEY');
const BatchUpdateList = tags.Batch('UPDATE_KEY');

// helper to extract the updated batch from a response
const getProvidedBatchIdFromResponse = (result?: Batch) => (result ? [tags.Batch(result.id)] : []);

const dataStudioBatchApi = injectEndpoints({
  endpoints: (builder) => ({
    cancelBatch: builder.mutation<Batch, { batchId: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.DATA_STUDIO_BATCH_CANCEL, params), method: 'PATCH' }),
      invalidatesTags: (_, __, params) => [tags.Batch(params.batchId), tags.BatchList],
    }),
    getBatch: builder.query<Batch, { batchId: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.DATA_STUDIO_BATCH, params) }),
      providesTags: getProvidedBatchIdFromResponse,
    }),
    getBatchesForEntity: builder.query<BatchListResponse, { entityId: string; operation?: BatchOperation }>({
      query: ({ entityId, operation }) => ({
        url: makeUrl(DataUrlConstants.DATA_STUDIO_ENTITY_BATCHES, { entityId }, { operation }),
      }),
      providesTags: (result) => [...(result || []).map((batch) => tags.Batch(batch.id)), tags.BatchList],
    }),
    deleteRecords: builder.mutation<Batch, BatchDeleteParams>({
      query: ({ entityId, predicate, ...params }) => ({
        url: makeUrl(DataUrlConstants.DATA_STUDIO_RECORDS_BATCH_DELETE, { entityId }, params),
        method: 'POST',
        body: { predicate },
      }),
      // TODO: Invalidate DS Record Ids in the future as well
      invalidatesTags: (result) => [...getProvidedBatchIdFromResponse(result), tags.BatchList, BatchDeleteList],
    }),
    updateRecords: builder.mutation<Batch, BatchUpdateParams>({
      query: ({ entityId, fields, predicate }) => ({
        url: makeUrl(DataUrlConstants.DATA_STUDIO_RECORDS_BATCH, { entityId }),
        method: 'PATCH',
        body: { updates: fields, predicate },
      }),
      // TODO: Invalidate DS Record Ids in the future as well
      invalidatesTags: (result) => [...getProvidedBatchIdFromResponse(result), tags.BatchList, BatchUpdateList],
    }),
  }),
  overrideExisting: false,
});

export const {
  useCancelBatchMutation,
  useDeleteRecordsMutation,
  useGetBatchQuery,
  useGetBatchesForEntityQuery,
  useLazyGetBatchQuery,
  useLazyGetBatchesForEntityQuery,
  useUpdateRecordsMutation,
  util,
} = dataStudioBatchApi;
