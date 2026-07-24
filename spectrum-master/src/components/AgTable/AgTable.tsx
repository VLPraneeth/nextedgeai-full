import {
  CellClassParams,
  CellFocusedEvent,
  ColDef,
  ColumnApi,
  ColumnMovedEvent,
  ColumnPinnedEvent,
  ColumnVisibleEvent,
  FirstDataRenderedEvent,
  GridApi,
  GridOptions,
  GridOptionsWrapper,
  GridReadyEvent,
} from 'ag-grid-community';
import { AgGridReact, AgGridReactProps } from 'ag-grid-react';
import Spin from 'antd/lib/spin';
import cx from 'classnames';
import * as React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useDebouncedCallback } from 'use-debounce';

import { HStack } from 'components/layout';
import useDimensions from 'hooks/useDimensions';
import { validateMutuallyExclusiveProps } from 'utils/ComponentUtils';
import { tc } from 'utils/i18nUtil';
import { UnreachableCaseError } from 'utils/TypeUtils';

import TableError, { TableErrorProps } from './Error';
import NoRowsOverlay from './NoRowsOverlay';

import 'ag-grid-community/dist/styles/ag-grid.css';
import 'ag-grid-community/dist/styles/ag-theme-balham.css';
import './AgTable.less';

// Delay the column resize for cases when multiple data changes
// happening triggering it
const AUTOMATIC_COLUMN_RESIZE_DEBOUNCE = 100;
/*
 * Enhanced Types to expose some private fields as a workaround for suppressing row click handling in action cells
 */
type EnhancedGridOptionsWrapper = Omit<GridOptionsWrapper, 'gridOptions'> & {
  gridOptions: GridOptions;
};

type EnhancedGridApi = Omit<GridApi, 'gridOptionsWrapper'> & {
  gridOptionsWrapper: EnhancedGridOptionsWrapper;
};

/* this will size each column to the width of it's content */
const handleAutoSizeColumns = (columns: AgTableProps['autoSizeColumns'], api: ColumnApi) => {
  if (Array.isArray(columns) && columns.length > 0) {
    api.autoSizeColumns(columns);
  } else if (columns === true) {
    api.autoSizeAllColumns(false);
  }
};

/* this will expand all columns to fill the available width of the grid, if possible. If the columns are wider
 * than the grid, it's a no-op
 */
const forceFillGridWidth = (gridApi: GridApi, columnApi: ColumnApi) => {
  const columnsAreNarrowerThanGrid = () => {
    // this assumes that we haven't scrolled horizontally yet
    // and that left would be 0
    const { right: gridWidth } = gridApi.getHorizontalPixelRange();
    const columnsWidth = columnApi.getAllDisplayedVirtualColumns().reduce((acc, col) => acc + col.getActualWidth(), 0);

    return columnsWidth < gridWidth;
  };
  if (!columnsAreNarrowerThanGrid()) {
    // resize all items to the content so we can see if the columns will fit or are narrower
    columnApi.autoSizeAllColumns(false);
  }

  requestAnimationFrame(() => {
    // Check if the grid still exists before resizing
    if (gridApi?.getHorizontalPixelRange()) {
      if (columnsAreNarrowerThanGrid()) {
        // nested animation frame request to wait for the grid
        gridApi.sizeColumnsToFit();
      } else {
        columnApi.autoSizeAllColumns(false);
      }
    }
  });
};

export const DefaultColDef: ColDef = {
  resizable: true,
  lockPinned: true,
  suppressMovable: true,
};

export type CombinedColumnEvent = ColumnMovedEvent | ColumnPinnedEvent | ColumnVisibleEvent;

export enum ResizeColumnsCondition {
  ALWAYS = 'always',
  // When column width is narrower than grid, expand to fill horizontally
  // There is an issue on columns that has maxWidth and maxHeight with
  // the api call getActualWidth. Its not returning the correct value. Use
  // 'always' for those cases and only with fewer columns (< 10) to avoid perf issue.
  WHEN_NARROWER = 'narrower',
}

