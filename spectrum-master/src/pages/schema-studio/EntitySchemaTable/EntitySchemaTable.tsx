import { NavigateFn, RouteComponentProps } from '@reach/router';
import { GridApi } from 'ag-grid-community';
import Icon from 'antd/lib/icon';
import produce from 'immer';
import * as React from 'react';
import { useEffect, useMemo, useReducer, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import { ReactComponent as OpenArrowIcon } from 'assets/icons/open-arrow.svg';
import { ReactComponent as GearIcon } from 'assets/icons/settings.svg';
import AgTable, { DefaultPageSizeOptions, PageBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import Button from 'components/Button';
import DateRangePicker from 'components/DateRangePicker';
import { HStack, Stack } from 'components/layout';
import { agFrameworkComponentsFromRendererMap, defaultRendererMap } from 'components/renderers';
import ActionsCell from 'components/renderers/ActionsCellRenderer';
import { useEnhancedDispatch } from 'hooks/redux';
import useClientPagination from 'hooks/useClientPagination';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useConnectorCanEditSchema, useSynapseRefreshingStatus } from 'store/connectors';
import { selectConnectorSchema, selectConnectorSchemaStatus } from 'store/schema/selectors';
import {
  createDraftForEntityAndRefreshConnector,
  showPublishConfirmationModalForEntityId as showPublishConfirmationModalForEntityIdAction,
} from 'store/schema/thunks';
import { selectSchemaStudioEntityColumnsPrefs } from 'store/user/selectors';
import { updateSchemaStudioPreferences as updateSchemaStudioPreferencesAction } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { wrapIcon } from 'utils/IconUtils';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { RootState } from '../../../reducers';
import { ColumnItem, mergeConfiguredAndDefaultColumns, useConfigurableColumns } from '../ConfigureTableColumnsModal';
import { all, datePropIsBetween, stringPropsContains } from '../filterUtils';
import { useDiscardEntityDraftWithConfirm } from '../SchemaStudio.hooks';
import DisplayNameCellRenderer from '../TableCellRenderers/DisplayNameCellRenderer';
import TableFilters, { TableFilter, TableFilterButton } from '../TableFilters';
import { ConnectorSchemaResponse, EntityModel, SchemaVersion } from '../types';
import EntitySchemaTableOptions from './EntitySchemaTableOptions';

import './EntitySchemaTable.less';

const tc = tNamespaced('Common');
const tn = tNamespaced('SchemaStudio.EntityTable');

interface EntityActionsCellProps {
  connectorId: string;
  entityApiName: string;
  record: EntityModel;
  navigate?: NavigateFn;
  schemaVersion: SchemaVersion;
}

const actionsCellConnector = connect(null, (dispatch) =>
  bindActionCreators(
    {
      showPublishConfirmationModalForEntityId: showPublishConfirmationModalForEntityIdAction,
    },
    dispatch
  )
);

type ActionsCellPropsFromRedux = ConnectedProps<typeof actionsCellConnector>;

const EntityActionsCell = ({
  connectorId,
  entityApiName,
  record,
  navigate,
  schemaVersion,
  showPublishConfirmationModalForEntityId,
}: EntityActionsCellProps & ActionsCellPropsFromRedux) => {
  const dispatch = useEnhancedDispatch();
  const { userHasPermission } = useUserHasPermission();

  const makePublishEntityHandler = (entityId: string) => () => {
    showPublishConfirmationModalForEntityId(entityId, connectorId);
  };

  const discardDraftForEntity = useDiscardEntityDraftWithConfirm();

  const { isRefreshing } = useSynapseRefreshingStatus(connectorId);

  const makeCreateDraftForEntityHandler = (entityId: string) => () => {
    dispatch(
      createDraftForEntityAndRefreshConnector({
        connectorId,
        entityId,
      })
    );
  };

  const actionTooltip = isRefreshing ? tc('unavailable_during_refresh') : undefined;
  const permissionsTooltip = !userHasPermission(AllPermissions.WRITE_STUDIO) ? tc('permission_error') : undefined;

  // @ts-ignore
  const canEditSchema = useConnectorCanEditSchema(connectorId) && !record.readonly;

  const fieldsButton = (
    <Button
      type="default"
      size="small"
      onClick={(evt: React.SyntheticEvent) => {
        const version = record.hasDraft ? 'draft' : schemaVersion;
        navigate?.(makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE_ENTITY, { connectorId, entityApiName, version }));
      }}
      className="show-entity-fields-btn">
      <Icon component={wrapIcon(OpenArrowIcon)} />
      {tn('fields')}
    </Button>
  );

  if (!canEditSchema && !record.hasDraft) {
    return fieldsButton;
  }

  return (
    <ActionsCell
      size="small"
      menuItems={[
        record.hasDraft && {
          key: 'publish',
          ariaLabel: tn('actions.publish'),
          label: tn('actions.publish'),
          disabled: isRefreshing || !userHasPermission(AllPermissions.WRITE_STUDIO),
          tooltipTitle: actionTooltip ?? permissionsTooltip,
          onSelect: makePublishEntityHandler(record.id),
        },
        record.hasDraft && {
          key: 'delete',
          label: tn('actions.delete'),
          ariaLabel: tn('actions.delete'),
          disabled: isRefreshing || !userHasPermission(AllPermissions.WRITE_STUDIO),
          tooltipTitle: actionTooltip ?? permissionsTooltip,
          onSelect: () => discardDraftForEntity({ entityId: record.id, connectorId, entityName: record.displayName }),
        },
        !record.hasDraft && {
          key: 'create',
          label: tn('actions.create'),
          ariaLabel: tn('actions.create'),
          disabled: isRefreshing || !userHasPermission(AllPermissions.WRITE_STUDIO),
          tooltipTitle: actionTooltip ?? permissionsTooltip,
          onSelect: makeCreateDraftForEntityHandler(record.id),
        },
      ]}>
      {fieldsButton}
    </ActionsCell>
  );
};

