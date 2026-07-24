//
// Copyright (c) 2021 Syncari All rights reserved.
//

import { ColDef } from 'ag-grid-community';
import { keyBy } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import AgTable, { CursorBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import { AgTableProps } from 'components/AgTable/AgTable';
import { useI18nContext } from 'components/I18nProvider';
import { EnhancedAgCellRendererParams, I18nStringRenderer, withAntRenderer } from 'components/renderers';
import { LinkRenderer } from 'components/renderers/LinkRenderer';
import { rendererWrapper as TransactionDateRenderer } from 'components/renderers/TransactionDate';
import RouteSpin from 'components/RouteSpin';
import useUserLocalMoment from 'hooks/moment';
import { useCursorPagination } from 'hooks/pagination';
import useQueryParams from 'hooks/useQueryParams';
import useSyncariEntities from 'hooks/useSyncariEntities';
import { rendererWrapper as TransactionChangesRenderer } from 'pages/logs/TransactionChangesRenderer';
import { rendererWrapper as TransactionErrorsRenderer } from 'pages/logs/TransactionErrorsRenderer';
import { usePipelineErrorSystemFilter } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import {
  Transaction,
  TransactionsParams,
  useGetTransactionsByMessageQuery,
  useGetTransactionsQuery,
} from 'store/transactions';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from './common';
import { TransactionContextProvider } from './TransactionContext';
import TransactionDetailsPanel from './TransactionDetailsPanel';
import { TransactionsTableQueryParams } from './types';

import './Transactions.less';

type TransactionColumnKey =
  | 'errors'
  | 'changes'
  | 'operation'
  | 'sources'
  | 'entityName'
  | 'createdAt'
  | 'syncariId'
  | 'transactionId';

export const DEFAULT_COLUMNS: TransactionColumnKey[] = [
  'changes',
  'createdAt',
  'entityName',
  'errors',
  'operation',
  'sources',
  'syncariId',
  'transactionId',
];

export interface TransactionsTableProps {
  params: TransactionsParams;
  agTableProps?: Partial<AgTableProps>;
  selectedColumns?: TransactionColumnKey[];
}

const TransactionsTable = ({ params, agTableProps, selectedColumns = DEFAULT_COLUMNS }: TransactionsTableProps) => {
  const [{ transactionDetail, ...queryParamsRest }, updateQueryString] = useQueryParams<TransactionsTableQueryParams>();
  const { tn } = useI18nContext();
  const { data: entities, loading: entitiesLoading } = useSyncariEntities();
  const [transactionId, setTransactionId] = useState(transactionDetail);

  const moment = useUserLocalMoment();

  useEffect(() => {
    setTransactionId(transactionDetail);
  }, [transactionDetail]);

  const entitiesMap = useMemo(() => keyBy(entities, 'apiName'), [entities]);

  const components = useMemo(() => {
    return {
      createdAt: withAntRenderer(TransactionDateRenderer),
      changes: withAntRenderer(TransactionChangesRenderer),
      errors: withAntRenderer(TransactionErrorsRenderer),
      operation: withAntRenderer(I18nStringRenderer('Transaction.operations')),
      linkRenderer: (item: EnhancedAgCellRendererParams<[Transaction['id'], string | undefined], Transaction>) => {
        const [id, url] = item.value;
        return <LinkRenderer text={id} url={url} />;
      },
      sourceMap: (item: EnhancedAgCellRendererParams<Transaction['sources'], Transaction>) => {
        const sources = item.value?.map((source, idx) => (
          <>
            <span className="ag-cell-value">
              <span className="source-cell__connector-namee">{source.connectorName}:</span>
              <span className="source-cell__connector-name">{source.externalId}</span>
            </span>
            {item.value.length && idx < item.value.length - 1 && <br />}
          </>
        ));
        return <div className="source-cell">{sources}</div>;
      },
    } as const;
  }, []);

  const columns = useMemo<ColDef[]>(() => {
    const columnDefs = [
      {
        headerName: tn('headers.transaction_id'),
        colId: 'transactionId',
        field: 'id',
      },
      {
        headerName: tn('headers.syncari_id'),
        colId: 'syncariId',
        valueGetter: ({ data: transaction }: { data: Transaction }) => {
          const transactionId = getTransactionId(transaction);
          const entityId = entitiesMap[transaction.entityName]?.id;

          const url =
            transactionId && entityId
              ? makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId: transactionId })
              : undefined;

          return [transactionId, url];
        },
        cellRenderer: 'linkRenderer',
      },
      {
        headerName: tn('headers.date'),
        colId: 'createdAt',
        field: 'createdAt',
        cellRenderer: 'createdAt',
      },
      {
        headerName: tn('headers.entity_name'),
        colId: 'entityName',
        field: 'entityName',
        valueGetter: ({ data: transaction }: { data: Transaction }) => {
          const entity = entitiesMap[transaction.entityName];
          return entity ? `${entity.displayName} (${entity.apiName})` : transaction.entityName;
        },
      },
      {
        headerName: tn('headers.sources'),
        colId: 'sources',
        field: 'sources',
        cellRenderer: 'sourceMap',
        autoHeight: true,
      },
      {
        headerName: tn('headers.operation'),
        colId: 'operation',
        field: 'operation',
        cellRenderer: 'operation',
      },
      {
        headerName: tn('headers.changes'),
        colId: 'changes',
        field: 'changes',
        cellClass: 'transactions-table-synri-changes-column',
        cellRenderer: 'changes',
      },
      {
        headerName: tn('headers.errors'),
        colId: 'errors',
        field: 'errors',
        cellRenderer: 'errors',
      },
    ];

    return columnDefs.filter((col) => selectedColumns.includes(col.colId as TransactionColumnKey));
  }, [entitiesMap, selectedColumns, tn]);

  const onRequestCloseTransactionDetail = useCallback(
    (replaceState = false) => {
      if (queryParamsRest) {
        updateQueryString(queryParamsRest, { encodeToPlus: false, replaceState });
      }
      setTransactionId(undefined);
    },
    [queryParamsRest, updateQueryString]
  );

  const {
    cursor,
    direction,
    pageSize,
    setPageSize,
    onRequestNextPage,
    onRequestPrevPage,
    setFirstPageStartCursor,
  } = useCursorPagination();

  const { queryParams } = usePipelineErrorSystemFilter();

  const isSystemFilterOn = Boolean(queryParams.message);

  const {
    isLoading: transactionByMessageIsLoading,
    isFetching: transactionByMessageIsFetching,
    data: transactionByMessageData,
    error: transactionByMessageError,
  } = useGetTransactionsByMessageQuery(
    { ...queryParams, cursor, direction, count: pageSize },
    { skip: !isSystemFilterOn }
  );

  const {
    isLoading: transactionLoading,
    isFetching: transactionFetching,
    data: transactionData,
    error: transactionError,
  } = useGetTransactionsQuery(
    {
      ...params,
      cursor,
      direction,
      count: pageSize,
    },
    { skip: isSystemFilterOn }
  );

  const data = isSystemFilterOn ? transactionByMessageData : transactionData;

  const isLoading = isSystemFilterOn
    ? transactionByMessageIsLoading || transactionByMessageIsFetching
    : transactionLoading || transactionFetching;

  const rowData = data?.records || [];

  const showTransactionDetail = !!transactionId
    ? rowData.find((t) => t.syncariId === transactionId || t.id === transactionId)
    : undefined;

  useEffect(() => {
    if (transactionId && !showTransactionDetail) {
      onRequestCloseTransactionDetail(true);
    }
  }, [onRequestCloseTransactionDetail, showTransactionDetail, transactionId]);

  useEffect(() => {
    // track our "first page" by saving the first
    // "start" cursor that we get on the response
    // this should be the first record of a our first
    // page in the result set
    setFirstPageStartCursor(data?.pageInfo);
  }, [setFirstPageStartCursor, data?.pageInfo]);

  // Use the cursor (alphanumeric id) for mongo (a week or less filter time
  // range) or page id (number) for BQ if start date is great than 1 week ago
  const shouldUseBigQueryCursor = useMemo(() => {
    // We don't have a start date for Lineage. We should use Big Query for Lineage
    if (!params.startDate) {
      return true;
    }
    return Boolean(moment().diff(moment(params.startDate), 'days') >= 7);
  }, [moment, params.startDate]);

  if (entitiesLoading) {
    return <RouteSpin />;
  }

  const pageInfo = data?.pageInfo
    ? {
        ...data.pageInfo,
        // NOTE: This may be unusable and unreliable once we introduce client configurable sorting
        hasPrevious: data.pageInfo.hasPrevious,
      }
    : undefined;

  return (
    <>
      <TransactionContextProvider>
        <AgTable
          columnDefs={columns}
          getRowHeight={(params: { data: Transaction }) => {
            const rowCount = params?.data?.sources?.length || 1;
            const defaultRowHeight = 28;
            return rowCount * defaultRowHeight;
          }}
          frameworkComponents={components}
          loading={isLoading}
          rowData={rowData}
          error={getRtkQueryErrorMessage(isSystemFilterOn ? transactionByMessageError : transactionError)}
          getRowNodeId={(transaction) => [transaction.syncariId, transaction.createdAt].join(':')}
          suppressColumnVirtualisation
          sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
          enableCellTextSelection
          suppressCellSelection
          suppressRowClickSelection
          pagerComponent={
            <CursorBasedPagination
              pageInfo={pageInfo}
              allowPageSizeChange
              pageSize={pageSize}
              isLoading={isLoading}
              onPageSizeChange={setPageSize}
              onRequestNextPage={(cur: string, count?: number) => {
                const nextPageNumber = String((pageInfo?.pageNumber || 0) + 1);
                onRequestNextPage(shouldUseBigQueryCursor ? nextPageNumber : cur, count);
              }}
              onRequestPreviousPage={(cur: string, count?: number) => {
                const previousPageNumber = String((pageInfo?.pageNumber || 0) - 1);
                onRequestPrevPage(shouldUseBigQueryCursor ? previousPageNumber : cur, count);
              }}
            />
          }
          {...agTableProps}
        />

        <TransactionDetailsPanel onRequestClose={onRequestCloseTransactionDetail} transaction={showTransactionDetail} />
      </TransactionContextProvider>
    </>
  );
};

export default TransactionsTable;