const NO_ROWS_OVERLAY_COMPONENT_NAME = 'syncariNoRowsOverlayComponent';

export interface AgTableItemCellParams<TData, TValue = any> extends Omit<CellClassParams, 'data' | 'value'> {
  data: TData;
  value: TValue;
}

export interface AgTableProps extends Omit<AgGridReactProps, 'loadingOverlayComponent' | 'noRowsOverlayComponent'> {
  /* if true, all columns will be auto-sized, if an array of column names is provided only those will be auto-sized */
  autoSizeColumns?: true | string[];
  className?: string;
  columnEventsSettledDelay?: number;
  disableRowSelectionForCells?: string[];
  error?: TableErrorProps['error'];
  errorComponentProps?: TableErrorProps;
  footerAccessory?: React.ReactNode | React.ReactElement;
  loading?: boolean;
  loadingText?: string;
  noRowsOverlayComponent?: React.ReactNode;
  noRowsOverlayComponentProps?: AgGridReactProps['noRowsOverlayComponentParams'];
  onColumnEventsSettled?: (events: CombinedColumnEvent[], columnApi: ColumnApi | null) => void;
  onGridReady?: (event: GridReadyEvent) => void;
  pagerComponent?: React.ReactNode | React.ReactElement;
  sizeColumnsToFit?: ResizeColumnsCondition;
  themeName?: 'ag-theme-balham' | 'ag-theme-alpine' | 'ag-theme-alpine-dark' | 'ag-theme-material';
  style?: React.CSSProperties;
}

