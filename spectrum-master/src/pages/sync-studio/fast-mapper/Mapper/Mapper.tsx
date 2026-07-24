//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import {
  CellClassParams,
  CellKeyDownEvent,
  ColDef,
  GridApi,
  GridReadyEvent,
  RowEditingStoppedEvent,
} from 'ag-grid-community';
import { Icon } from 'antd';
import cx from 'classnames';
import { cloneDeep } from 'lodash';
import { Dispatch, SetStateAction, useCallback, useEffect, useMemo, useState } from 'react';
import { useDebouncedCallback } from 'use-debounce';

import PipelineIcon from 'assets/icons/pipeline.svg';
import AgTable from 'components/AgTable';
import Button from 'components/Button';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import InlineSvg from 'components/icons/InlineSvg';
import { HStack, Stack } from 'components/layout';
import { TableSearchFilter } from 'components/TableFilters';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { getEntityFields } from 'store/entity/thunks';
import { selectEditMappingsResponse, selectSaveMappingsResponse } from 'store/fast-mapper/selectors';
import { resetBrowseMappingModal } from 'store/fast-mapper/slice';
import { deleteMappings, getMappings } from 'store/fast-mapper/thunks';
import { Mapping } from 'store/fast-mapper/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { getColumns as getAddMappingColumns } from '../AddMapping/AddMapping.config';
import { AddMappingContextProvider } from '../AddMapping/AddMapping.context';
import { useAutoMap } from '../AutoMap/AutoMap.hooks';
import { AutoMapModal } from '../AutoMap/AutoMapModal';
import { BrowseMappingContextProvider } from '../BrowseMapping';
import { getColumns as getBrowseMappingColumns } from '../BrowseMapping/BrowseMapping.config';
import { CreateFieldDropdown } from '../CreateFieldDropdown';
import { DeleteSelectedModal } from '../DeleteSelectedModal';
import { convertToDirections } from '../FastMapper.util';
import { FastMapperMode, useFastMapper } from '../FastMapperModal';
import { EditedMapping } from '../types';
import { ADD_ROW_DEBOUNCE_DELAY, DefaultColDef } from './Mapper.constants';
import { useMapper } from './Mapper.hooks';
import { MapperFields, ValidColumnDefFieldId } from './Mapper.types';

import './Mapper.scss';

const tn = tNamespaced('AddMapping');
const tAm = tNamespaced('AutoMap');

export interface MapperProps {
  className?: string;
  header?: JSX.Element;
  initialMappings?: Mapping[];
  mode: FastMapperMode;
  onChange?: (values: Mapping[]) => void;
  onFilter?: (value: string) => void;
  setEditedValues?: Dispatch<SetStateAction<EditedMapping[]>>;
  setGridApi?: Dispatch<SetStateAction<GridApi | undefined>>;
  setGridUpdatedTrigger?: Dispatch<SetStateAction<boolean>>;
  setValues: Dispatch<SetStateAction<Mapping[]>>;
  switchToAdd?: () => void;
}