const ConnectedEntityActionsCell = actionsCellConnector(EntityActionsCell);

const makeEntityActionsCellRenderer = (connectorId: string, schemaVersion: SchemaVersion, navigate?: NavigateFn) => (
  _: string,
  record: EntityModel
) => {
  return (
    <ConnectedEntityActionsCell
      connectorId={connectorId}
      entityApiName={record.apiName}
      record={record}
      navigate={navigate}
      schemaVersion={schemaVersion}
    />
  );
};

const defaultFilters = {
  dates: {
    from: null,
    to: null,
  },
  sourceIds: [],
  destinationIds: [],
};

const initialState = {
  pageSize: DefaultPageSizeOptions[1],
  searchInput: '',
  showingFilters: false,
  filters: defaultFilters,
};

const SET_PAGE_SIZE = 'pagesize/set';
const UPDATE_SEARCH_INPUT = 'search/update';
const SHOW_FILTERS = 'filters/show';
const UPDATE_FILTER = 'filters/update';
const RESET_FILTERS = 'filters/reset';
const RESET_STATE = 'state/reset';

const reducer = produce((draft, action) => {
  switch (action.type) {
    case SET_PAGE_SIZE:
      draft.pageSize = action.payload.pageSize;
      break;
    case UPDATE_SEARCH_INPUT:
      draft.searchInput = action.payload.value;
      break;
    case UPDATE_FILTER:
      draft.filters[action.payload.key] = action.payload.value;
      break;
    case SHOW_FILTERS:
      draft.showingFilters = true;
      break;
    case RESET_FILTERS:
      draft.showingFilters = false;
      draft.filters = defaultFilters;
      break;
    case RESET_STATE:
      return initialState;
    default:
      return;
  }
});

const displayNameOrApiNameContains = stringPropsContains(['displayName', 'apiName']);
const lastUpdatedDateIsBetween = datePropIsBetween('lastUpdated');

const rendererMap = {
  ...defaultRendererMap,
  displayName: DisplayNameCellRenderer,
  // render a count of the references
  references: (references: null | string | string[]) => (Array.isArray(references) ? references.length : 0),
};

const agFrameworkComponents = agFrameworkComponentsFromRendererMap(rendererMap);

type ConnectorSchemaMeta = ConnectorSchemaResponse['meta'];
type ConnectorSchemaMetaKey = keyof EntityModel;

// when the user hasn't configured their columns, use these
const DEFAULT_COLUMNS_LIST: ConnectorSchemaMetaKey[] = [
  'displayName',
  'description',
  'status',
  'lastUpdated',
  'apiName',
  'deletedRecords',
  'references',
  'tags',
  'totalFields',
  'id',
];

const getAgColumnsConfig = (metadata: ConnectorSchemaMeta, columns: ColumnItem[]) => {
  return columns
    .map((column) => metadata[column.columnName as ConnectorSchemaMetaKey])
    .filter(Boolean)
    .map((meta) => ({
      headerName: meta.value,
      field: meta.label,
      cellRenderer:
        meta.label in agFrameworkComponents
          ? (meta.label as string)
          : meta.dataType in agFrameworkComponents
          ? (meta.dataType as string)
          : undefined,
    }));
};

