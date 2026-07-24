//
// Copyright (c) 2021 Syncari All rights reserved.
//

import { useMatch } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { Moment } from 'moment-timezone';
import { useCallback, useEffect, useMemo } from 'react';

import AgTable, { CursorBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import { EnhancedAgCellRendererParams, TruncatedTextCopyCell } from 'components/renderers';
import DateCellRenderer from 'components/renderers/DateCellRenderer';
import { LinkRenderer } from 'components/renderers/LinkRenderer';
import { useCursorPagination } from 'hooks/pagination';
import { useGetPipelineLogsMutation } from 'store/pipeline/api';
import { PipelineLog } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { FULL_DATE_TIME } from 'utils/DateUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { PipelineSettings, usePipelineSettings } from '../settings/Settings.hooks';
import { InputOutpuRenderer } from './PipelineLogs.renderers';

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

const tn = tNamespaced('PipelineLogs');

export const PipelineLogsTable = ({ entityId, filter }: { entityId: string; filter: LogsFilterValues }) => {
  const versionMatch = useMatch('/sync-studio/entity/:entityId/pipeline-logs/:graphVersion/*');

  const { isSettingsEnabled } = usePipelineSettings();

  const getPipelineUrl = useCallback(
    (pipelineLog: PipelineLogRecord, entityId?: string, params?: Record<string, string>) => {
      return pipelineLog && entityId
        ? makeUrl(
            pipelineLog.pipelineType === AppConstants.SCOPE.ENTITY
              ? RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION
              : RouteConstants.FIELD_PIPELINE_GRAPH_VERSION,
            {
              entityId,
              fieldId: pipelineLog.syncariAttributeId,
              graphVersion: versionMatch?.graphVersion,
            },
            params
          )
        : '';
    },
    [versionMatch?.graphVersion]
  );

  const columns = useMemo<ColDef[]>(() => {
    const columnDefs = [
      {
        headerName: tn('timestamp'),
        colId: 'timestamp',
        field: 'timestamp',
        cellRenderer: 'userDate',
        valueGetter: ({ data: pipelineLog }: { data: PipelineLogRecord }) => pipelineLog?.timestamp,
      },
      {
        headerName: tn('syncari_record'),
        colId: 'syncariRecordId',
        field: 'syncariRecordId',
        cellRenderer: 'linkRenderer',
        valueGetter: ({ data: pipelineLog }: { data: PipelineLogRecord }) => {
          const entityId = versionMatch?.entityId;
          const url = pipelineLog
            ? makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, {
                entityId,
                recordId: pipelineLog.syncariRecordId,
              })
            : '';
          return [pipelineLog.syncariRecordId, url];
        },
      },
      {
        headerName: tn('external_entity'),
        colId: 'externalEntity',
        field: 'externalEntity',
      },
      {
        headerName: tn('external_record'),
        colId: 'externalRecord',
        field: 'externalRecord',
      },
      {
        headerName: tn('sync_cycle'),
        colId: 'syncCycle',
        field: 'syncCycle',
      },
      {
        headerName: tn('node_name'),
        colId: 'nodeName',
        field: 'nodeName',
        cellRenderer: 'linkRenderer',
        valueGetter: ({ data: pipelineLog }: { data: PipelineLogRecord }) => {
          return [
            pipelineLog.nodeName,
            getPipelineUrl(pipelineLog, versionMatch?.entityId, {
              nodeIds: pipelineLog.nodeId,
            }),
          ];
        },
      },
      {
        headerName: tn('input'),
        colId: 'input',
        field: 'input',
        minWidth: 100,
        cellRendererFramework: InputOutpuRenderer,
      },
      {
        headerName: tn('output'),
        colId: 'output',
        field: 'output',
        minWidth: 100,
        cellRendererFramework: InputOutpuRenderer,
      },
      {
        headerName: tn('error'),
        colId: 'errorMessage',
        field: 'errorMessage',
      },
      {
        headerName: tn('errorDetails'),
        colId: 'errorDetails',
        field: 'errorDetails',
        cellRenderer: 'truncatedTextCopy',
        valueGetter: ({ data: pipelineLog }: { data: PipelineLogRecord }) => {
          return pipelineLog['errorDetails'];
        },
      },
      {
        headerName: tn('batch_mode'),
        colId: 'batchMode',
        field: 'batchMode',
      },
      {
        headerName: tn('run_mode'),
        colId: 'runMode',
        field: 'runMode',
      },
      {
        headerName: tn('time_taken_in_millisecs'),
        colId: 'timeTakenInMillis',
        field: 'timeTakenInMillis',
      },

      {
        headerName: tn('pipeline_name'),
        colId: 'pipelineName',
        field: 'pipelineName',
        cellRenderer: 'linkRenderer',
        valueGetter: ({ data: pipelineLog }: { data: PipelineLogRecord }) => {
          return [pipelineLog.pipelineName, getPipelineUrl(pipelineLog, versionMatch?.entityId)];
        },
      },
      {
        headerName: tn('pipeline_type'),
        colId: 'pipelineType',
        field: 'pipelineType',
      },
    ];

    return columnDefs;
  }, [getPipelineUrl, versionMatch?.entityId]);

  const [getPipelineLogs, { data, isLoading, error }] = useGetPipelineLogsMutation();

  const rowData = useMemo(() => {
    return data?.records?.map<PipelineLogRecord>((record) => {
      return {
        id: record.id,
        timestamp: record.occurredTime,
        syncariRecordId: record.syncariRecordId,
        externalEntity: record.externalEntity,
        externalRecord: record.externalRecordIds,
        syncCycle: record.batchId,
        nodeName: record.nodeName,
        nodeId: record.nodeId,
        input: record.input,
        output: record.output,
        pipelineName: record.pipelineName,
        pipelineType: record.scope,
        errorMessage: record.error,
        errorDetails: record.errorDetails,
        batchMode: record.batchMode,
        runMode: record.runMode,
        syncariAttributeId: record.syncariAttributeId,
        timeTakenInMillis: record.timeTakenInMillis,
      };
    });
  }, [data?.records]);

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

  const { cursor, direction, pageSize, setPageSize, onRequestNextPage, onRequestPrevPage } = useCursorPagination();

  useEffect(() => {
    const graphVersion = versionMatch?.graphVersion || 'draft';

    getPipelineLogs({
      cursor: cursor ?? '0',
      direction,
      count: String(pageSize),
      start: filter.startDate.utc().format(FULL_DATE_TIME),
      end: filter.endDate.utc().format(FULL_DATE_TIME),
      syncariRecordId: filter.syncariRecordId,
      syncariEntityId: entityId,
      status: graphVersion,
    });
  }, [
    cursor,
    direction,
    entityId,
    filter.endDate,
    filter.startDate,
    filter.syncariRecordId,
    getPipelineLogs,
    pageSize,
    versionMatch?.graphVersion,
  ]);

  const components = useMemo(() => {
    return {
      userDate: (item: EnhancedAgCellRendererParams<Moment, PipelineLogRecord>) => {
        return !item?.value ? '' : DateCellRenderer(item.value);
      },
      linkRenderer: (item: EnhancedAgCellRendererParams<[string, string], PipelineLogRecord>) => {
        const [text, url] = item.value;
        return <LinkRenderer text={text} url={url} />;
      },
      truncatedTextCopy: (item: EnhancedAgCellRendererParams<string, PipelineLogRecord>) => {
        return (
          <TruncatedTextCopyCell textToCopy={item.value}>
            <span>{item.value}</span>
          </TruncatedTextCopyCell>
        );
      },
    } as const;
  }, []);

  return (
    <AgTable
      columnDefs={columns}
      frameworkComponents={components}
      loading={isLoading}
      rowData={rowData}
      getRowNodeId={(record) => record.id}
      suppressColumnVirtualisation
      sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
      enableCellTextSelection
      suppressCellSelection
      suppressRowClickSelection
      noRowsOverlayComponentProps={{
        description: isSettingsEnabled(PipelineSettings.nodeLoggingEnabled) ? tc('no_data') : tn('empty_logs'),
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
