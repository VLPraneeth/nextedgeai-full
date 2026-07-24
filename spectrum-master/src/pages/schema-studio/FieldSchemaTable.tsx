import { RouteComponentProps } from '@reach/router';
import { GridApi, GridReadyEvent } from 'ag-grid-community';
import produce from 'immer';
import { capitalize, upperFirst } from 'lodash';
import { Moment } from 'moment-timezone';
import * as React from 'react';
import { useEffect, useMemo, useReducer, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import { ReactComponent as GearIcon } from 'assets/icons/settings.svg';
import AgTable, { DefaultPageSizeOptions, PageBasedPagination, ResizeColumnsCondition } from 'components/AgTable';
import { IconButton } from 'components/Button';
import Checkbox, { CheckboxChangeEvent } from 'components/Checkbox';
import DateRangePicker from 'components/DateRangePicker';
import { DataTypeIcons } from 'components/FieldTypeBadge';
import { HStack, Stack } from 'components/layout';
import { agFrameworkComponentsFromRendererMap, defaultRendererMap } from 'components/renderers';
import SelectInput from 'components/SelectInput';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useClientPagination from 'hooks/useClientPagination';
import { useDownloadCSVHandler } from 'hooks/useDownloadCSVHandler';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useConnectorCanEditSchema } from 'store/connectors';
import { selectEntitySchema, selectEntitySchemaStatus } from 'store/schema/selectors';
import { createDraftForEntityAndRefreshConnector } from 'store/schema/thunks';
import { Connector } from 'store/schema/types';
import { selectSchemaStudioFieldColumnsPrefs } from 'store/user/selectors';
import { updateSchemaStudioPreferences as updateSchemaStudioPreferencesAction } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { RootState } from '../../reducers';
import { ColumnItem, mergeConfiguredAndDefaultColumns, useConfigurableColumns } from './ConfigureTableColumnsModal';
import { generateSchemaCSVData } from './FieldSchemaTable.utils';
import { all, datePropIsBetween, prop, stringPropIsIn, stringPropsContains } from './filterUtils';
import { useDiscardEntityDraftWithConfirm, useFieldSchema } from './SchemaStudio.hooks';
import DisplayNameCellRenderer from './TableCellRenderers/DisplayNameCellRenderer';
import TableFilters, { TableFilter, TableFilterButton } from './TableFilters';
import { EntitySchemaResponse, FieldModel, SchemaVersion } from './types';

import './FieldSchemaTable.less';

// Hidden user preference column only for syncari entities
const HIDDEN_SYNCARI_COLUMNS = ['isCreateonly'];

const tn = tNamespaced('SchemaStudio.FieldsTable');
const translateDatatype = tNamespaced('DataTypes');

const displayNameOrApiNameContains = stringPropsContains(['displayName', 'apiName']);
const lastUpdatedDateIsBetween = datePropIsBetween('lastUpdated');
const datatypeIsIn = stringPropIsIn('dataType');
const applyFlags = (flags: { [index: string]: boolean }) => (item: FieldModel) =>
  Object.entries(flags)
    .filter(([_, bool]) => Boolean(bool))
    .every(([flag]) => Boolean(prop(flag)(item)));

const rendererMap = {
  ...defaultRendererMap,
  displayName: DisplayNameCellRenderer,
};

const agFrameworkComponents = agFrameworkComponentsFromRendererMap(rendererMap);

const getAgColumnsConfig = (metadata: EntitySchemaMeta, columns: ColumnItem[]) => {
  return columns
    .map((column) => metadata[column.columnName as EntitySchemaMetaKey])
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

interface InitialState {
  pageSize: number;
  filters: {
    datatypes: string[];
    dates: { from: null | Moment; to: null | Moment };
    flags: {
      isRequired: boolean;
    };
  };
}

const defaultFilters = {
  datatypes: [],
  dates: { from: null, to: null },
  flags: {
    isRequired: false,
  },
};

const initialState: InitialState = {
  pageSize: DefaultPageSizeOptions[1],
  filters: defaultFilters,
};

const SET_PAGE_SIZE = 'pagesize/set';
const UPDATE_SEARCH_INPUT = 'search/update';
const SHOW_FILTERS = 'filters/show';
const UPDATE_FLAG = 'filters/flag/update';
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
    case UPDATE_FLAG:
      draft.filters.flags[action.payload.key] = action.payload.value;
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
      return;
    case RESET_STATE:
      return initialState;

    default:
      return;
  }
});

