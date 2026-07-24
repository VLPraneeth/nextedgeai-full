import { navigate, RouteComponentProps } from '@reach/router';
import { ColDef, ColGroupDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Button, message } from 'antd';
import cx from 'classnames';
import { useCallback, useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Can from 'components/Can';
import InlineMessage from 'components/InlineMessage';
import { HStack } from 'components/layout';
import Modal from 'components/Modal';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import SearchBox from 'components/SearchBox';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useDeleteDatasetsMutation, useGetDatasetsQuery } from 'store/insights-studio';
import { Dataset } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useUnifiedDataCardAuthoringContext } from '../context/UnifiedDataCardAuthoringContext';
import { useUnifiedDataCardNavigate } from '../utils/useUnifiedDataCardNavigate';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';

const tn = tNamespaced('InsightsStudio');

export function DatasetList(props: RouteComponentProps) {
  const { data: datasets, isFetching } = useGetDatasetsQuery(true);
  const [filterString, setFilterString] = useState('');
  const { navigateTo, getCurrentDashboard } = useUnifiedDataCardNavigate();
  const [selectedDatasets, setSelectedDatasets] = useState<Dataset[]>([]);
  const [gridApi, setGridApi] = useState<GridApi>();
  const selectedDataset = useMemo(() => selectedDatasets[0], [selectedDatasets]);
  const sortedDatasets = useMemo(
    () => datasets?.filter((ds) => !ds.seeded && ds.displayName.toLowerCase().includes(filterString.toLowerCase())),
    [datasets, filterString]
  );
  const utcToLocal = useUtcTimeInUsersTimezone();
  const [deleteDataset] = useDeleteDatasetsMutation();
  const { setUnifiedMode } = useUnifiedDataCardAuthoringContext();

  const onGridReady = useCallback((params: GridReadyEvent) => {
    setGridApi(params.api);
  }, []);

  const openCreate = useCallback(() => navigateTo('THOUGHT_SPOT_DATASET', 'new'), [navigateTo]);

  const onRowSelected = useCallback(() => {
    gridApi && setSelectedDatasets(gridApi?.getSelectedRows() || []);
  }, [gridApi]);

  const handleDelete = useCallback(() => {
    deleteDataset(selectedDatasets.map((ds) => ds.id))
      .unwrap()
      .then(() => {
        message.success(tn('data_set_deleted'));
      })
      .catch((err) => {
        message.error(getRtkQueryErrorMessage(err, tc('generic_error')));
      });
  }, [selectedDatasets, deleteDataset]);

  const confirmDelete = useCallback(() => {
    Modal.confirm({
      className: 'authoring-sidebar-list__delete-modal',
      title: tn('data_set_delete'),
      content: (
        <>
          <InlineMessage initallyExpanded type="warning">
            <span>
              {selectedDataset.displayName} ({selectedDataset.name})
              {selectedDatasets.length > 1 && <> and {selectedDatasets.length - 1} more</>}
            </span>
          </InlineMessage>
          {tn('delete_confirm')}
        </>
      ),
      onOk: handleDelete,
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
  }, [selectedDataset, selectedDatasets, handleDelete]);

  const openEdit = useCallback(() => {
    setUnifiedMode('DATASET_ONLY');
    navigateTo('THOUGHT_SPOT_DATASET', selectedDataset.id);
  }, [selectedDataset, navigateTo, setUnifiedMode]);

  const openDuplicate = useCallback(() => {
    const { dashboardId } = getCurrentDashboard();
    navigate(
      makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASET_COPY, {
        dashboardId,
        datasetId: selectedDataset.id,
      })
    );
  }, [getCurrentDashboard, selectedDataset]);

  const openPreview = useCallback(() => {
    const { dashboardId } = getCurrentDashboard();
    navigate(
      makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASET_PREVIEW, {
        dashboardId,
        datasetId: selectedDataset.id,
      })
    );
  }, [getCurrentDashboard, selectedDataset]);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: '',
        field: 'select',
        minWidth: 48,
        maxWidth: 48,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        suppressMovable: true,
      },
      {
        headerName: tc('name'),
        field: 'displayName',
        resizable: true,
      },

      {
        headerName: tc('created_by'),
        field: 'createdBy',
        resizable: true,
      },
      {
        headerName: tc('created_at'),
        field: 'createdAt',
        resizable: true,
        cellRendererFramework: ({ data }: { data: Dataset }) => {
          return <span className="ag-cell-value">{data?.createdAt ? utcToLocal(data.createdAt) : '-'}</span>;
        },
      },

      {
        headerName: tc('last_modified_by'),
        field: 'updatedBy',
        resizable: true,
      },
      {
        headerName: tc('last_modified_date'),
        field: 'updatedAt',
        resizable: true,
        cellRendererFramework: ({ data }: { data: Dataset }) => {
          return <span className="ag-cell-value">{data?.updatedAt ? utcToLocal(data.updatedAt) : '-'}</span>;
        },
      },
    ];
  }, [utcToLocal]);

  return (
    <div className="dataset-list__container">
      <div className="dataset-list__top-actions">
        {!selectedDatasets.length ? (
          <SearchBox
            onChange={(event) => setFilterString(event.target.value)}
            value={filterString}
            placeholder={tc('search')}
            className="dataset-list__search"
          />
        ) : (
          <HStack spacing="sm">
            {selectedDatasets.length === 1 && (
              <HStack spacing="sm">
                <Button type="primary" onClick={openPreview}>
                  {tc('preview')}
                </Button>
                <Can permission={AllPermissions.UPDATE_DATASET}>
                  <Button onClick={openEdit}>{tc('edit')}</Button>
                </Can>
                <Can permission={AllPermissions.UPDATE_DATASET}>
                  <Button onClick={openDuplicate}>{tc('make_copy')}</Button>
                </Can>
              </HStack>
            )}

            <Can permission={AllPermissions.DELETE_DATASET}>
              <Button type="danger" className="dataset-list__delete" onClick={confirmDelete}>
                {tc('delete')}
              </Button>
            </Can>
          </HStack>
        )}

        {!selectedDatasets.length && (
          <div className="dataset-list__top-buttons">
            <Can permission={AllPermissions.CREATE_DATASET}>
              <Button onClick={openCreate} type="primary" icon="plus">
                {tc('new')}
              </Button>
            </Can>
          </div>
        )}
      </div>
      <ScrollableArea className="dataset-list__table-container">
        <AgTable
          className={cx('dataset-list__table', !sortedDatasets?.length && 'empty')}
          onGridReady={onGridReady}
          onRowSelected={onRowSelected}
          columnDefs={columns}
          loading={isFetching}
          rowData={sortedDatasets}
          rowSelection="multiple"
          suppressCellSelection
          suppressRowClickSelection
          singleClickEdit
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
          getRowNodeId={(data) => data.id}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
      </ScrollableArea>
    </div>
  );
}
