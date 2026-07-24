//
// Copyright (c) 2021 Syncari All rights reserved.
//

import { CellClassParams, ColDef } from 'ag-grid-community';
import cx from 'classnames';
import { Moment } from 'moment-timezone';
import { useEffect, useMemo } from 'react';

import AgTable, { CursorBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import { JsonRendererPopover } from 'components/JsonRendererPopover';
import { useCursorPagination } from 'hooks/pagination';
import { useGetWebhookLogsMutation } from 'store/custom-synapse/webhook/api';
import { PipelineLog } from 'store/pipeline/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

export interface LogsFilterValues {
  syncariRecordId?: string;
  startDate: Moment;
  endDate: Moment;
}

export interface PipelineLogRecord {
  id: PipelineLog['id'];
  timestamp: PipelineLog['occurredTime'];
  syncariRecordId: PipelineLog['syncariRecordId'];
  externalEntity?: PipelineLog['externalEntity'];
  externalRecord: PipelineLog['externalRecordIds'];
  syncCycle: PipelineLog['batchId'];
  nodeName: PipelineLog['nodeName'];
  nodeId: PipelineLog['nodeId'];
  input: PipelineLog['input'];
  output: PipelineLog['output'];
  pipelineName: PipelineLog['pipelineName'];
  pipelineType: PipelineLog['scope'];
  errorMessage: PipelineLog['error'];
  errorDetails: PipelineLog['errorDetails'];
  batchMode: PipelineLog['batchMode'];
  runMode: PipelineLog['runMode'];
  syncariAttributeId: PipelineLog['syncariAttributeId'];
  timeTakenInMillis: PipelineLog['timeTakenInMillis'];
}

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

export const WebhookLogsTable = ({ connectorId }: { connectorId?: string }) => {
  const columns = useMemo<ColDef[]>(() => {
    const columnDefs = [
      {
        headerName: tn('received_on'),
        colId: 'receivedOn',
        field: 'receivedOn',
        sort: 'desc',
        sortable: true,
        sortingOrder: ['asc', 'desc'],
      },
      {
        headerName: tc('payload'),
        colId: 'payload',
        field: 'payload',
        cellRendererFramework: ({ value }: CellClassParams) => {
          return <JsonRendererPopover jsonString={value} />;
        },
      },
      {
        headerName: tc('headers'),
        colId: 'headers',
        field: 'headers',
        cellRendererFramework: ({ value }: CellClassParams) => {
          return <JsonRendererPopover jsonString={value} format="table" />;
        },
      },
      {
        headerName: tn('signature_verified'),
        colId: 'verified',
        field: 'verified',
      },
      {
        headerName: tn('authenticated'),
        colId: 'authenticated',
        field: 'authenticated',
      },
    ];

    return columnDefs;
  }, []);

  const [getLogs, { data, isLoading, error }] = useGetWebhookLogsMutation();

  const { cursor, direction, pageSize, setPageSize, onRequestNextPage, onRequestPrevPage } = useCursorPagination();

  useEffect(() => {
    if (connectorId) {
      getLogs({
        cursor: cursor ?? '0',
        direction,
        count: String(pageSize),
        connectorId,
      });
    }
  }, [cursor, direction, connectorId, getLogs, pageSize]);

  const pageInfo = useMemo(() => {
    return {
      start: null,
      end: null,
      hasMore: true,
      hasPrevious: false,
      pageNumber: 0,
      ...(data?.pageInfo || {}),
    };
  }, [data?.pageInfo]);

  return (
    <AgTable
      className={cx('custom-synapse__table', !data?.records?.length && 'empty')}
      domLayout="autoHeight"
      columnDefs={columns}
      loading={isLoading}
      rowData={data?.records}
      getRowNodeId={(record) => record.id}
      sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
      enableCellTextSelection
      suppressCellSelection
      suppressRowClickSelection
      noRowsOverlayComponentProps={{
        description: tc('no_records_found'),
      }}
      error={getRtkQueryErrorMessage(error)}
      pagerComponent={
        <CursorBasedPagination
          pageInfo={pageInfo}
          allowPageSizeChange
          pageSize={pageSize}
          isLoading={isLoading}
          onPageSizeChange={setPageSize}
          onRequestNextPage={(cur: string, count?: number) => {
            onRequestNextPage(String((pageInfo?.pageNumber || 0) + 1), count);
          }}
          onRequestPreviousPage={(cur: string, count?: number) => {
            onRequestPrevPage(String((pageInfo?.pageNumber || 0) - 1), count);
          }}
        />
      }
    />
  );
};
