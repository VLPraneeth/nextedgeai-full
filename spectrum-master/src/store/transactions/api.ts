import { SystemFilterQuery } from 'store/logs/types';
import { PaginatedApi } from 'store/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { TransactionKpis, TransactionsResponse, TransactionsParams } from './types';

const transactionsApi = injectEndpoints({
  endpoints: (builder) => ({
    getTransactions: builder.query<TransactionsResponse, TransactionsParams & PaginatedApi>({
      query: (params) => ({
        url: makeUrl(DataUrlConstants.TRANSACTIONS_BASE, null, params),
      }),
      providesTags: (result) => [
        ...(result?.records || []).map((trans) => tags.Transaction(trans.id)),
        tags.TransactionList,
      ],
    }),
    getTransactionsByMessage: builder.query<TransactionsResponse, SystemFilterQuery & PaginatedApi>({
      query: ({ syncCycleId, nodeId, message, count, direction, cursor }) => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.TRANSACTIONS_BY_MESSAGE, { syncCycleId, nodeId }, { count, direction, cursor }),
        body: { message },
      }),
      providesTags: (result) => [
        ...(result?.records || []).map((trans) => tags.Transaction(trans.id)),
        tags.TransactionList,
      ],
    }),
    getKpis: builder.query<TransactionKpis, TransactionsParams>({
      query: (params: TransactionsParams) => ({
        url: makeUrl(DataUrlConstants.TRANSACTION_KPIS, null, params),
      }),
    }),
  }),
  overrideExisting: false,
});

export const { useGetTransactionsQuery, useGetKpisQuery, useGetTransactionsByMessageQuery } = transactionsApi;
