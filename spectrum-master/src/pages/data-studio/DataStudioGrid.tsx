import { RouteComponentProps, useNavigate } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { Link } from '@reach/router';
import { useDispatch } from 'react-redux';
import type { ThunkDispatch } from '@reduxjs/toolkit';
import type { AnyAction } from 'redux';
import { checkCustomRuleAssignmentExists } from 'store/data-quality/thunks';

import Icon from 'antd/lib/icon';
import Tooltip from 'antd/lib/tooltip';
import ObjectID from 'bson-objectid';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ReactComponent as ExportIcon } from 'assets/icons/export.svg';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { MAX_AMOUNT_OF_NON_VIRTUALIZED_COLUMNS } from 'components/AgTable/constants';
import { CursorBasedPagination } from 'components/AgTable/Pagination';
import Can from 'components/Can';
import { useI18nContext } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import { FilterRef } from 'components/inputs/filter';
import KebabMenu, { joinGroupsWithDividers, MenuItem } from 'components/KebabMenu';
import { HStack, Stack } from 'components/layout';
import { agFrameworkComponentsFromRendererMap, defaultRendererMap } from 'components/renderers';
import FileLinkRenderer from 'components/renderers/FileLinkRenderer';
import SyncariSystemFieldHeader from 'components/renderers/SyncariSystemFieldHeader';
import RouteSpin from 'components/RouteSpin';
import StatBlock from 'components/StatBlock';
import {
  TableFilterButton,
  TableFilterDisclosureButton,
  TableFilterProvider,
  TableFilterProviderRef,
} from 'components/TableFilters';
import { TranslatedText } from 'components/typography';
import { useCursorPagination } from 'hooks/pagination';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { updateAdhocEntityFilter } from 'store/data-studio';
import { useEntityRecordsList } from 'store/data-studio/hooks';
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

import { ColumnItem, useConfigurableColumns } from '../schema-studio/ConfigureTableColumnsModal';
import BatchModal, { BatchOperationMode, BatchProgressMenu } from './Batch';
import BatchHistoryDrawer, { BatchHistoryDrawerMode } from './Batch/HistoryDrawer';
import DataScore from './DataScore';
import FilterPanel from './FilterPanel';
import FiltersListDrawer from './Filters/FiltersListDrawer';
import UpdateFilterDrawer from './Filters/UpdateFilterDrawer';
import { useFilterFromQueryString, useUserConfiguredColumnsForEntity } from './hooks';
import { RecordActionCell } from './RecordActionCell';

import './DataStudio.less';

const NEW_FILTER_KEY = 'new';

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