const mapState = (state: RootState, props: EntitySchemaTableProps) => ({
  connectorSchema: selectConnectorSchema(state, props),
  connectorSchemaLoading: selectConnectorSchemaStatus(state, props),
  entityColumnsPref: selectSchemaStudioEntityColumnsPrefs(state),
});

const connector = connect(mapState, (dispatch) =>
  bindActionCreators(
    {
      updateSchemaStudioPreferences: updateSchemaStudioPreferencesAction,
    },
    dispatch
  )
);

const rowClassRules = {
  'schema-studio-row': () => true,
  'schema-studio-entity-row': () => true,
  'has-draft': (params: { data: EntityModel }) => params.data.hasDraft,
};

interface EntitySchemaTableProps extends RouteComponentProps {
  connectorId?: string;
  schemaVersion?: SchemaVersion;
  isSyncariConnector?: boolean;
  onSelectEntityRow: (entity: EntityModel) => void;
  selectedEntity?: EntityModel;
  showNewEntity?: () => void;
  showConfigureEntityModal?: () => void;
}

type TablePropsFromRedux = ConnectedProps<typeof connector>;

const EntitySchemaTable = ({
  navigate,
  connectorId,
  schemaVersion = 'published',
  isSyncariConnector = false,
  onSelectEntityRow,
  selectedEntity,
  connectorSchema,
  connectorSchemaLoading,
  showNewEntity,
  updateSchemaStudioPreferences,
  entityColumnsPref,
  showConfigureEntityModal,
}: EntitySchemaTableProps & TablePropsFromRedux) => {
  const [gridApi, setGridApi] = useState<GridApi>();
  const [state, dispatch] = useReducer(reducer, initialState);
  const { pageSize, searchInput, showingFilters, filters } = state;

  const { meta: connectorMeta } = connectorSchema || {};

  const defaultColumnsList = useMemo(() => {
    return (connectorMeta ? (Object.keys(connectorMeta) as (keyof EntityModel)[]) : DEFAULT_COLUMNS_LIST).map(
      (columnName) => ({
        columnName,
        isSelected: true,
      })
    );
  }, [connectorMeta]);

  const columnsList = useMemo(() => {
    return Array.isArray(entityColumnsPref) && entityColumnsPref.length > 0
      ? mergeConfiguredAndDefaultColumns(entityColumnsPref, defaultColumnsList)
      : defaultColumnsList;
  }, [entityColumnsPref, defaultColumnsList]);

  const agFrameworkComponents = useMemo(() => {
    if (!connectorId) {
      return {};
    }

    return agFrameworkComponentsFromRendererMap({
      ...rendererMap,
      actions: makeEntityActionsCellRenderer(connectorId, schemaVersion, navigate),
    });
  }, [connectorId, navigate, schemaVersion]);

  const columnDefs = useMemo(() => {
    const selectedColumns = columnsList.filter((col) => col.isSelected);
    return connectorMeta
      ? [
          ...getAgColumnsConfig(connectorMeta, selectedColumns),
          {
            headerName: 'Actions',
            colId: 'actions',
            field: 'hasDraft',
            cellRenderer: 'actions',
            pinned: 'right',
            minWidth: 140,
            maxWidth: 140,
          },
        ]
      : [];
  }, [columnsList, connectorMeta]);

  const data = useMemo(() => {
    if (connectorSchema?.data?.length) {
      return connectorSchema.data
        .map((datum) => {
          const draftFields = datum.draft?.fields || {};
          const publishedFields = datum.published?.fields || {};

          return {
            ...publishedFields,
            ...draftFields,
            hasDraft: Boolean(datum.draft),
          };
        })
        .filter(Boolean);
    }

    return [];
  }, [connectorSchema]);

  useEffect(() => {
    if (selectedEntity) {
      // this covers a case when we re-navigate to the entity table and we still
      // have one selected, ensure the row is highlighted properly
      gridApi?.getRowNode(selectedEntity.id)?.setSelected(true, true);
    } else {
      // when we deselect an entity, ensure the row is deselected
      gridApi?.deselectAll();
    }
  }, [gridApi, selectedEntity]);

  const { dates, sourceIds, destinationIds } = filters;
  const { startDate, endDate } = dates;

  const activeFilterCount = [startDate && endDate, sourceIds.length, destinationIds.length].filter(Boolean).length;

  const handleUpdateTableColumns = (entityColumns: ColumnItem[]) => {
    updateSchemaStudioPreferences?.({ allEntityColumns: entityColumns });
  };

  const [ConfigureColumnsModal, modalProps, { toggleModal: toggleColumnsConfigureModal }] = useConfigurableColumns({
    onRequestSave: handleUpdateTableColumns,
    allAvailableColumns: columnsList,
    labelForColumn: (columnName) =>
      connectorMeta && columnName in connectorMeta ? connectorMeta[columnName as keyof EntityModel]?.value : columnName,
    dataTypeForColumn: (columnName) =>
      connectorMeta && columnName in connectorMeta ? connectorMeta[columnName as keyof EntityModel]?.dataType : '',
  });

  const onRequestNewEntity = () => {
    showNewEntity?.();
  };
  const onRequestConfigureEntity = () => {
    showConfigureEntityModal?.();
  };

  const filteredData: EntityModel[] = useMemo(() => {
    return data.filter(
      all<EntityModel>(displayNameOrApiNameContains(searchInput), lastUpdatedDateIsBetween(startDate, endDate))
    );
  }, [data, searchInput, startDate, endDate]);

  const [{ pageInfo, records }, { getNextPage, getPreviousPage, goToPage }] = useClientPagination(
    filteredData,
    pageSize
  );

  useEffect(() => {
    goToPage(0);
  }, [goToPage, searchInput, startDate, endDate]);

  return (
    <>
      <Stack fill>
        <TableFilters
          activeFilterCount={activeFilterCount}
          searchInputValue={searchInput}
          onSearchInputChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
            dispatch({
              type: UPDATE_SEARCH_INPUT,
              payload: {
                value: evt?.currentTarget?.value,
              },
            });
          }}
          isShowingFilters={showingFilters}
          onRequestShowFilters={() => dispatch({ type: SHOW_FILTERS })}
          onRequestClearFilters={() => {
            dispatch({ type: RESET_FILTERS });
          }}
          renderActions={
            <HStack>
              {isSyncariConnector ? (
                <TableFilterButton
                  type="primary"
                  icon="plus"
                  onClick={onRequestNewEntity}
                  permission={AllPermissions.WRITE_STUDIO}>
                  {tn('actions.new_entity')}
                </TableFilterButton>
              ) : (
                <TableFilterButton
                  type="primary"
                  onClick={onRequestConfigureEntity}
                  permission={AllPermissions.WRITE_STUDIO}>
                  <Icon
                    style={{
                      fontSize: '1.5em',
                      position: 'relative',
                      top: '0.05em',
                    }}
                    component={wrapIcon(GearIcon)}
                  />
                  {tn('actions.configure_entity')}
                </TableFilterButton>
              )}
              <EntitySchemaTableOptions
                isSyncariConnector={isSyncariConnector}
                connectorId={connectorId}
                toggleColumnsConfigureModal={toggleColumnsConfigureModal}
              />
            </HStack>
          }>
          <TableFilter title={tn('filters.last_updated')}>
            <DateRangePicker
              startDate={filters.dates.startDate}
              endDate={filters.dates.endDate}
              onChange={(startDate, endDate) =>
                dispatch({
                  type: UPDATE_FILTER,
                  payload: {
                    key: 'dates',
                    value: {
                      startDate,
                      endDate,
                    },
                  },
                })
              }
            />
          </TableFilter>
        </TableFilters>

        <AgTable
          key={connectorId}
          columnDefs={columnDefs}
          frameworkComponents={agFrameworkComponents}
          loading={connectorSchemaLoading === AppConstants.FETCH_STATUS.LOADING}
          rowData={records}
          rowSelection="single"
          pagination={false}
          sizeColumnsToFit={columnDefs.length < 13 ? ResizeColumnsCondition.WHEN_NARROWER : undefined}
          disableRowSelectionForCells={['actions']}
          suppressCellSelection
          enableCellTextSelection
          onGridReady={(evt) => setGridApi(evt.api)}
          rowClassRules={rowClassRules}
          onRowSelected={(evt) => {
            if (evt.node.isSelected()) {
              onSelectEntityRow(evt.data);
            }
          }}
          pagerComponent={
            <PageBasedPagination
              pageInfo={pageInfo}
              allowPageSizeChange
              pageSize={pageSize}
              onPageSizeChange={(pageSize) => dispatch({ type: SET_PAGE_SIZE, payload: { pageSize } })}
              onRequestNextPage={getNextPage}
              onRequestPreviousPage={getPreviousPage}
            />
          }
        />
      </Stack>
      <ConfigureColumnsModal {...modalProps} />
    </>
  );
};

export default connector(EntitySchemaTable);