const AgTable = ({
  applyColumnDefOrder = true,
  autoSizeColumns,
  className,
  columnDefs,
  columnEventsSettledDelay = 1000,
  defaultColDef = DefaultColDef,
  error,
  errorComponentProps,
  frameworkComponents,

  // default for using immutableData from immer
  getRowNodeId = (node: any) => node.syncariId || node.id,

  // default for immer datasource
  immutableData = true,

  loading = false,
  loadingText = tc('loading'),
  noRowsOverlayComponent = NoRowsOverlay as any,
  noRowsOverlayComponentProps = {},
  onColumnEventsSettled,
  onColumnMoved,
  onColumnPinned,
  onColumnVisible,
  onGridReady,
  sizeColumnsToFit,
  rowData,
  themeName = 'ag-theme-balham',
  footerAccessory,
  pagerComponent,
  onCellFocused,
  disableRowSelectionForCells,
  onFirstDataRendered,

  // we want to disable virtualization by default when testing
  // if we don't then all of our expected data may not be rendered
  suppressColumnVirtualisation = process.env.NODE_ENV === 'test',

  style,
  ...props
}: AgTableProps) => {
  const [gridApi, setGridApi] = useState<GridApi | null>(null);
  const [columnApi, setColumnApi] = useState<ColumnApi | null>(null);
  const [columnEvents, setColumnEvents] = useState<CombinedColumnEvent[]>([]);

  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') {
      validateMutuallyExclusiveProps<AgTableProps>({
        autoSizeColumns,
        sizeColumnsToFit,
      });
    }
  }, [autoSizeColumns, sizeColumnsToFit]);

  const agFrameworkComponents = useMemo(
    () => ({
      [NO_ROWS_OVERLAY_COMPONENT_NAME]: noRowsOverlayComponent,
      ...frameworkComponents,
    }),
    [frameworkComponents, noRowsOverlayComponent]
  );

  const stableAutomaticColumnResizing = useCallback(
    (gApi: GridApi, cApi: ColumnApi) => {
      if (autoSizeColumns) {
        handleAutoSizeColumns(autoSizeColumns, cApi);
      } else if (sizeColumnsToFit) {
        switch (sizeColumnsToFit) {
          case ResizeColumnsCondition.ALWAYS:
            gApi.sizeColumnsToFit();
            break;
          case ResizeColumnsCondition.WHEN_NARROWER:
            forceFillGridWidth(gApi, cApi);
            break;
          default:
            throw new UnreachableCaseError(sizeColumnsToFit);
        }
      }
    },
    [autoSizeColumns, sizeColumnsToFit]
  );

  const [handleAutomaticColumnResizing] = useDebouncedCallback(
    stableAutomaticColumnResizing,
    AUTOMATIC_COLUMN_RESIZE_DEBOUNCE,
    { trailing: true }
  );

  const handleOnGridReady = (event: GridReadyEvent) => {
    setGridApi(event.api);
    setColumnApi(event.columnApi);
    onGridReady?.(event);
  };

  /** the ag-grid column events are a bit too much of a firehose to consume
    in a simple fashion. We can coalesce the events here for a duration
    and then give them all at once to a consumer. We'll pass the columnApi
    as the second arg so that the consumer can call `.getAllGridColumns()` in
    order to get a clearer picture of the world.
  */
  const onColumnEventsSettledCb = useCallback(
    (events: CombinedColumnEvent[]) => {
      onColumnEventsSettled?.(events, columnApi);
      setColumnEvents([]);
    },
    [onColumnEventsSettled, columnApi]
  );

  const [callbackWithEvents] = useDebouncedCallback(onColumnEventsSettledCb, columnEventsSettledDelay);

  /** given an EventType and a handler for that event type, we return a wrapped fn for AgTable
   * this wrapper handler will accumulate the events and call our debounced settled callback
   */
  const accumulateEvent = <T extends CombinedColumnEvent>(handler?: (evt: T) => void) => (evt: T) => {
    setColumnEvents((prev) => [...prev, evt]);
    handler?.(evt);
    callbackWithEvents(columnEvents);
  };

  const handleOnFirstDataRendered = (evt: FirstDataRenderedEvent) => {
    handleAutomaticColumnResizing(evt.api, evt.columnApi);
    onFirstDataRendered?.(evt);
  };

  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });

  // ensure specific columns are autosized to their content
  useEffect(() => {
    if (columnApi && gridApi) {
      handleAutomaticColumnResizing(gridApi, columnApi);
    }
  }, [columnApi, gridApi, handleAutomaticColumnResizing, dimensions.width]);

  const onCellFocusedWrapper = (e: CellFocusedEvent) => {
    if (disableRowSelectionForCells) {
      // See https://stackoverflow.com/a/50919233/4280755
      const rendererName = e.column?.getColDef().cellRenderer;
      const suppressSelection = typeof rendererName === 'string' && disableRowSelectionForCells.includes(rendererName);

      ((e.api as unknown) as EnhancedGridApi).gridOptionsWrapper.gridOptions.suppressRowClickSelection = suppressSelection;
    }

    onCellFocused?.(e);
  };

  return (
    <>
      <div style={style} className={cx('ag-table-wrapper', themeName, className)} ref={measurementRef}>
        <Spin spinning={loading} tip={loadingText}>
          <AgGridReact
            onGridReady={handleOnGridReady}
            onFirstDataRendered={handleOnFirstDataRendered}
            defaultColDef={defaultColDef}
            overlayLoadingTemplate="<div />"
            immutableData={immutableData}
            getRowNodeId={getRowNodeId}
            frameworkComponents={agFrameworkComponents}
            noRowsOverlayComponent={NO_ROWS_OVERLAY_COMPONENT_NAME}
            noRowsOverlayComponentParams={noRowsOverlayComponentProps}
            rowData={rowData}
            onColumnMoved={accumulateEvent<ColumnMovedEvent>(onColumnMoved)}
            onColumnPinned={accumulateEvent<ColumnPinnedEvent>(onColumnPinned)}
            onColumnVisible={accumulateEvent<ColumnVisibleEvent>(onColumnVisible)}
            columnDefs={columnDefs}
            applyColumnDefOrder={applyColumnDefOrder}
            onCellFocused={onCellFocusedWrapper}
            suppressColumnVirtualisation={suppressColumnVirtualisation}
            {...props}
          />
        </Spin>
        {!loading && error && <TableError error={error} {...errorComponentProps} />}
      </div>
      <HStack justify="end">
        {footerAccessory && footerAccessory}
        {pagerComponent && pagerComponent}
      </HStack>
    </>
  );
};

export default AgTable;
