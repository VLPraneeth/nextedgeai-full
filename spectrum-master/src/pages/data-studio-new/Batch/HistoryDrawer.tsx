import { ColDef } from 'ag-grid-community';
import { useCallback, useEffect, useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { LeftValue } from 'components/inputs/types';
import { withAntRenderer, defaultAgFrameworkComponents } from 'components/renderers';
import makeFilterCellRenderer from 'components/renderers/FilterRenderer';
import { useLazyGetBatchesForEntityQuery } from 'store/data-studio-batch';
import { BatchOperation } from 'store/data-studio-batch/types';

import BatchStatusRenderer from './BatchStatusRenderer';
const DRAWER_WIDTH = 1000;
const drawerStyle = { display: 'flex', height: '100%' };

export enum BatchHistoryDrawerMode {
  CLOSED = 'closed',
  DELETE = 'delete',
  UPDATE = 'update',
}

export type BatchHistoryDrawerProps = {
  entityId: string;
  fieldValues: LeftValue[];
  mode?: BatchHistoryDrawerMode;
  onRequestClose: () => void;
};

const BatchHistoryDrawer = ({
  entityId,
  fieldValues,
  mode = BatchHistoryDrawerMode.CLOSED,
  onRequestClose,
}: BatchHistoryDrawerProps) => {
  const { tn } = useI18nContext();
  const [isOpen, setIsOpen] = useState(() => mode !== BatchHistoryDrawerMode.CLOSED);
  const closeDrawer = useCallback(() => setIsOpen(false), []);

  const [getBatches, { data, isLoading, isFetching }] = useLazyGetBatchesForEntityQuery();

  useEffect(() => {
    setIsOpen(mode !== BatchHistoryDrawerMode.CLOSED);
  }, [mode]);

  useEffect(() => {
    if (mode === BatchHistoryDrawerMode.DELETE) {
      getBatches({ entityId, operation: BatchOperation.DELETE });
    } else if (mode === BatchHistoryDrawerMode.UPDATE) {
      getBatches({ entityId, operation: BatchOperation.UPDATE });
    }
  }, [entityId, getBatches, mode]);

  const components = useMemo(
    () => ({
      ...defaultAgFrameworkComponents,
      filter: withAntRenderer(makeFilterCellRenderer(fieldValues)),
      status: BatchStatusRenderer,
    }),
    [fieldValues]
  );

  const columns: ColDef[] = useMemo(
    () => [
      {
        headerName: tn('headers.lastUpdated'),
        colId: 'lastUpdatedAt',
        field: 'lastUpdatedAt',
        cellRenderer: 'datetime',
      },
      {
        headerName: tn('headers.initiatedBy'),
        colId: 'initiatedByUser',
        field: 'initiatedByUser',
      },
      {
        headerName: tn('headers.filters'),
        colId: 'filter',
        field: 'filter',
        cellRenderer: 'filter',
        flex: 1,
      },
      {
        headerName: tn(mode === BatchHistoryDrawerMode.DELETE ? 'headers.records_deleted' : 'headers.records_updated'),
        colId: 'recordsProcessed',
        field: 'recordsProcessed',
      },
      {
        headerName: tn('headers.status'),
        colId: 'status',
        field: 'status',
        cellRenderer: 'status',
      },
    ],
    [mode, tn]
  );

  const handleVisibleChange = (visible: boolean) => {
    // when the panel closes, then fire our close event
    if (!visible) {
      onRequestClose();
    }
  };

  return (
    <DrawerPanel
      absolutePositioning
      destroyOnClose
      onClose={closeDrawer}
      afterVisibleChange={handleVisibleChange}
      title={tn(mode === BatchHistoryDrawerMode.DELETE ? 'past_record_deletions_title' : 'past_record_updates_title')}
      mask
      visible={isOpen}
      width={DRAWER_WIDTH}
      className="data-studio-history-drawer"
      bodyStyle={drawerStyle}>
      <AgTable
        key={mode}
        frameworkComponents={components}
        columnDefs={columns}
        rowData={data}
        loading={isLoading || isFetching}
        sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
      />
    </DrawerPanel>
  );
};

export default withI18n(BatchHistoryDrawer, 'DataStudio.BatchHistory');
