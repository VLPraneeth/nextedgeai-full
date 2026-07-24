//
// Copyright (c) 2021 Syncari All rights reserved.
//

import { ColDef } from 'ag-grid-community';
import { useEffect, useMemo, useState } from 'react';

import AgTable, { PageBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import useClientPagination from 'hooks/useClientPagination';
import { TimeCell } from 'pages/sync-studio/entity/PipelineDetailsTable/PipelineDetailsTable.renderers';
import { useGetRulesListQuery } from 'store/data-quality-v2/api';
import { PipelineLog } from 'store/pipeline/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { DataQualityFilterValues } from '../DataQuality';
import { useDataQuality } from '../DataQuality.hooks';
import './RulesTable.scss';
import { Category, ConditionRenderer, Policy, RulesActions, Scope } from './RulesTable.renderer';

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

const tn = tNamespaced('DataQuality');

export const RulesTable = ({ entityId, filter }: { entityId: string; filter: Partial<DataQualityFilterValues> }) => {
  const { entityId: syncariEntityId, graphVersion } = useDataQuality();
  const columns = useMemo<ColDef[]>(() => {
    const columnDefs = [
      {
        headerName: tc('name'),
        colId: 'name',
        field: 'name',
      },
      {
        headerName: tc('created_by'),
        colId: 'createdBy',
        field: 'createdBy',
      },
      {
        headerName: tn('scope'),
        colId: 'scope',
        field: 'scope',
        cellRenderer: 'scope',
      },
      {
        headerName: tn('category'),
        colId: 'category',
        field: 'category',
        cellRenderer: 'category',
      },
      {
        headerName: tn('policy'),
        colId: 'policy',
        field: 'policy',
        width: 70,
        cellRenderer: 'policy',
      },
      {
        headerName: tn('condition'),
        colId: 'condition',
        field: 'condition',
        cellRenderer: 'condition',
      },
      {
        headerName: tn('passed'),
        colId: 'passed',
        field: 'passed',

        width: 70,
      },
      {
        headerName: tn('failed'),
        colId: 'failed',
        field: 'failed',
        width: 70,
      },
      {
        headerName: tn('total'),
        colId: 'total',
        field: 'total',
        width: 70,
      },
      {
        headerName: tc('last_changed'),
        colId: 'lastModifiedDate',
        field: 'lastModifiedDate',
        cellRenderer: 'time',
      },
      {
        headerName: tc('actions'),
        field: 'actions',
        minWidth: 70,
        maxWidth: 70,
        cellRenderer: 'actions',
        pinned: 'right',
        width: 70,
        resizable: false,
      },
    ];

    return columnDefs;
  }, []);

  const { data, error, isLoading, isFetching, refetch } = useGetRulesListQuery(
    { syncariEntityId, version: graphVersion },
    { skip: !Boolean(syncariEntityId) || !Boolean(graphVersion) }
  );

  useEffect(() => {
    if (syncariEntityId) {
      refetch();
    }
  }, [refetch, syncariEntityId]);

  const rowData = useMemo(() => {
    return data
      ?.filter((record) => !Boolean(filter.name) || (filter.name && record.name.includes(filter.name)))
      .map((record) => {
        return {
          id: record.id,
          name: record.name,
          scope: record.scope,
          category: record.category,
          policy: record.policy,
          condition: record.ruleConfig,
          passed: record.passed,
          failed: record.failed,
          total: record.total,
          lastModifiedDate: record.updatedAt,
          createdBy: record.createdBy || record.updatedBy || '-',
        };
      });
  }, [data, filter.name]);

  const [pageSize, setPageSize] = useState(25);
  const [{ pageInfo, records }, { getNextPage, getPreviousPage, goToPage }] = useClientPagination(
    rowData || [],
    pageSize
  );

  useEffect(() => {
    goToPage(0);
  }, [goToPage]);

  return (
    <AgTable
      className="data-quality-rules-table"
      columnDefs={columns}
      frameworkComponents={catFrameworkComponents}
      loading={isLoading || isFetching}
      rowData={records}
      getRowNodeId={(record) => record.id}
      suppressColumnVirtualisation
      sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
      enableCellTextSelection
      suppressCellSelection
      suppressRowClickSelection
      noRowsOverlayComponentProps={{
        description: tn('empty_rules'),
      }}
      error={getRtkQueryErrorMessage(error)}
      pagerComponent={
        <PageBasedPagination
          pageInfo={pageInfo}
          allowPageSizeChange
          pageSize={pageSize}
          onPageSizeChange={setPageSize}
          onRequestNextPage={getNextPage}
          onRequestPreviousPage={getPreviousPage}
        />
      }
    />
  );
};

export const catFrameworkComponents = {
  actions: RulesActions,
  time: TimeCell,
  category: Category,
  policy: Policy,
  scope: Scope,
  condition: ConditionRenderer,
};
