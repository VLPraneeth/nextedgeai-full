import { ColDef } from 'ag-grid-community';

import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('PipelineDetailsTable');

export const usePipelineDetailsTableColumns = () => {
  const pipelineDetailsTableColumns: ColDef[] = [
    {
      headerName: tn('name'),
      field: 'name',
      cellRenderer: 'name',
      width: 140,
    },
    {
      headerName: tn('api_name'),
      field: 'apiName',
      pinned: 'left',
      cellRenderer: 'placeholder',
      width: 140,
    },
    {
      headerName: tn('pipeline_status'),
      field: 'pipelineStatusTagData',
      cellRenderer: 'tag',
      width: 140,
    },
    {
      headerName: tn('pipeline_state'),
      field: 'pipelineStateTagData',
      cellRenderer: 'pipelineStatus',
      width: 140,
    },
    {
      headerName: tn('current_activity'),
      field: 'currentActivity',
      cellRenderer: 'activity',
    },
    {
      headerName: tn('last_completed_sync'),
      field: 'lastCompletedSync',
      cellRenderer: 'time',
    },
    {
      headerName: tn('transactions_in_last_cycle'),
      field: 'transactionsInLastCycle',
      cellRenderer: 'transactions',
    },
    {
      headerName: tn('last_cycle'),
      field: 'lastCycleDuration',
      cellRenderer: 'cycle',
    },
    {
      headerName: tn('sources'),
      field: 'sources',
      cellRenderer: 'sinkSource',
      width: 400,
    },
    {
      headerName: tn('destinations'),
      field: 'destinations',
      cellRenderer: 'sinkSource',
      width: 400,
    },
    {
      headerName: tn('number_of_fields_mapped'),
      field: 'fieldsMapped',
      cellRenderer: 'placeholder',
    },
    {
      headerName: tn('number_of_versions'),
      field: 'numberOfVersions',
      cellRenderer: 'versions',
    },
    {
      headerName: tn('merge_config'),
      field: 'mergeConfig',
      cellRenderer: 'merge',
    },
    {
      headerName: tn('last_modified_on'),
      field: 'lastModifiedOn',
      cellRenderer: 'time',
    },
    {
      headerName: tn('last_published'),
      field: 'lastPublishedOn',
      cellRenderer: 'time',
    },
    {
      headerName: tn('last_published_by'),
      field: 'lastModifiedBy',
      cellRenderer: 'user',
    },
    {
      headerName: tn('actions'),
      field: 'actions',
      cellRenderer: 'pipelineActions',
      pinned: 'right',
      width: 70,
      resizable: false,
    },
  ];

  return { pipelineDetailsTableColumns };
};