type EntitySchemaMeta = EntitySchemaResponse['meta'];
type EntitySchemaMetaKey = keyof FieldModel;

const DEFAULT_COLUMNS_LIST: EntitySchemaMetaKey[] = [
  'displayName',
  'dataType',
  'description',
  'lastUpdated',
  'status',
  'id',
];

const connector = connect(
  (state: RootState, props: FieldSchemaTableProps) => ({
    entitySchema: selectEntitySchema(state, props),
    entitySchemaStatus: selectEntitySchemaStatus(state, props),
    fieldColumnsPref: selectSchemaStudioFieldColumnsPrefs(state),
  }),
  (dispatch) =>
    bindActionCreators(
      {
        updateSchemaStudioPreferences: updateSchemaStudioPreferencesAction,
      },
      dispatch
    )
);

interface FieldSchemaTableProps extends RouteComponentProps<{ entityApiName: string; version: string }> {
  entityId?: string;
  connectorId?: string;
  hasDraft: boolean;
  schemaVersion?: SchemaVersion;
  handleEntityChange: (entityApiName: string, version: SchemaVersion) => void;
  onSelectFieldRow: (entity: FieldModel) => void;
  showNewField?: () => void;
  selectedField?: FieldModel;
  isSyncariConnector?: boolean;
  onGridReady?: (event: GridReadyEvent) => void;
  synapse?: Connector;
}

type TablePropsFromRedux = ConnectedProps<typeof connector>;