export var Mapper = ({
  className,
  header,
  initialMappings,
  mode,
  onChange,
  onFilter,
  setEditedValues,
  setGridApi: setParentGridApi,
  setGridUpdatedTrigger: setParentGridUpdatedTrigger,
  setValues,
  switchToAdd,
}: MapperProps) => {
  const {
    state,
    dispatch: contextDispatch,
    visible,
    getSerializedValues,
    tableDataHandlers: { addRow },
  } = useMapper(mode, undefined, undefined, initialMappings);

  const { entityId } = useFastMapper();
  const { values, editedValues } = state;
  const [focusedField, setFocusedField] = useState<ValidColumnDefFieldId>();

  const [gridApi, setGridApi] = useState<GridApi>();
  const [rowAdding, setRowAdding] = useState(false);
  const [rowsSelected, setRowsSelected] = useState(false);
  const [showDeleteSelectedModal, setShowDeleteSelectedModal] = useState(false);
  const [gridUpdatedTrigger, setGridUpdatedTrigger] = useState(false);

  const reduxDispatch = useEnhancedDispatch();

  const saveMappingsResponse = useSelector(selectSaveMappingsResponse);
  const editMappingsResponse = useSelector(selectEditMappingsResponse);

  const previousSaveMappingsResponse = usePreviousValue(saveMappingsResponse);
  const previousEditMappingsResponse = usePreviousValue(editMappingsResponse);

  const columns: ColDef[] = useMemo(
    // cloneDeep: see below tableData comment
    () =>
      cloneDeep(
        mode === FastMapperMode.ADD ? getAddMappingColumns(focusedField) : getBrowseMappingColumns(initialMappings)
      ),
    [focusedField, initialMappings, mode]
  );

  // We use a cloned object before passing it to AG-Grid since
  // immer freezes the state object and AG-Grid mutates it.
  // Sort data to show rows with errors first, but maintain position when errors are fixed
  const tableData = useMemo(() => {
    const clonedValues = cloneDeep(values);

    // Only sort if there are any errors
    const hasAnyErrors = clonedValues.some((item) => Boolean(item.errorMessage));

    if (hasAnyErrors) {
      return clonedValues.sort((a, b) => {
        const aHasError = Boolean(a.errorMessage);
        const bHasError = Boolean(b.errorMessage);

        if (aHasError && !bHasError) {
          return -1;
        }
        if (!aHasError && bHasError) {
          return 1;
        }
        return 0;
      });
    }

    // If no errors, return original order
    return clonedValues;
  }, [values]);

  /**
   * Handlers
   */

  const [handleAddNewRow] = useDebouncedCallback(
    () => {
      setRowAdding(true);
      addRow({ externalUpdate: true });
      setFocusedField(MapperFields.SYNAPSE_ID);
    },
    ADD_ROW_DEBOUNCE_DELAY,
    { trailing: false, leading: true }
  );

  const handleGridReady = (params: GridReadyEvent) => {
    setGridApi(params.api);
    if (mode === FastMapperMode.ADD && values?.length === 1) {
      // TODO: check this -> we are not auto editing on add
      handleStartCellEdit(params.api, MapperFields.SYNAPSE_ID, values.length - 1);
    }
  };

  const handleRemoveSelected = useCallback(() => {
    if (gridApi) {
      const selectedRows = gridApi.getSelectedRows();
      reduxDispatch(resetBrowseMappingModal());
      reduxDispatch(
        deleteMappings({
          entityId,
          mappings: selectedRows.map(({ id, synapseFieldId, syncariFieldId, syncDirectionId }) => {
            return {
              id,
              directions: convertToDirections(syncDirectionId),
              synapseFieldId,
              syncariFieldId,
            };
          }),
        })
      )
        .unwrap()
        .then(() => {
          reduxDispatch(getMappings({ entityId }));
        });

      setShowDeleteSelectedModal(false);
      setRowsSelected(false);
    }
  }, [entityId, gridApi, reduxDispatch]);

  const handleStartCellEdit = useCallback((gridApi: GridApi, columnName: string, rowIndex: number) => {
    if (!gridApi) {
      return;
    }
    gridApi.setFocusedCell(0, columnName);
    gridApi.startEditingCell({
      rowIndex,
      colKey: columnName,
    });
  }, []);

  const handleRowDataUpdated = useCallback(() => {
    const error = mode === FastMapperMode.ADD ? saveMappingsResponse?.error : editMappingsResponse?.error;
    const currentResponse = mode === FastMapperMode.ADD ? saveMappingsResponse : editMappingsResponse;
    const previousResponse = mode === FastMapperMode.ADD ? previousSaveMappingsResponse : previousEditMappingsResponse;

    if (gridApi) {
      if (rowAdding) {
        setRowAdding(false);
        handleStartCellEdit(gridApi, MapperFields.SYNAPSE_ID, values.length - 1);
      }
      if (previousResponse !== currentResponse && error?.length) {
        gridApi.refreshCells({ force: true, columns: [MapperFields.ERROR_MESSAGE] });
      }
    }
  }, [
    editMappingsResponse,
    gridApi,
    handleStartCellEdit,
    mode,
    previousEditMappingsResponse,
    previousSaveMappingsResponse,
    rowAdding,
    saveMappingsResponse,
    values.length,
  ]);

  const handleRowEditingStopped = useCallback(
    ({ data }: RowEditingStoppedEvent) => {
      const { errorMessage, ...mappingData } = data;
      if (
        mode === FastMapperMode.ADD &&
        mappingData.synapseId &&
        mappingData.synapseEntityId &&
        mappingData.synapseFieldId &&
        mappingData.syncDirectionId &&
        mappingData.syncariFieldId &&
        values?.[values.length - 1]?.id === mappingData.id
      ) {
        handleAddNewRow();
      }
    },
    [handleAddNewRow, mode, values]
  );

  const handleRowSelected = () => {
    setGridUpdatedTrigger((prev) => !prev);
    gridApi && setRowsSelected(gridApi.getSelectedRows().length > 0);
  };

  const handleSortChanged = () => {
    setGridUpdatedTrigger((prev) => !prev);
  };

  /**
   * Effects
   */

  // Pass the context values up to the parent.
  useEffect(() => {
    setValues(values);
  }, [setValues, values]);

  // Pass the grid API up to the parent.
  useEffect(() => {
    setParentGridApi?.(gridApi);
  }, [gridApi, setParentGridApi]);

  // Past the grid update trigger to the parent.
  useEffect(() => {
    setParentGridUpdatedTrigger?.(gridUpdatedTrigger);
  }, [gridUpdatedTrigger, setParentGridUpdatedTrigger]);

  useEffect(() => {
    if (mode === FastMapperMode.BROWSE && setEditedValues && editedValues) {
      setEditedValues(editedValues);
    }
  }, [setEditedValues, editedValues, mode]);

  useEffect(() => {
    reduxDispatch(getEntityFields(entityId));
    return () => {
      // Cancel any ongoing delayed effects when its unmounting
      setRowAdding(false);
      setGridApi(undefined);
    };
  }, [reduxDispatch, entityId]);

  useEffect(() => {
    if (mode === FastMapperMode.ADD && values.length === 0 && visible) {
      addRow({ externalUpdate: true });
    }
  }, [addRow, mode, values.length, visible]);

  useEffect(() => {
    onChange?.(values);
  }, [values, onChange, getSerializedValues]);

  const handleKeyDown = (event: CellKeyDownEvent, columns: ReturnType<typeof getAddMappingColumns>) => {
    const keyboardEvent = event.event as KeyboardEvent;
    const isTab = keyboardEvent.key === 'Tab';
    const isShift = keyboardEvent.shiftKey;

    if (isTab) {
      const currentIndex = columns.findIndex((col) => col.field === event.column.getColId());

      let nextIndex = isShift ? currentIndex - 1 : currentIndex + 1;

      if (nextIndex >= columns.length) {
        nextIndex = 0;
      } else if (nextIndex < 0) {
        nextIndex = columns.length - 1;
      }

      const nextColumn = columns[nextIndex];
      if (nextColumn && nextColumn.editable) {
        const cellElement = document.querySelector(
          `.ag-row[row-index="${event.rowIndex}"] .ag-cell[col-id="${nextColumn.field}"] .ant-select-arrow`
        ) as HTMLSpanElement;
        if (cellElement) {
          cellElement.click();
        }
      }
    }
  };

  const { setVisible: showAutoMap } = useAutoMap();

  /**
   * Render
   */
  const content = (
    <Stack className={cx('mapper', className)}>
      {tableData?.length ? (
        <>
          <Stack>
            <div className="mapper__header">
              {mode === FastMapperMode.BROWSE && onFilter && (
                <TableSearchFilter className="mapper__table-search" onChange={(evt) => onFilter(evt.target.value)} />
              )}
              {mode === FastMapperMode.ADD && (
                <Button
                  type="primary"
                  className="mapper__header-button mapper__header-button mapper__header-button--second"
                  onClick={() => showAutoMap(true)}>
                  {tAm('auto_map')}
                </Button>
              )}
              <Button
                className={cx(
                  'mapper__header-button mapper__header-button--first',
                  mode === FastMapperMode.ADD && 'mapper__header-button--first-add'
                )}
                onClick={() => {
                  setShowDeleteSelectedModal(true);
                }}
                disabled={!rowsSelected}>
                {tn('delete_selected')}
              </Button>
              {header}
            </div>
            <Stack className={cx('mapper__table-container', className)}>
              <AgTable
                onGridReady={handleGridReady}
                defaultColDef={DefaultColDef}
                columnDefs={columns}
                rowData={tableData}
                onRowDataUpdated={handleRowDataUpdated}
                editType="fullRow"
                rowSelection="multiple"
                suppressRowClickSelection
                stopEditingWhenGridLosesFocus
                singleClickEdit
                onCellKeyDown={(event) => handleKeyDown(event, columns)}
                onCellFocused={(event) => {
                  const column = event.column?.getColId() as ValidColumnDefFieldId;
                  if (column) {
                    setFocusedField(column);
                  }
                }}
                onRowEditingStopped={handleRowEditingStopped}
                onRowSelected={handleRowSelected}
                onSortChanged={handleSortChanged}
                rowClassRules={{
                  'mapper__row--edited': (params: CellClassParams) => params.data.edited,
                  'mapper__row--error': (params: CellClassParams) => params.data.errorMessage,
                }}
              />
              {mode === FastMapperMode.ADD && (
                <HStack justify="end">
                  <Button className="mapper__button--primary" type="primary" onClick={handleAddNewRow}>
                    <Icon type="plus" />
                    {tc('add')}
                  </Button>
                </HStack>
              )}
            </Stack>
          </Stack>
          <DeleteSelectedModal
            visible={showDeleteSelectedModal}
            onOk={handleRemoveSelected}
            onCancel={() => {
              setShowDeleteSelectedModal(false);
            }}
            gridApi={gridApi}
          />
          <CreateFieldDropdown />
          <AutoMapModal />
        </>
      ) : (
        <EmptyGraphPanel
          className="mapper__empty"
          onActionClick={() => switchToAdd?.()}
          actionPermission={AllPermissions.WRITE_STUDIO}
          actionButtonType="primary"
          panelIcon={<InlineSvg title={tn('add_mapping')} src={PipelineIcon} />}
          actionText={tn('add_mapping')}>
          <TranslatedText beDangerous namespace="MappingBrowser" text="create_mapping" />
        </EmptyGraphPanel>
      )}
    </Stack>
  );

  if (mode === FastMapperMode.ADD) {
    return (
      <AddMappingContextProvider value={{ state, dispatch: contextDispatch }}>{content}</AddMappingContextProvider>
    );
  } else if (mode === FastMapperMode.BROWSE) {
    return (
      <BrowseMappingContextProvider value={{ state, dispatch: contextDispatch }}>
        {content}
      </BrowseMappingContextProvider>
    );
  }
  return null;
};

export default Mapper;