const getAgColumnsConfig = (
  metadata: Record<string, EnhancedFieldMetadata>,
  columns: string[],
  frameworkComponents: typeof defaultRendererMap
): ColDef[] => [
  ...columns
    .filter((column) => column in metadata && metadata[column].canDisplay)
    .map((column) => {
      const meta = metadata[column];

      return {
        headerName: meta.label,
        colId: column,
        field: isSyncariEntitySpecialField(column) ? column : `values.${column}`,
        headerComponentFramework: meta.isSystem ? SyncariSystemFieldHeader : undefined,
        cellRenderer:
          !blackListLabelRenderer.includes(meta.label) && meta.label in frameworkComponents
            ? meta.label
            : meta.dataType in frameworkComponents
            ? (meta.dataType as string)
            : undefined,
      };
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
}

interface DataStudioGridProps {
  entityId: string;
  appliedFilter?: EntityFilter | Partial<EntityFilter>;
}

const DataStudioGrid = ({ entityId, appliedFilter }: DataStudioGridProps) => {
  const [showSaveFilterPanel, setShowSaveFilterPanel] = useState<boolean>();
  const [filterListOpen, setFilterListOpen] = useState(false);
  const [showingBatchModalForOperation, setShowingBatchModalForOperation] = useState(BatchOperationMode.NONE);
  const [batchHistoryDrawerMode, setBatchHistoryDrawerMode] = useState(BatchHistoryDrawerMode.CLOSED);
  const [showDataScore, setShowDataScore] = useState(false);
  const { tn, tc } = useI18nContext();

  const dispatch = useDispatch<ThunkDispatch<any, any, AnyAction>>();
  const entityName = useEnhancedSelector((state) => selectEntityById(state, entityId))?.displayName;

  const {
    cursor,
    pageSize,
    setPageSize,
    direction,
    resetPagination,
    onRequestNextPage,
    onRequestPrevPage,
  } = useCursorPagination();

  const [filterInEditor, setFilterInEditor] = useState<DataStudioGridProps['appliedFilter']>(appliedFilter);

  // this gives us a handle on the Filter component
  const filterControlRef = useRef<FilterRef | null>(null);
  const filterPanelRef = useRef<TableFilterProviderRef | null>(null);
  const navigate = useNavigate();

  // this is used to naiively track when we're on the first page - this will be
  // unreliable with client configured sorting
  const [firstPageStartCursor, setFirstPageStartCursor] = useState<string | null>();

  // Retrieves filtered records from backend
  const { error, loading, refetch, data, metadata } = useEntityRecordsList({
    entityId,
    count: pageSize,
    cursor,
    direction,
    filter: appliedFilter ? appliedFilter.criteria : undefined,
  });
  const previousError = usePreviousValue(error);

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
    (columns: ColumnItem[]) => updateColumns(columns.filter((col) => col.columnName !== actionColDef.colId)),
    [updateColumns]
  );

  const [ConfigureColumnsModal, modalProps, { toggleModal: showConfigureColumnsModal }] = useConfigurableColumns({
    allAvailableColumns: allColumns,
    labelForColumn: (column) => (column in metadata ? metadata[column].label : column),
    onRequestSave: onColumnsUpdated,
    dataTypeForColumn: (column) => (metadata && column in metadata ? metadata[column]?.dataType : ''),
  });

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
      const updatedFilterId = ObjectID.generate();
      const updatedFilter = {
        ...draftFilter,
        id: updatedFilterId,
      };

      if (updatedFilter.criteria) {
        // Save current filter in localstorage to persist after returning
        // from viewing a record
        localStorage.setItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID, updatedFilterId);
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
    localStorage.removeItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID);
    // force a full refresh because Filter maintains it's own internal state
    // that we can't easily reset
    navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: '' })).then(() => {
      navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }), { replace: true });
    });
  }, [entityId, navigate]);

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
      navigate(makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId }));
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
          onRequestRecordDetail={onRequestNavigateToRecordDetail}
        />
      ),
    });
  }, [entityId, onRequestNavigateToRecordDetail]);

  const getBatchMenuItems = () => {
    return joinGroupsWithDividers([
      [
        <MenuItem key={DataStudioGridAction.CONFIGURE_COLUMNS}>
          <TranslatedText text="configure_columns_tooltip" />
        </MenuItem>,
      ],
      [
        <Can key={DataStudioGridAction.DELETE_DATA} permission={AllPermissions.WRITE_DATA_STUDIO}>
          <MenuItem disabled={!Boolean(appliedFilter)}>
            <Tooltip title={!Boolean(appliedFilter) && tn('apply_filter_record_actions', { action: tc('delete') })}>
              <TranslatedText text="batch_delete_entity_records" />
            </Tooltip>
          </MenuItem>
        </Can>,
        <MenuItem key={DataStudioGridAction.SHOW_DELETE_HISTORY}>
          <TranslatedText text="show_batch_delete_history" />
        </MenuItem>,
      ],
      [
        <Can key={DataStudioGridAction.UPDATE_DATA} permission={AllPermissions.WRITE_DATA_STUDIO}>
          <MenuItem disabled={!Boolean(appliedFilter)}>
            <Tooltip title={!Boolean(appliedFilter) && tn('apply_filter_record_actions', { action: tc('update') })}>
              <TranslatedText text="batch_update_entity_records" />
            </Tooltip>
          </MenuItem>
        </Can>,
        <MenuItem key={DataStudioGridAction.SHOW_UPDATE_HISTORY}>
          <TranslatedText text="show_batch_update_history" />
        </MenuItem>,
      ],
      [
        <Can key={DataStudioGridAction.PURGE_DATA} permission={AllPermissions.WRITE_DATA_STUDIO}>
          <MenuItem disabled={(appliedFilter?.criteria?.predicates?.length || 0) > 0}>
            <Tooltip
              title={(appliedFilter?.criteria?.predicates?.length || 0) > 0 ? tn('purge_tooltip_text') : undefined}>
              <TranslatedText text="purge_entity" />
            </Tooltip>
          </MenuItem>
        </Can>,
      ],
    ]);
  };

  const columnDefs = useMemo(() => {
    if (!data) {
      return [];
    }
    const selectedColumns = allColumns.filter((col) => col.isSelected).map((col) => col.columnName);

    const columnsList = Array.from(new Set([...selectedColumns, 'actions']));
    return getAgColumnsConfig(metadata, columnsList, agFrameworkComponents);
  }, [data, metadata, allColumns, agFrameworkComponents]);

  const datasource = data?.records || [];
  const pageInfo = data?.pageInfo
    ? {
        ...data.pageInfo,
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

  return (
    <TableFilterProvider ref={filterPanelRef}>
      <Stack fill>
        {pageInfo?.message && <InfoBox message={pageInfo.message} type="info" />}

        <Stack spacing="xs">
          <div className="data-studio-meta-row">
            <HStack spacing="lg">
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
              {pageInfo && (
                <StatBlock
                  value={pageInfo.filteredCount}
                  label={<TranslatedText text="records" args={{ count: pageInfo.filteredCount }} />}
                />
              )}
            </HStack>
            <HStack spacing="xs">
              <BatchProgressMenu entityId={entityId} />
              <HStack spacing="xs">
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
                <TableFilterDisclosureButton
                  activeFilterCount={appliedFilter?.criteria?.predicates?.length || 0}
                  onRequestClear={resetFilters}
                  size="small"
                />
              </HStack>
              <TableFilterButton aria-label={tn('export')} onClick={exportData} size="small">
                <ExportIcon />
                {tn('export')}
              </TableFilterButton>

              <Link
                className="data-studio-link"
                to={makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
                  entityId,
                  graphVersion: AppConstants.GRAPH_STATUS.APPROVED,
                })}>
                {tn('view_pipeline')}
              </Link>

              <KebabMenu<DataStudioGridAction>
                menuItems={getBatchMenuItems()}
                onClick={({ key }) => {
                  switch (key) {
                    case DataStudioGridAction.CONFIGURE_COLUMNS:
                      showConfigureColumnsModal();
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
                    default:
                      throw new UnreachableCaseError(key);
                  }
                }}
                size="small"
              />
            </HStack>
          </div>
          <FilterPanel
            key={filterInEditor?.id || NEW_FILTER_KEY}
            entityId={entityId}
            error={error?.errorMessage}
            fieldValues={fieldValues}
            filter={appliedFilter}
            filterControlRef={filterControlRef}
            onApplyFilter={onApplyFilter}
            onRequestRefreshData={refetch}
            onRequestResetFilter={resetFilters}
            onRequestSaveFilter={(updatedFilter) => {
              setShowSaveFilterPanel(true);
              setFilterInEditor(updatedFilter);
            }}
            onRequestShowFiltersList={() => setFilterListOpen(true)}
          />
        </Stack>
        <AgTable
          // only virtualize columns if the data set is large enough to impact performance
          suppressColumnVirtualisation={columnDefs.length < MAX_AMOUNT_OF_NON_VIRTUALIZED_COLUMNS}
          columnDefs={columnDefs}
          frameworkComponents={agFrameworkComponents}
          loading={loading}
          rowData={datasource}
          enableCellTextSelection
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
            />
          }
        />
      </Stack>

      <ConfigureColumnsModal {...modalProps} />

      {entityId && (
        <FiltersListDrawer
          entityId={entityId}
          onRequestClose={() => setFilterListOpen(false)}
          visible={filterListOpen}
        />
      )}

      {showSaveFilterPanel && filterInEditor && (
        <UpdateFilterDrawer
          key={filterInEditor.id || NEW_FILTER_KEY}
          entityId={entityId}
          filter={filterInEditor}
          onSaveFilter={setFilterInEditor}
          onRequestClose={() => setShowSaveFilterPanel(false)}
        />
      )}

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
    </TableFilterProvider>
  );
};

type DataStudioGridContainerProps = RouteComponentProps & DataStudioGridProps;

/* wrapper around DataStudioGrid in order to load filter/datascore factor filter
 * from the querystring before rendering and fetching records
 */
const DataStudioGridContainer = ({ entityId }: DataStudioGridContainerProps) => {
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
    />
  );
};

export default DataStudioGridContainer;