const FieldSchemaTable = ({
  schemaVersion = 'published',
  entityId,
  connectorId,
  hasDraft,
  onSelectFieldRow,
  handleEntityChange,
  entityApiName,
  selectedField,
  entitySchema,
  entitySchemaStatus,
  showNewField,
  isSyncariConnector,
  fieldColumnsPref,
  updateSchemaStudioPreferences,
  onGridReady,
  synapse,
}: FieldSchemaTableProps & TablePropsFromRedux) => {
  const [gridApi, setGridApi] = useState<GridApi>();
  const [state, dispatch] = useReducer(reducer, initialState);

  const [creatingDraft, setCreatingDraft] = useState(false);
  const [discardingDraft, setDiscardingDraft] = useState(false);

  const { pageSize, filters, searchInput, showingFilters } = state;
  const { dates, flags, datatypes } = filters;
  const { startDate, endDate } = dates;

  const { entities } = useEnhancedSelector((state) => state.entity);

  const currentEntity = entities?.find((entity) => entity.id === entityId);

  const { userHasPermission } = useUserHasPermission();
  const { handleDownloadCSV } = useDownloadCSVHandler();

  const enhancedDispatch = useEnhancedDispatch();

  const { meta: entityMeta } = entitySchema || {};

  const discardEntityDraft = useDiscardEntityDraftWithConfirm(() => {
    setDiscardingDraft(true);
  });

  useEffect(() => {
    if (selectedField) {
      // this covers a case when we re-navigate to the entity table and we still
      // have one selected, ensure the row is highlighted properly
      gridApi?.getRowNode(selectedField.id)?.setSelected(true, true);
    } else {
      // when we deselect an entity, ensure the row is deselected
      gridApi?.deselectAll();
    }
  }, [gridApi, selectedField]);

  const DATA_TYPES = useMemo(
    () =>
      Object.keys(DataTypeIcons).map((datatype) => {
        const translatedDataTypeStr = translateDatatype(datatype) as string;
        return { value: datatype, label: translatedDataTypeStr };
      }),
    []
  );

  const handleSearchInputChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    dispatch({
      type: UPDATE_SEARCH_INPUT,
      payload: {
        value: evt.currentTarget.value,
      },
    });
  };

  const handleFlagUpdate = (evt: CheckboxChangeEvent) => {
    dispatch({
      type: UPDATE_FLAG,
      payload: {
        key: evt.target.name,
        value: evt.target.checked,
      },
    });
  };

  const handleDatatypesUpdate = (selection: string[]) => {
    dispatch({
      type: UPDATE_FILTER,
      payload: {
        key: 'datatypes',
        value: selection,
      },
    });
  };

  const activeFilterCount = [startDate && endDate, datatypes.length, ...Object.values(flags)].filter(Boolean).length;

  const handleUpdateTableColumns = (fieldColumns: ColumnItem[]) => {
    updateSchemaStudioPreferences?.({ allFieldColumns: fieldColumns });
  };

  const defaultColumnsList = useMemo(() => {
    return (entityMeta ? (Object.keys(entityMeta) as (keyof FieldModel)[]) : DEFAULT_COLUMNS_LIST).map(
      (columnName) => ({
        columnName,
        isSelected: true,
      })
    );
  }, [entityMeta]);

  // merge down to rendered columns here
  const columnsList = useMemo(() => {
    if (Array.isArray(fieldColumnsPref) && fieldColumnsPref.length > 0) {
      const columns = mergeConfiguredAndDefaultColumns(fieldColumnsPref, defaultColumnsList);

      return isSyncariConnector ? columns.filter((col) => !HIDDEN_SYNCARI_COLUMNS.includes(col.columnName)) : columns;
    }
    return defaultColumnsList;
  }, [fieldColumnsPref, defaultColumnsList, isSyncariConnector]);

  const columnDefs = useMemo(() => {
    const selectedColumns = columnsList.filter((col) => col.isSelected);
    return entityMeta ? getAgColumnsConfig(entityMeta, selectedColumns) : [];
  }, [columnsList, entityMeta]);

  const data = useMemo(() => {
    if (entitySchema?.data?.length) {
      return entitySchema.data
        .map((datum) => {
          // only show published
          if (schemaVersion === 'published') {
            return datum.published?.fields;
          }

          // if showing draft,
          // merges published/draft data down for the table
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
  }, [entitySchema, schemaVersion]);

  const filteredData = useMemo(() => {
    return data.filter(
      all<FieldModel>(
        displayNameOrApiNameContains(searchInput),
        lastUpdatedDateIsBetween(startDate, endDate),
        datatypeIsIn(datatypes),
        applyFlags(flags)
      )
    );
  }, [data, searchInput, startDate, endDate, datatypes, flags]);

  const [{ pageInfo, records }, { getNextPage, getPreviousPage, goToPage }] = useClientPagination(
    filteredData,
    pageSize
  );

  // Update the selectedField if the selectedField values change
  const currentField = records?.find(({ id }) => id === selectedField?.id);
  useEffect(() => {
    if (currentField) {
      onSelectFieldRow(currentField);
    }
  }, [currentField, onSelectFieldRow]);

  useEffect(() => {
    goToPage(0);
  }, [goToPage, searchInput, startDate, endDate, datatypes, flags]);

  const [ConfigureColumnsModal, modalProps, { toggleModal }] = useConfigurableColumns({
    onRequestSave: handleUpdateTableColumns,
    allAvailableColumns: columnsList,
    labelForColumn: (columnName) =>
      entityMeta && columnName in entityMeta ? entityMeta[columnName as keyof FieldModel]?.value : columnName,
    dataTypeForColumn: (columnName) =>
      entityMeta && columnName in entityMeta ? entityMeta[columnName as keyof FieldModel]?.dataType : '',
  });

  const canEditSchema = useConnectorCanEditSchema(synapse?.id);

  const { canCreateSchemaField } = useFieldSchema(synapse);

  const canCreateField = schemaVersion === 'draft' && canEditSchema && canCreateSchemaField;
  // We are disabling create / delete draft options when the entity is in a readonly state.
  // @ts-ignore
  const isReadonly = currentEntity?.readonly ?? false;

  return (
    <>
      <Stack fill>
        <TableFilters
          activeFilterCount={activeFilterCount}
          searchInputValue={searchInput}
          onSearchInputChange={handleSearchInputChange}
          isShowingFilters={showingFilters}
          renderActions={
            <HStack>
              {canCreateField && (
                <TableFilterButton
                  permission={AllPermissions.WRITE_STUDIO}
                  type="primary"
                  icon="plus"
                  onClick={showNewField}
                  aria-label={tn('new_field_btn_aria_label')}>
                  {tn('new_field_btn')}
                </TableFilterButton>
              )}
              {schemaVersion === 'draft' && hasDraft && (
                <TableFilterButton
                  permission={AllPermissions.WRITE_STUDIO}
                  type="primary"
                  loading={discardingDraft}
                  onClick={() => {
                    // This will always be true but needed for type validation
                    if (connectorId && entityId && entityApiName) {
                      discardEntityDraft({ connectorId, entityId, entityName: upperFirst(entityApiName) })
                        .then(() => {
                          setDiscardingDraft(false);
                          // Navigate to the newly created draft
                          handleEntityChange(entityApiName, 'published');
                        })
                        .catch(() => {
                          setDiscardingDraft(false);
                        });
                    }
                  }}
                  aria-label={tc('delete_draft')}>
                  {tc('delete_draft')}
                </TableFilterButton>
              )}
              {schemaVersion === 'published' && (
                <TableFilterButton
                  disabled={entitySchemaStatus === AppConstants.FETCH_STATUS.LOADING || filteredData.length === 0}
                  onClick={() => {
                    const csvName = `${synapse?.name} - ${capitalize(entityApiName)}.csv`;
                    const csvData = generateSchemaCSVData(filteredData, columnDefs);
                    handleDownloadCSV(csvData, csvName);
                  }}>
                  {tc('export_as_csv')}
                </TableFilterButton>
              )}
              {schemaVersion === 'published' && !hasDraft && canEditSchema && (
                <TableFilterButton
                  type="primary"
                  permission={AllPermissions.WRITE_STUDIO}
                  // @ts-ignore
                  isReadonly={isReadonly}
                  loading={creatingDraft}
                  onClick={() => {
                    // This will always be true but needed for type validation
                    if (connectorId && entityId && entityApiName) {
                      setCreatingDraft(true);
                      enhancedDispatch(createDraftForEntityAndRefreshConnector({ connectorId, entityId }))
                        .then(() => {
                          setCreatingDraft(false);
                          // Navigate to the newly created draft
                          handleEntityChange(entityApiName, 'draft');
                        })
                        .catch(() => {
                          setCreatingDraft(false);
                        });
                    }
                  }}>
                  {tn('new_draft')}
                </TableFilterButton>
              )}
              <IconButton
                icon={GearIcon}
                onClick={toggleModal}
                disabled={!userHasPermission(AllPermissions.WRITE_STUDIO)}
              />
            </HStack>
          }
          onRequestShowFilters={() => dispatch({ type: SHOW_FILTERS })}
          onRequestClearFilters={() => dispatch({ type: RESET_FILTERS })}>
          <TableFilter title={tn('filters.last_updated') as string}>
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
          <TableFilter title={tn('filters.data_types') as string}>
            <SelectInput
              mode="multiple"
              options={DATA_TYPES}
              value={datatypes}
              onChange={(value) => handleDatatypesUpdate((value as unknown) as string[])}
            />
          </TableFilter>
          <TableFilter title={tn('filters.flags') as string}>
            <Checkbox name="isRequired" onChange={handleFlagUpdate} checked={flags.isRequired}>
              {tn('required')}
            </Checkbox>
          </TableFilter>
        </TableFilters>

        <AgTable
          key={entityId}
          columnDefs={columnDefs}
          frameworkComponents={agFrameworkComponents}
          loading={entitySchemaStatus === AppConstants.FETCH_STATUS.LOADING}
          rowData={records}
          rowSelection="single"
          pagination={false}
          suppressCellSelection
          enableCellTextSelection
          sizeColumnsToFit={columnDefs.length < 13 ? ResizeColumnsCondition.WHEN_NARROWER : undefined}
          onGridReady={(evt) => setGridApi(evt.api)}
          rowClassRules={{
            'schema-studio-row': () => true,
            'schema-studio-field-row': () => true,
            'has-draft': (params) => params.data.hasDraft,
          }}
          onRowSelected={(evt) => {
            if (evt.node.isSelected()) {
              onSelectFieldRow(evt.data);
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

export default connector(FieldSchemaTable);
