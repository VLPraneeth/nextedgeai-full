import { RouteComponentProps, useNavigate, useLocation, Router } from '@reach/router';
import { ColDef, IHeaderParams } from 'ag-grid-community';
import { Link } from '@reach/router';
import { useDispatch } from 'react-redux';
import type { ThunkDispatch } from '@reduxjs/toolkit';
import type { AnyAction } from 'redux';
import { checkCustomRuleAssignmentExists } from 'store/data-quality/thunks';

import Icon from 'antd/lib/icon';
import Tooltip from 'antd/lib/tooltip';
import ObjectID from 'bson-objectid';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ReactComponent as RefreshOutline } from 'assets/icons/refresh-outline.svg';
import AgTable, { CursorBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import { MAX_AMOUNT_OF_NON_VIRTUALIZED_COLUMNS } from 'components/AgTable/constants';
import Can from 'components/Can';
import { useI18nContext } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import { FilterRef } from 'components/inputs/filter';
import { LeftValue } from 'components/inputs/types';
import KebabMenu, { joinGroupsWithDividers, MenuItem } from 'components/KebabMenu';
import { HStack, Stack } from 'components/layout';
import { agFrameworkComponentsFromRendererMap, defaultRendererMap } from 'components/renderers';
import FileLinkRenderer from 'components/renderers/FileLinkRenderer';
import SyncariSystemFieldHeader from 'components/renderers/SyncariSystemFieldHeader';
import RouteSpin from 'components/RouteSpin';
import { TableFilterProvider, TableFilterProviderRef } from 'components/TableFilters';
import { TranslatedText } from 'components/typography';
import { useCursorPagination, useOffsetBasedPagination } from 'hooks/pagination';
import { useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { useEntityFiltersList, useEntityRecordsList } from 'store/data-studio/hooks';
import { EntityFilter, EntityRecord, FieldMetadata, SyncariExtendedRecordDeletedKeys } from 'store/data-studio/types';
import { selectEntityById } from 'store/entity/selectors';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadFile } from 'utils/DownloadUtil';
import { packageData } from 'utils/ErrorUtils';
import { colors, variables } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { remToPixels } from 'utils/StyleUtil';
import { UnreachableCaseError } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';
import { ColumnItem } from './Filters/DataStudioConfigureColumn';
import BatchModal, { BatchOperationMode, BatchProgressMenu } from './Batch';
import BatchHistoryDrawer, { BatchHistoryDrawerMode } from './Batch/HistoryDrawer';
import DataScore from './DataScore';
import FilterPanel from './FilterPanel';
import { useFilterFromQueryString, useUserConfiguredColumnsForEntity } from './hooks';
import { RecordActionCell } from './RecordActionCell';
import DataStudioRecordDetail from './RecordDetail/RecordDetail';
import { processPredicates } from './utils';

import './DataStudio.less';
import DataStudioFilterButton from './Filters/DataStudioFilterButton';
import { Drawer } from 'antd';
import Button from 'components/button-component';
import CustomHeaderGrid from './CustomHeaderGrid';
import { updateAdhocEntityFilter } from 'store/data-studio/actions';
import { DragDropContext, DropResult } from 'react-beautiful-dnd';
import InlineMessage from 'components/InlineMessage';
import SearchBox from 'components/SearchBox';
import { moveItem } from 'utils/ArrayUtil';
import { ColumnList } from './Filters/DataStudioConfigureColumn';
import './Filters/DataStudioConfigureColumn/ConfigureColumn.scss';
import { IconButton } from 'components/Button';

const NEW_FILTER_KEY = 'new';

// Module-level Map to store sorting state per entity
// This persists across component remounts while filters change
interface SortingState {
  orderBy?: string;
  sortDirection?: 'asc' | 'desc';
}
const entitySortingState = new Map<string, SortingState>();

const SyncariEntityColumns = ['syncariId', 'syncariTimestamp', 'idMapping', 'deleted'] as const;
const IdMappingDataType = 'idMapping';

// expand to support our keys as well as idMapping field
interface EnhancedFieldMetadata extends Omit<FieldMetadata, 'dataType'> {
  dataType: FieldMetadata['dataType'] | typeof IdMappingDataType;
  key?: string;
}

const exclamationStyle = { fontSize: variables.fontSizes.lgr };

// Don't any renderer in this list of metadata label.
const blackListLabelRenderer = ['status'];

const isSyncariEntitySpecialField = (
  variableToCheck: any
): variableToCheck is typeof SyncariEntityColumns | SyncariExtendedRecordDeletedKeys => {
  return [...Object.values(SyncariExtendedRecordDeletedKeys), ...SyncariEntityColumns].includes(variableToCheck);
};

const actionColDef: ColDef = {
  headerName: 'Actions',
  colId: 'actions',
  field: '__actionsCol',
  cellRenderer: 'actions',
  pinned: 'right',
  minWidth: remToPixels(10), // 10rem, in pixels
  maxWidth: remToPixels(10), // 10rem, in pixels
};

/**
 * Reusable function to add tooltip functionality to a column definition
 * The tooltip will display the cell value
 */
const addTooltipToColumn = (colDef: ColDef): ColDef => {
  return {
    ...colDef,
    tooltipValueGetter: (params) => {
      // Get the value from the cell
      const value = params.value;

      // Don't show tooltip for null or undefined values
      if (value === null || value === undefined) {
        return '';
      }

      // For objects/arrays, stringify them
      if (typeof value === 'object') {
        try {
          return JSON.stringify(value, null, 2);
        } catch {
          return String(value);
        }
      }

      // For booleans, convert to string
      if (typeof value === 'boolean') {
        return value ? 'true' : 'false';
      }

      // For everything else, convert to string
      return String(value);
    },
  };
};

const getAgColumnsConfig = (
  metadata: Record<string, EnhancedFieldMetadata>,
  columns: string[],
  frameworkComponents: typeof defaultRendererMap,
  entityId: string,
  fieldValues: LeftValue[],
  appliedFilter?: EntityFilter | Partial<EntityFilter>,
  onApplyFilter?: (filter: Partial<EntityFilter> | undefined) => void,
  error?: string,
  onColumnVisibilityChange?: (columnName: string, isVisible: boolean) => void,
  onSortChange?: (orderBy: string, sortDirection: 'asc' | 'desc') => void
): ColDef[] => [
  ...columns
    .filter((column) => column in metadata && metadata[column].canDisplay)
    .map((column) => {
      const meta = metadata[column];

      const colDef: ColDef = {
        headerName: meta.label,
        colId: column,
        field: isSyncariEntitySpecialField(column) ? column : `values.${column}`,
        headerComponentFramework: (props: IHeaderParams) =>
          meta.isSystem ? (
            <SyncariSystemFieldHeader {...props} />
          ) : (
            <CustomHeaderGrid
              entityId={entityId}
              fieldMetadata={meta as FieldMetadata}
              fieldValues={fieldValues}
              appliedFilter={appliedFilter}
              onApplyFilter={onApplyFilter}
              error={error}
              onColumnVisibilityChange={onColumnVisibilityChange}
              onSortChange={onSortChange}
              {...props}
            />
          ),
        cellRenderer:
          !blackListLabelRenderer.includes(meta.label) && meta.label in frameworkComponents
            ? meta.label
            : meta.dataType in frameworkComponents
            ? (meta.dataType as string)
            : undefined,
      };

      // Apply tooltip to all columns
      return addTooltipToColumn(colDef);
    }),
  actionColDef,
];

enum DataStudioGridAction {
  CONFIGURE_COLUMNS = 'Configure Columns',
  DELETE_DATA = 'Delete Data',
  PURGE_DATA = 'Purge Data',
  UPDATE_DATA = 'Update Data',
  SHOW_DELETE_HISTORY = 'Show Delete History',
  SHOW_UPDATE_HISTORY = 'Show Update History',
  EXPORT_DATA = 'Export Data',
}

interface DataStudioGridProps {
  entityId: string;
  appliedFilter?: EntityFilter | Partial<EntityFilter>;
  onRecordCountChange?: (forceFetch?: boolean) => void;
}

const DataStudioGrid = ({ entityId, appliedFilter, onRecordCountChange }: DataStudioGridProps) => {
  const [showingBatchModalForOperation, setShowingBatchModalForOperation] = useState(BatchOperationMode.NONE);
  const [batchHistoryDrawerMode, setBatchHistoryDrawerMode] = useState(BatchHistoryDrawerMode.CLOSED);
  const [showDataScore, setShowDataScore] = useState(false);
  const [filterDrawerVisible, setFilterDrawerVisible] = useState({
    visible: false,
    mode: '',
  });
  const [isSaveFilterChecked, setIsSaveFilterChecked] = useState(false);
  const [configureColumnsDrawerVisible, setConfigureColumnsDrawerVisible] = useState(false);
  const [draftAllAvailableColumns, setDraftAllAvailableColumns] = useState<ColumnItem[]>([]);
  const [columnsFilterString, setColumnsFilterString] = useState('');
  const [hideDisabledColumns, setHideDisabledColumns] = useState<boolean>(false);
  const [columnsErrorMessage, setColumnsErrorMessage] = useState('');

  // Initialize sorting state from module-level Map (persists across remounts)
  const [orderBy, setOrderBy] = useState<string | undefined>(() => {
    return entitySortingState.get(entityId)?.orderBy;
  });
  const [sortDirection, setSortDirection] = useState<string | undefined>(() => {
    return entitySortingState.get(entityId)?.sortDirection;
  });

  const { tn, tc } = useI18nContext();

  const dispatch = useDispatch<ThunkDispatch<any, any, AnyAction>>();
  const entityName = useEnhancedSelector((state) => selectEntityById(state, entityId))?.displayName;

  // Use cursor-based pagination when no sorting is active
  const cursorPagination = useCursorPagination();

  // Use offset-based pagination when sorting is active
  const offsetPagination = useOffsetBasedPagination();

  // Determine which pagination mode to use based on whether sorting is active
  const isOffsetBased = Boolean(orderBy);
  const pageSize = isOffsetBased ? offsetPagination.pageSize : cursorPagination.pageSize;
  const setPageSize = isOffsetBased ? offsetPagination.setPageSize : cursorPagination.setPageSize;
  const resetPagination = isOffsetBased ? offsetPagination.resetPagination : cursorPagination.resetPagination;

  // Pagination handlers that work for both modes
  // For offset-based, ignore the cursor parameter and just call the handler
  // For cursor-based, use the cursor parameter
  const onRequestNextPage = useCallback(
    (cursor?: string, count?: number) => {
      if (isOffsetBased) {
        offsetPagination.onRequestNextPage();
      } else if (cursor) {
        cursorPagination.onRequestNextPage(cursor, count);
      }
    },
    [isOffsetBased, offsetPagination, cursorPagination]
  );

  const onRequestPrevPage = useCallback(
    (cursor?: string, count?: number) => {
      if (isOffsetBased) {
        offsetPagination.onRequestPrevPage();
      } else if (cursor) {
        cursorPagination.onRequestPrevPage(cursor, count);
      }
    },
    [isOffsetBased, offsetPagination, cursorPagination]
  );

  const [filterInEditor, setFilterInEditor] = useState<DataStudioGridProps['appliedFilter']>(appliedFilter);

  const location = useLocation();

  // Track if we're on a record detail page
  const isRecordDetailPath = useMemo(() => {
    return /\/record\/[^/]+(\/|$)/.test(location.pathname);
  }, [location.pathname]);

  // this gives us a handle on the Filter component
  const filterControlRef = useRef<FilterRef | null>(null);
  const filterPanelRef = useRef<TableFilterProviderRef | null>(null);
  const navigate = useNavigate();

  // this is used to naiively track when we're on the first page - this will be
  // unreliable with client configured sorting
  const [firstPageStartCursor, setFirstPageStartCursor] = useState<string | null>();

  // Retrieves filtered records from backend
  // Use either cursor-based or offset-based pagination depending on whether sorting is active
  const { error, loading, refetch, data, metadata } = useEntityRecordsList({
    entityId,
    count: pageSize,
    cursor: isOffsetBased ? undefined : cursorPagination.cursor,
    direction: isOffsetBased ? 'next' : cursorPagination.direction,
    filter: appliedFilter ? appliedFilter.criteria : undefined,
    orderBy,
    sortDirection,
    page: isOffsetBased ? offsetPagination.page : undefined,
  });
  const previousError = usePreviousValue(error);

  // Get list of saved filters
  const { data: bookmarkedFiltersData } = useEntityFiltersList({ bookmarked: false });

  const activeEntitySavedFilters = useMemo(() => {
    if (!bookmarkedFiltersData?.filters) return [];
    return bookmarkedFiltersData.filters.filter((filter) => filter.syncariEntityId === entityId);
  }, [bookmarkedFiltersData, entityId]);

  // Check if the applied filter is saved
  const isFilterSaved = useMemo(() => {
    if (!appliedFilter || !appliedFilter.id) {
      return false;
    }
    return activeEntitySavedFilters.some((savedFilter) => savedFilter.id === appliedFilter.id);
  }, [appliedFilter, activeEntitySavedFilters]);

  useEffect(() => {
    // Open filter panel by default
    if (filterPanelRef.current) {
      filterPanelRef.current.setFiltersVisible(true);
    }
  }, []);

  useEffect(() => {
    if (!!error && error !== previousError) {
      // if we just encountered an error, open the filter panel to show it
      filterPanelRef.current?.setFiltersVisible(true);
    }
  }, [error, previousError]);

  const allDisplayableColumns = useMemo(() => {
    return Object.entries(metadata)
      .filter(([, metadata]) => metadata.canDisplay)
      .map(([columnName]) => ({
        columnName,
        isSelected: true,
      }));
  }, [metadata]);

  const [allColumns, updateColumns] = useUserConfiguredColumnsForEntity(entityId, allDisplayableColumns);

  // remove actions before saving - we shouldn't save this to the server, it's a forced column
  const onColumnsUpdated = useCallback(
    (columns: ColumnItem[]) => {
      const filteredColumns = columns.filter((col) => col.columnName !== actionColDef.colId);
      updateColumns(filteredColumns);
    },
    [updateColumns]
  );

  const exportData = useCallback(() => {
    const predicate = appliedFilter?.criteria ? packageData(appliedFilter?.criteria) || '' : '';
    downloadFile(makeUrl(DataUrlConstants.DOWNLOAD_ENTITY_RECORDS_LIST, { entityId }), {
      predicate,
    });
  }, [entityId, appliedFilter]);

  const onApplyFilter = useCallback(
    (draftFilter?: Partial<EntityFilter>) => {
      resetPagination();
      setFirstPageStartCursor(null);

      // when applying a filter, the filter will no longer match the stored
      // filter data, so we should store the new filter as ad-hoc
      // and update the querystring.
      // Doing so will allow the user to navigate back to this page as long as
      // our store is in memory.
      //
      // Saving an ad-hoc filter should then update the querystring, after
      // successful save, to the now persisted filterId

      // Retain existing filter ID if present, otherwise generate a new one
      const updatedFilterId = draftFilter?.id || ObjectID.generate();
      const updatedFilter = {
        ...draftFilter,
        id: updatedFilterId,
      };

      if (updatedFilter.criteria) {
        // Save current filter in localstorage to persist after returning
        // from viewing a record
        sessionStorage.setItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID, updatedFilterId);
        dispatch(updateAdhocEntityFilter(entityId, updatedFilterId, updatedFilter.criteria, updatedFilter));

        // navigate to apply new filter
        navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }, { filterId: updatedFilterId }), {
          replace: true,
        });
      }
    },
    [dispatch, entityId, resetPagination, navigate]
  );

  const resetFilters = useCallback(() => {
    sessionStorage.removeItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID);
    // force a full refresh because Filter maintains it's own internal state
    // that we can't easily reset
    navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: '' })).then(() => {
      navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }), { replace: true });
    });
  }, [entityId, navigate]);

  // Reset filters and sorts after creating a new record or clearing filters
  const resetFiltersAndSortsAfterCreate = useCallback(() => {
    entitySortingState.delete(entityId);
    setOrderBy(undefined);
    setSortDirection(undefined);
    resetPagination();
    setFirstPageStartCursor(null);
    onRecordCountChange?.(true);
    resetFilters();
    refetch();
  }, [entityId, navigate, resetPagination, onRecordCountChange, refetch]);

  const openConfigureColumnsDrawer = useCallback(() => {
    setDraftAllAvailableColumns(allColumns);
    setColumnsFilterString('');
    setColumnsErrorMessage('');
    setHideDisabledColumns(false);
    setConfigureColumnsDrawerVisible(true);
  }, [allColumns]);

  const closeConfigureColumnsDrawer = useCallback(() => {
    setColumnsFilterString('');
    setColumnsErrorMessage('');
    setHideDisabledColumns(false);
    setConfigureColumnsDrawerVisible(false);
  }, []);

  const handleColumnDragEnd = useCallback((result: DropResult) => {
    const { destination, source } = result;
    if (
      source.droppableId === 'selected' &&
      destination?.droppableId === 'selected' &&
      source.index !== destination.index
    ) {
      setDraftAllAvailableColumns((columns) => {
        const newItems = moveItem(columns, source.index, destination.index);
        return newItems;
      });
    }
  }, []);

  const toggleHideDisabledColumns = useCallback(() => {
    if (hideDisabledColumns) {
      const selectedItems: ColumnItem[] = [];
      const nonSelectedItems: ColumnItem[] = [];

      draftAllAvailableColumns.forEach((col) => {
        if (col.isSelected) {
          selectedItems.push(col);
        } else {
          nonSelectedItems.push(col);
        }
      });

      nonSelectedItems.sort((a, b) => a.columnName.localeCompare(b.columnName));
      setDraftAllAvailableColumns(() => [...selectedItems, ...nonSelectedItems]);
      setHideDisabledColumns(false);
    } else {
      setHideDisabledColumns(true);
    }
  }, [draftAllAvailableColumns, hideDisabledColumns]);

  const handleSelectedColumnChange = useCallback((columnName: string, checked: boolean) => {
    setDraftAllAvailableColumns((columns) =>
      columns.map((col) => {
        if (columnName === col.columnName) {
          return {
            columnName,
            isSelected: checked,
          };
        }
        return col;
      })
    );
  }, []);

  // Handle column visibility change from header popover
  const handleColumnVisibilityChange = useCallback(
    (columnName: string, isVisible: boolean) => {
      const updatedColumns = allColumns.map((col) =>
        col.columnName === columnName ? { ...col, isSelected: isVisible } : col
      );
      onColumnsUpdated(updatedColumns);
    },
    [allColumns, onColumnsUpdated]
  );

  const handleSortChange = useCallback(
    (newOrderBy: string, newSortDirection: 'asc' | 'desc') => {
      setOrderBy(newOrderBy);
      setSortDirection(newSortDirection);
      // Persist sorting to module-level Map so it survives filter changes
      entitySortingState.set(entityId, { orderBy: newOrderBy, sortDirection: newSortDirection });
      offsetPagination.resetPagination();
      setFirstPageStartCursor(null);
      navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }), { replace: false });
    },
    [entityId, navigate]
  );

  const handleSaveColumns = useCallback(() => {
    if (!draftAllAvailableColumns.find((col) => col.isSelected)) {
      setColumnsErrorMessage(tn('no_selected_columns'));
      return;
    }
    onColumnsUpdated(draftAllAvailableColumns);
    closeConfigureColumnsDrawer();
  }, [draftAllAvailableColumns, onColumnsUpdated, closeConfigureColumnsDrawer, tn]);

  const moveColumnTo = useCallback((currentIndex: number, destinationIndex: number) => {
    setDraftAllAvailableColumns((columns) => {
      const newItems = moveItem(columns, currentIndex, destinationIndex);
      return newItems;
    });
  }, []);

  const handlePostDeleteOperation = useCallback(() => {
    onRecordCountChange?.(true);
    refetch();
  }, [onRecordCountChange, refetch]);

  useEffect(() => {
    if (!firstPageStartCursor && !loading && data) {
      // track our "first page" by saving the first
      // "start" cursor that we get on the response
      // this should be the first record of a our first
      // page in the result set
      setFirstPageStartCursor(data.pageInfo.start);
    }
  }, [firstPageStartCursor, data, loading]);

  // generates sorted FieldValues for Filter
  const fieldValues = useMemo(() => {
    return Object.values(metadata)
      .filter((meta) => meta.canFilter)
      .map((meta) => ({
        dataType: meta.dataType,
        label: meta.label,
        picklistGroup: '',
        type: 'variable',
        value: meta.fieldId,
      }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }, [metadata]);

  const onRequestNavigateToRecordDetail = useCallback(
    (entityId: string, recordId: string) => {
      navigate(makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId }), {
        replace: false,
      });
    },
    [navigate]
  );

  const agFrameworkComponents = useMemo(() => {
    return agFrameworkComponentsFromRendererMap({
      ...defaultRendererMap,
      filelink: (_: string, record: EntityRecord) => (
        <FileLinkRenderer entityId={entityId} recordId={record.syncariId || record.id} />
      ),
      actions: (_: string, record: EntityRecord) => (
        <RecordActionCell
          entityId={entityId}
          recordId={record.syncariId || record.id}
          onRequestRecordDetail={() => {
            navigate(makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId: record.syncariId }), {
              replace: false,
            });
          }}
          onRecordDeleted={handlePostDeleteOperation}
        />
      ),
    });
  }, [entityId, onRequestNavigateToRecordDetail, handlePostDeleteOperation]);

  const getBatchMenuItems = () => {
    // Check if filter is applied
    const hasFilter = (appliedFilter?.criteria?.predicates?.length || 0) > 0;

    return joinGroupsWithDividers([
      [
        <MenuItem key={DataStudioGridAction.EXPORT_DATA}>
          <TranslatedText text="export" />
        </MenuItem>,
      ],
      [
        <MenuItem key={DataStudioGridAction.CONFIGURE_COLUMNS}>
          <TranslatedText text="configure_columns_tooltip" />
        </MenuItem>,
      ],
      [
        <Can key={DataStudioGridAction.UPDATE_DATA} permission={AllPermissions.WRITE_DATA_STUDIO}>
          <MenuItem disabled={!Boolean(appliedFilter)}>
            <Tooltip title={!Boolean(appliedFilter) && tn('apply_filter_record_actions', { action: tc('update') })}>
              <TranslatedText
                text={appliedFilter ? 'batch_update_filtered_records' : 'batch_update_entity_records'}
                args={{ count: pageInfo?.filteredCount }}
              />
            </Tooltip>
          </MenuItem>
        </Can>,
        <MenuItem key={DataStudioGridAction.SHOW_UPDATE_HISTORY}>
          <TranslatedText text="show_batch_update_history" />
        </MenuItem>,
      ],
      // Conditionally render DELETE or DELETE ALL based on filter state
      hasFilter
        ? [
            <Can key={DataStudioGridAction.DELETE_DATA} permission={AllPermissions.WRITE_DATA_STUDIO}>
              <MenuItem disabled={!Boolean(appliedFilter)}>
                <Tooltip title={!Boolean(appliedFilter) && tn('apply_filter_record_actions', { action: tc('delete') })}>
                  <TranslatedText
                    text={appliedFilter ? 'batch_delete_filtered_records' : 'batch_delete_entity_records'}
                    args={{ count: pageInfo?.filteredCount }}
                  />
                </Tooltip>
              </MenuItem>
            </Can>,
          ]
        : [
            <Can key={DataStudioGridAction.PURGE_DATA} permission={AllPermissions.WRITE_DATA_STUDIO}>
              <MenuItem disabled={hasFilter}>
                <Tooltip title={hasFilter ? tn('purge_tooltip_text') : undefined}>
                  <TranslatedText args={{ count: pageInfo?.filteredCount }} text="batch_delete_entity_records" />
                </Tooltip>
              </MenuItem>
            </Can>,
          ],
      [
        <MenuItem key={DataStudioGridAction.SHOW_DELETE_HISTORY}>
          <TranslatedText text="show_batch_delete_history" />
        </MenuItem>,
      ],
    ]);
  };

  const columnDefs = useMemo(() => {
    if (!data) {
      return [];
    }
    const selectedColumns = allColumns.filter((col) => col.isSelected).map((col) => col.columnName);

    const columnsList = Array.from(new Set([...selectedColumns, 'actions']));
    return getAgColumnsConfig(
      metadata,
      columnsList,
      agFrameworkComponents,
      entityId,
      fieldValues,
      appliedFilter,
      onApplyFilter,
      error?.errorMessage,
      handleColumnVisibilityChange,
      handleSortChange
    );
  }, [
    data,
    metadata,
    allColumns,
    agFrameworkComponents,
    entityId,
    fieldValues,
    appliedFilter,
    onApplyFilter,
    error,
    handleColumnVisibilityChange,
    orderBy,
    sortDirection,
    handleSortChange,
  ]);

  const datasource = data?.records || [];
  // When using offset-based pagination, we need to clear cursors from pageInfo
  // and provide the page number for the Pagination component
  const pageInfo = data?.pageInfo
    ? {
        ...data.pageInfo,
        // Clear cursors for offset-based pagination and provide page number
        start: isOffsetBased ? null : data.pageInfo.start,
        end: isOffsetBased ? null : data.pageInfo.end,
        // For offset-based pagination, use page number - 1 (0-indexed for Pagination component)
        pageNumber: isOffsetBased ? offsetPagination.page - 1 : data.pageInfo.pageNumber,
      }
    : undefined;

  useEffect(() => {
    const checkDataQuality = async () => {
      try {
        const action = await dispatch(checkCustomRuleAssignmentExists());
        if (checkCustomRuleAssignmentExists.fulfilled.match(action)) {
          setShowDataScore(action.payload);
        } else {
          setShowDataScore(false);
        }
      } catch {
        setShowDataScore(false);
      }
    };

    checkDataQuality();
  }, [dispatch]);

  const openFilterDrawer = useCallback(() => {
    setFilterDrawerVisible({
      visible: true,
      mode: '',
    });
    setIsSaveFilterChecked(false);
  }, []);

  const openFilterDrawerToSave = useCallback(() => {
    setFilterDrawerVisible({
      visible: true,
      mode: '',
    });
    setIsSaveFilterChecked(true);
    setFilterInEditor(appliedFilter || {});
  }, [appliedFilter]);

  const closeFilterDrawer = useCallback(() => {
    setFilterDrawerVisible({
      visible: false,
      mode: '',
    });
    setIsSaveFilterChecked(false);
    setFilterInEditor({});
  }, []);

  const onEditFilter = (filter: EntityFilter, view: boolean) => {
    setFilterDrawerVisible({
      visible: true,
      mode: view ? 'View' : 'Edit',
    });
    setFilterInEditor(filter);
    setIsSaveFilterChecked(true);
  };

  const handleRowClick = useCallback(
    ({ data }) => {
      const url = makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId: data.syncariId }) + '/view';
      navigate(url, {
        replace: false,
      });
    },
    [entityId, navigate]
  );

  return (
    <TableFilterProvider ref={filterPanelRef}>
      <div className="data-studio-main-content content-section" data-drawer-visible={isRecordDetailPath}>
        <Stack fill>
          {pageInfo?.message && <InfoBox message={pageInfo.message} type="info" />}
          <Stack spacing="xxxs">
            <div className="data-studio-meta-row">
              <HStack spacing="md" align="center">
                {showDataScore && (
                  <DataScore
                    entityId={entityId}
                    predicate={appliedFilter?.criteria}
                    onRequestShowRecords={(factor) => {
                      filterPanelRef.current?.setFiltersVisible(true);
                      filterControlRef.current?.addFilterCondition(factor.filterCondition, true);
                    }}
                  />
                )}
                {error && (
                  <Tooltip title={error?.message}>
                    <Icon
                      style={exclamationStyle}
                      theme="twoTone"
                      type="exclamation-circle"
                      twoToneColor={colors.red500}
                    />
                  </Tooltip>
                )}
                <DataStudioFilterButton
                  activeFilterCount={processPredicates.countAll(appliedFilter?.criteria?.predicates || [])}
                  savedFilters={activeEntitySavedFilters}
                  isFilterApplied={Boolean(appliedFilter)}
                  onSelectFilter={onApplyFilter}
                  onEditFilter={onEditFilter}
                  onCreateNewFilter={openFilterDrawer}
                  onClearFilters={resetFilters}
                  currentAppliedFilter={appliedFilter}
                />
                {appliedFilter && !error && (
                  <>
                    {!isFilterSaved && (
                      <HStack spacing="sm" className="data-studio-filter-save-button">
                        <Button type="default" onClick={openFilterDrawerToSave}>
                          Save Filter
                        </Button>
                      </HStack>
                    )}
                    <span>{tn('showing_matching_records', { count: pageInfo?.filteredCount })}</span>
                  </>
                )}
              </HStack>
              <HStack spacing="md" justify="space-between">
                <BatchProgressMenu entityId={entityId} />
                <HStack spacing="xs" className="kebab-menu">
                  <KebabMenu<DataStudioGridAction>
                    menuItems={getBatchMenuItems()}
                    overlayClassName="data-studio-kebab-menu-dropdown"
                    onClick={({ key }) => {
                      switch (key) {
                        case DataStudioGridAction.CONFIGURE_COLUMNS:
                          openConfigureColumnsDrawer();
                          break;
                        case DataStudioGridAction.DELETE_DATA:
                          setShowingBatchModalForOperation(BatchOperationMode.DELETE);
                          break;
                        case DataStudioGridAction.PURGE_DATA:
                          setShowingBatchModalForOperation(BatchOperationMode.PURGE);
                          break;
                        case DataStudioGridAction.UPDATE_DATA:
                          setShowingBatchModalForOperation(BatchOperationMode.UPDATE);
                          break;
                        case DataStudioGridAction.SHOW_DELETE_HISTORY:
                          setBatchHistoryDrawerMode(BatchHistoryDrawerMode.DELETE);
                          break;
                        case DataStudioGridAction.SHOW_UPDATE_HISTORY:
                          setBatchHistoryDrawerMode(BatchHistoryDrawerMode.UPDATE);
                          break;
                        case DataStudioGridAction.EXPORT_DATA:
                          exportData();
                          break;
                        default:
                          throw new UnreachableCaseError(key);
                      }
                    }}
                    size="small"
                  />
                </HStack>
                <IconButton className="refresh-btn" icon={RefreshOutline} onClick={refetch} />
                <Link
                  className="data-studio-link"
                  to={makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
                    entityId,
                    graphVersion: AppConstants.GRAPH_STATUS.APPROVED,
                  })}>
                  {tn('view_pipeline')}
                </Link>
                <Button
                  type="primary"
                  onClick={() =>
                    navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }) + '/record/create', {
                      replace: false,
                    })
                  }>
                  <Icon type="plus" style={{ padding: '8px 0' }} />
                  Create Record
                </Button>
              </HStack>
            </div>
            <Drawer
              title={filterInEditor?.name ? filterInEditor.name : 'Create a New Filter'}
              placement="right"
              width={800}
              onClose={closeFilterDrawer}
              visible={filterDrawerVisible.visible}
              className="data-studio-filter-drawer"
              destroyOnClose>
              <FilterPanel
                key={filterInEditor?.id || NEW_FILTER_KEY}
                currentMode={filterDrawerVisible.mode}
                entityId={entityId}
                fieldValues={fieldValues}
                filter={appliedFilter}
                filterInEditor={filterInEditor}
                error={error?.errorMessage}
                onApplyFilter={onApplyFilter}
                onRequestRefreshData={refetch}
                onRequestResetFilter={resetFilters}
                onRequestShowFilterPanel={closeFilterDrawer}
                filterControlRef={filterControlRef}
                isSaveFilterChecked={isSaveFilterChecked}
                onSaveFilterCheckChange={setIsSaveFilterChecked}
                onRequestSaveFilter={(updatedFilter) => {
                  setFilterInEditor(updatedFilter);
                }}
              />
            </Drawer>
          </Stack>
          <AgTable
            // only virtualize columns if the data set is large enough to impact performance
            suppressColumnVirtualisation={columnDefs.length < MAX_AMOUNT_OF_NON_VIRTUALIZED_COLUMNS}
            columnDefs={columnDefs}
            frameworkComponents={agFrameworkComponents}
            loading={loading}
            rowData={datasource}
            enableCellTextSelection
            tooltipShowDelay={0}
            onRowClicked={(params) => {
              // Check if the click was on an action button or its children
              const target = params.event?.target as HTMLElement;
              const isActionClick = target.closest('.actions-cell, .action-button');

              if (!isActionClick) {
                handleRowClick(params);
              }
            }}
            suppressCellSelection
            suppressRowClickSelection
            noRowsOverlayComponentParams={{
              description: tn('no_records_found_in_entity', { entityName }),
            }}
            sizeColumnsToFit={columnDefs.length < 13 ? ResizeColumnsCondition.ALWAYS : undefined}
            pagerComponent={
              <CursorBasedPagination
                pageInfo={pageInfo}
                onRequestNextPage={onRequestNextPage}
                onRequestPreviousPage={onRequestPrevPage}
                pageSize={pageSize}
                onPageSizeChange={setPageSize}
                allowPageSizeChange
                isLoading={loading}
                splitLayout
                isOffsetBasedPagination={isOffsetBased}
                currentRecordCount={datasource.length}
              />
            }
          />
        </Stack>

        <Drawer
          title={tn('configure_columns_tooltip')}
          placement="right"
          width={631}
          onClose={closeConfigureColumnsDrawer}
          visible={configureColumnsDrawerVisible}
          className="data-studio-configure-columns-drawer"
          bodyStyle={{ padding: 0, height: 'calc(100% - 55px)', overflow: 'hidden' }}>
          <div
            style={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
            }}>
            <div
              style={{
                flex: 1,
                overflow: 'auto',
                padding: '24px',
              }}>
              <InlineMessage type="error" title={columnsErrorMessage}>
                {columnsErrorMessage}
              </InlineMessage>

              <div style={{ marginBottom: '16px' }}>
                <HStack spacing="xs" align="center" className="data-studio-configure-column-inputs">
                  <SearchBox
                    className="column-search-box"
                    onChange={(event) => setColumnsFilterString(event.target.value)}
                    placeholder={tc('search')}
                    value={columnsFilterString}
                  />
                  <Button
                    disabled={!draftAllAvailableColumns.find((col) => !col.isSelected)}
                    onClick={() => {
                      setDraftAllAvailableColumns((columns) => columns.map((col) => ({ ...col, isSelected: true })));
                    }}>
                    {tn('enable_all')}
                  </Button>
                  <Button
                    disabled={!draftAllAvailableColumns.find((col) => col.isSelected)}
                    onClick={() => {
                      setDraftAllAvailableColumns((columns) => columns.map((col) => ({ ...col, isSelected: false })));
                    }}>
                    {tn('disable_all')}
                  </Button>
                  <Button onClick={toggleHideDisabledColumns}>
                    {hideDisabledColumns ? tn('show_disabled') : tn('hide_disabled')}
                  </Button>
                </HStack>
              </div>

              <DragDropContext onDragEnd={handleColumnDragEnd}>
                <ColumnList
                  id="selected"
                  allItems={draftAllAvailableColumns}
                  dataTypeForColumn={(column) => (metadata && column in metadata ? metadata[column]?.dataType : '')}
                  labelForColumn={(column) => (column in metadata ? metadata[column].label : column)}
                  filterString={columnsFilterString}
                  hideDisabled={hideDisabledColumns}
                  handleSelectedItemChange={handleSelectedColumnChange}
                  moveTo={moveColumnTo}
                  emptyColumnContent={tn('no_selected_columns')}
                />
              </DragDropContext>
            </div>

            <div
              style={{
                borderTop: '1px solid #e8e8e8',
                padding: '16px 24px',
                backgroundColor: '#fff',
                flexShrink: 0,
              }}>
              <HStack spacing="md" justify="end">
                <Button onClick={closeConfigureColumnsDrawer}>{tc('cancel')}</Button>
                <Button type="primary" onClick={handleSaveColumns}>
                  {tc('save')}
                </Button>
              </HStack>
            </div>
          </div>
        </Drawer>

        <BatchModal
          entityId={entityId}
          filter={appliedFilter}
          fieldValues={fieldValues}
          recordsCount={pageInfo?.filteredCount}
          mode={showingBatchModalForOperation}
          onRequestClose={() => setShowingBatchModalForOperation(BatchOperationMode.NONE)}
        />
        <BatchHistoryDrawer
          entityId={entityId}
          fieldValues={fieldValues}
          mode={batchHistoryDrawerMode}
          onRequestClose={() => setBatchHistoryDrawerMode(BatchHistoryDrawerMode.CLOSED)}
        />
        <Router primary={false}>
          <DataStudioRecordDetail
            path="record/:recordId/*"
            entityId={entityId}
            onRecordCreated={resetFiltersAndSortsAfterCreate}
            onRecordDeleted={handlePostDeleteOperation}
          />
        </Router>
      </div>
    </TableFilterProvider>
  );
};

type DataStudioGridContainerProps = RouteComponentProps & DataStudioGridProps;

/* wrapper around DataStudioGrid in order to load filter/datascore factor filter
 * from the querystring before rendering and fetching records
 */
const DataStudioGridContainer = ({ entityId, onRecordCountChange }: DataStudioGridContainerProps) => {
  const filterFromQs = useFilterFromQueryString(entityId);
  const appliedFilter = useMemo(() => filterFromQs?.data, [filterFromQs]);

  if (filterFromQs?.loading) {
    return <RouteSpin title={<TranslatedText text="loading_from_filter" />} />;
  }

  return (
    <DataStudioGrid
      key={filterFromQs?.data?.id || filterFromQs?.data?.name || entityId}
      entityId={entityId}
      appliedFilter={appliedFilter}
      onRecordCountChange={onRecordCountChange}
    />
  );
};

export default DataStudioGridContainer;
