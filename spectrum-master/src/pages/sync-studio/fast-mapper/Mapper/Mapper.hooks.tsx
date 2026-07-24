//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import ObjectID from 'bson-objectid';
import produce from 'immer';
import { find, sortBy } from 'lodash';
import { RefObject, useCallback, useEffect, useMemo, useReducer, useState } from 'react';

import { makeFieldOption } from 'components/inputs/FieldOptions';
import {
  useEnhancedDispatch as useDispatch,
  useEnhancedSelector,
  useEnhancedSelector as useSelector,
} from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { selectUserConnectorsForDisplay } from 'store/connectors/selectors';
import { selectConnectorEntitiesOnly, selectConnectorFields } from 'store/entity/selectors';
import { getConnectorEntities, getEntityFields } from 'store/entity/thunks';
import { Entity } from 'store/entity/types';
import { selectEditMappingsResponse, selectMappings, selectSaveMappingsResponse } from 'store/fast-mapper/selectors';
import { CreateFieldModalMode, showCreateField } from 'store/fast-mapper/slice';
import { Mapping } from 'store/fast-mapper/types';
import AppConstants from 'utils/AppConstants';
import { createUniqueEntityTitle } from 'utils/FieldUtil';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { useAddMappingContext, addMappingReducer, makeInitialAddMappingState, AddMappingActions } from '../AddMapping';
import { AddSyncariField } from '../AddSyncariField';
import {
  BrowseMappingActions,
  browseMappingReducer,
  makeInitialBrowseMappingState,
  useBrowseMappingContext,
} from '../BrowseMapping';
import { CreateFieldState } from '../CreateFieldDropdown';
import { FieldType, getCompatibleDestinationTypes, getCompatibleSourceTypes } from '../FastMapper.util';
import { FastMapperMode, useFastMapper } from '../FastMapperModal';
import { EntityFieldOption } from '../types';
import { HEADER_KEY_PREFIX } from './Mapper.constants';
import { ValidColumnDefFieldId, MapperFields, ChangeHandler } from './Mapper.types';
import {
  getDirectionOptions,
  makeDirectionOption,
  makeSynapseEntityOption,
  makeSynapseOption,
  makeDropdownHeader,
  getSerializedValues,
} from './Mapper.utils';

import './Mapper.scss';

const tn = tNamespaced('AddMapping');
const { FETCH_STATUS } = AppConstants;

export const useEditableCell = (fieldId: ValidColumnDefFieldId, id: string, mode: FastMapperMode, value?: string) => {
  const [selectedValue, setSelectedValue] = useState(value);

  const { state: addMappingState } = useAddMappingContext();
  const { state: browseMappingState } = useBrowseMappingContext();

  const state = mode === FastMapperMode.ADD ? addMappingState : browseMappingState;

  const row = useMemo(() => find(state.values, { id }), [state.values, id]);

  const { picklistChangeHandlers, picklistData, loading } = useMapper(mode, row, fieldId);

  const mappings = useSelector(selectMappings);

  const connectorIdToMetadataMap = useConnectorIdToMetadataMap();

  const { fieldData, changeHandler } = useMemo(() => {
    // TODO: Fix fieldData type
    let fieldData: Record<string, any> = {};
    let changeHandler: ChangeHandler = () => {};

    if (fieldId) {
      fieldData = picklistData[fieldId];
      changeHandler = picklistChangeHandlers[fieldId];
    }

    return {
      fieldData,
      changeHandler,
    };
  }, [fieldId, picklistChangeHandlers, picklistData]);

  const createSortedOptions = useCallback(
    (fieldIdPropName: string, disableIncompatableTypes: boolean = false) => {
      let lookupType: FieldType = '' as FieldType;
      let isLookupTypeRHS = true;

      if (disableIncompatableTypes) {
        if (fieldIdPropName === 'synapseFieldId') {
          const newSyncariField = state.newSyncariFields?.find((field) => field.id === row?.syncariFieldId);
          const existingSyncariField = picklistData.syncariFieldId?.find((field) => field?.id === row?.syncariFieldId);

          lookupType = (newSyncariField?.dataType ?? existingSyncariField?.dataType) as FieldType;
        }

        if (fieldIdPropName === 'syncariFieldId') {
          const synapseField = picklistData.synapseFieldId?.find((field) => field.id === row?.synapseFieldId);

          lookupType = synapseField?.dataType as FieldType;
          isLookupTypeRHS = false;
        }
      }

      const compatableTypes = isLookupTypeRHS
        ? getCompatibleSourceTypes(lookupType)
        : getCompatibleDestinationTypes(lookupType);

      const enabledFieldOptions: JSX.Element[] = [];
      const disabledFieldOptions: JSX.Element[] = [];

      fieldData.forEach((data: any) => {
        const disabled =
          disableIncompatableTypes && compatableTypes.length > 0 ? !compatableTypes.includes(data?.dataType) : false;
        const disabledTooltip = disabled
          ? tc('disabled_datatype_tooltip', {
              source: isLookupTypeRHS ? data?.dataType : lookupType,
              destination: isLookupTypeRHS ? lookupType : data?.dataType,
            })
          : '';

        const option = makeFieldOption({ ...data, disabled, disabledTooltip });

        if (!disabled) {
          enabledFieldOptions.push(option);
        } else {
          disabledFieldOptions.push(option);
        }
      });

      const fieldOptions = [...enabledFieldOptions, ...disabledFieldOptions];

      let unmappedOptions: React.ReactElement[] = [];
      let mappedOptions: React.ReactElement[] = [];

      fieldOptions.forEach((option: JSX.Element) => {
        // Ignore invalid options
        if (!option.key) {
          return;
        }
        // @ts-ignore
        const isExistingMap = mappings?.find((map) => map[fieldIdPropName] === option.key);

        // @ts-ignore
        const isInProgressMap = state.values?.find((map) => map[fieldIdPropName] === option.key);

        if (isExistingMap || isInProgressMap) {
          mappedOptions.push(option);
        } else {
          unmappedOptions.push(option);
        }
      });

      return [
        makeDropdownHeader(
          `${HEADER_KEY_PREFIX}-unmapped-${fieldId}`,
          tn('unmapped', { count: unmappedOptions.length })
        ),
        ...unmappedOptions,
        makeDropdownHeader(`${HEADER_KEY_PREFIX}-mapped-${fieldId}`, tn('mapped', { count: mappedOptions.length })),
        ...mappedOptions,
      ];
    },
    [
      fieldData,
      fieldId,
      mappings,
      picklistData.synapseFieldId,
      picklistData.syncariFieldId,
      row?.synapseFieldId,
      row?.syncariFieldId,
      state.newSyncariFields,
      state.values,
    ]
  );

  const { options, stickyItems } = useMemo(() => {
    let loading = false;
    let options: React.ReactElement[] = [];
    let stickyItems: React.ReactElement | null = null;

    if (fieldId === MapperFields.SYNAPSE_ID) {
      options = fieldData?.map(makeSynapseOption(connectorIdToMetadataMap));
    } else if (fieldId === MapperFields.SYNAPSE_ENTITY_ID) {
      options = fieldData?.map(makeSynapseEntityOption);
    } else if (fieldId === MapperFields.SYNAPSE_FIELD_ID) {
      if (!!fieldData.length) {
        options = createSortedOptions('synapseFieldId', true);
      }
    } else if (fieldId === MapperFields.SYNC_DIRECTION_ID) {
      options = fieldData?.map(makeDirectionOption);
    } else if (fieldId === MapperFields.SYNCARI_ENTITY_FIELD_ID) {
      if (!!fieldData.length) {
        options = createSortedOptions('syncariFieldId', true);
      }

      // Always show the 'Create Field' button, regardless of if there
      // is any valid data to display in the dropdown.
      stickyItems = mode === FastMapperMode.ADD ? <AddSyncariField id={id} /> : null;
    }
    return { options, loading, stickyItems };
  }, [connectorIdToMetadataMap, createSortedOptions, fieldData, fieldId, id, mode]);

  const onChange = useCallback(
    (value: string) => {
      setSelectedValue(value);
      if ([MapperFields.SYNAPSE_FIELD_ID, MapperFields.SYNCARI_ENTITY_FIELD_ID].includes(fieldId) && row) {
        const field = find(fieldData, { id: value });
        if (field) {
          changeHandler(id, value, {
            displayName: field.displayName,
            readOnly: field.readOnly,
            dataType: field.dataType,
            apiName: field.apiName,
          });
        }
      } else {
        id && changeHandler(id, value);
      }
    },
    [changeHandler, fieldData, fieldId, id, row]
  );

  return {
    options,
    loading,
    changeHandler,
    onChange,
    selectedValue,
    setSelectedValue,
    stickyItems,
    row,
  };
};

export const useMapper = (
  mode: FastMapperMode,
  row?: Mapping,
  fieldId?: ValidColumnDefFieldId,
  initialMappings?: Mapping[]
) => {
  const reduxDispatch = useDispatch();

  const { visible, entityId } = useFastMapper();
  const { dispatch: addMappingContextDispatch, state: addMappingContextState } = useAddMappingContext();
  const { dispatch: browseMappingContextDispatch, state: browseMappingContextState } = useBrowseMappingContext();

  const connectors = useEnhancedSelector(selectUserConnectorsForDisplay);
  const connectorEntities = useEnhancedSelector(selectConnectorEntitiesOnly);
  const connectorFields = useEnhancedSelector(selectConnectorFields);
  const saveMappingsResponse = useEnhancedSelector(selectSaveMappingsResponse);
  const editMappingsResponse = useEnhancedSelector(selectEditMappingsResponse);

  const reducer = mode === FastMapperMode.ADD ? addMappingReducer : browseMappingReducer;
  const MapperActions = mode === FastMapperMode.ADD ? AddMappingActions : BrowseMappingActions;

  // Use the context dispatch if triggered from the cells
  const contextDispatch = mode === FastMapperMode.ADD ? addMappingContextDispatch : browseMappingContextDispatch;
  const contextState = mode === FastMapperMode.ADD ? addMappingContextState : browseMappingContextState;

  const initalState = useMemo(() => {
    if (mode === FastMapperMode.ADD) {
      return makeInitialAddMappingState();
    } else {
      return makeInitialBrowseMappingState(initialMappings);
    }
  }, [initialMappings, mode]);

  // Local reducer state and dispatch. Mainly needed for values
  // in the cell that were not commited yet
  const [state, dispatch] = useReducer(reducer, initalState);

  const previousSaveMappingsResponse = usePreviousValue(saveMappingsResponse);
  const previousEditMappingsResponse = usePreviousValue(editMappingsResponse);

  /* This is messing up some stuff; need to debug
  const prevVisible = usePreviousValue(visible);

  useEffect(() => {
    if (prevVisible !== visible) {
      dispatch({
        type: MapperActions.RESET,
      });
    }
  }, [visible, prevVisible, MapperActions.RESET]);
  */

  useEffect(() => {
    const error = mode === FastMapperMode.ADD ? saveMappingsResponse?.error : editMappingsResponse?.error;
    const currentResponse = mode === FastMapperMode.ADD ? saveMappingsResponse : editMappingsResponse;
    const previousResponse = mode === FastMapperMode.ADD ? previousSaveMappingsResponse : previousEditMappingsResponse;

    if (previousResponse !== currentResponse && error?.length) {
      dispatch({
        type: MapperActions.SET_FAILED_MAPPINGS,
        error,
      });
    }
  }, [
    MapperActions.SET_FAILED_MAPPINGS,
    editMappingsResponse,
    mode,
    previousEditMappingsResponse,
    previousSaveMappingsResponse,
    saveMappingsResponse,
  ]);

  // map for cell picklist
  const picklistData = useMemo(() => {
    const connectorFieldOption =
      row &&
      connectorFields?.[row.synapseEntityId]?.data?.map((field) => {
        return produce<EntityFieldOption>(field, (draft) => {
          draft.title = createUniqueEntityTitle(field.displayName, field.apiName);
        });
      });

    const syncariFieldOption =
      row &&
      connectorFields?.[entityId]?.data
        ?.map((field) => {
          return produce<EntityFieldOption>(field, (draft) => {
            draft.title = createUniqueEntityTitle(field.displayName, field.apiName);
          });
        })
        .concat(contextState.newSyncariFields);

    const selectedConnector = connectors?.find((connector) => connector.id === row?.synapseId);

    let connectorEntitiesOption: Entity[] | undefined;

    if (row) {
      const options = connectorEntities?.[row.synapseId]?.data;

      // Only expose the timeTicker entity when the user selects Syncari as the synapse.
      if (selectedConnector?.name.toLowerCase() === 'syncari') {
        connectorEntitiesOption = options?.filter((option) => option.apiName === 'timeTicker');
      } else {
        connectorEntitiesOption = options;
      }
    }

    return {
      [MapperFields.SYNAPSE_ID]: sortBy(connectors, 'name'),
      [MapperFields.SYNAPSE_ENTITY_ID]: sortBy(connectorEntitiesOption, 'displayName'),
      [MapperFields.SYNAPSE_FIELD_ID]: sortBy(connectorFieldOption, 'displayName'),
      [MapperFields.SYNC_DIRECTION_ID]: sortBy(getDirectionOptions(row), 'displayName'),
      [MapperFields.SYNCARI_ENTITY_FIELD_ID]: sortBy(syncariFieldOption, 'displayName'),
    };
  }, [connectorEntities, connectorFields, connectors, entityId, row, contextState]);

  const clearFields = useCallback(
    (id: string, fields: string[]) => {
      fields.forEach((field) => contextDispatch({ type: field, id, value: '' }));
    },
    [contextDispatch]
  );

  const addNewCustomField = useCallback(
    ({ apiName, displayName, dataType, isRequired, isMultivalued }: CreateFieldState) => {
      if (mode === FastMapperMode.ADD) {
        contextDispatch({
          type: AddMappingActions.ADD_FIELD,
          id: row?.id,
          payload: {
            dataType,
            apiName,
            displayName,
            isRequired,
            isMultivalued,
            createNewSyncariField: true,
            id: ObjectID.generate(),
            title: createUniqueEntityTitle(displayName, apiName),
          },
        });
      }
    },
    [contextDispatch, mode, row?.id]
  );

  // map for cell picklist on selection change handlers
  const picklistChangeHandlers: Record<
    | MapperFields.SYNAPSE_ID
    | MapperFields.SYNAPSE_ENTITY_ID
    | MapperFields.SYNAPSE_FIELD_ID
    | MapperFields.SYNC_DIRECTION_ID
    | MapperFields.SYNCARI_ENTITY_FIELD_ID,
    ChangeHandler
  > = useMemo(() => {
    return {
      [MapperFields.SYNAPSE_ID]: (id, value) => {
        contextDispatch({ type: MapperActions.SET_SYNAPSE, id, value });
        clearFields(id, [
          MapperActions.SET_SYNAPSE_ENTITY,
          MapperActions.SET_SYNAPSE_FIELD,
          MapperActions.SET_SYNC_DIRECTION,
          MapperActions.SET_SYNCARI_ENTITY_FIELD,
        ]);
        if (!connectorEntities[value] && row) {
          reduxDispatch(getConnectorEntities(value, false, true));
        }
      },
      [MapperFields.SYNAPSE_ENTITY_ID]: (id, value) => {
        contextDispatch({ type: MapperActions.SET_SYNAPSE_ENTITY, id, value });
        clearFields(id, [
          MapperActions.SET_SYNAPSE_FIELD,
          MapperActions.SET_SYNC_DIRECTION,
          MapperActions.SET_SYNCARI_ENTITY_FIELD,
        ]);
        reduxDispatch(getEntityFields(value));
      },
      [MapperFields.SYNAPSE_FIELD_ID]: (id, value, options) => {
        contextDispatch({ type: MapperActions.SET_SYNAPSE_FIELD, id, value, ...options });
        clearFields(id, [MapperActions.SET_SYNC_DIRECTION, MapperActions.SET_SYNCARI_ENTITY_FIELD]);
      },
      [MapperFields.SYNC_DIRECTION_ID]: (id, value) => {
        contextDispatch({ type: MapperActions.SET_SYNC_DIRECTION, id, value });
      },
      [MapperFields.SYNCARI_ENTITY_FIELD_ID]: (id, value, options) => {
        contextDispatch({ type: MapperActions.SET_SYNCARI_ENTITY_FIELD, id, value, ...options });
      },
    };
  }, [
    MapperActions.SET_SYNAPSE,
    MapperActions.SET_SYNAPSE_ENTITY,
    MapperActions.SET_SYNAPSE_FIELD,
    MapperActions.SET_SYNCARI_ENTITY_FIELD,
    MapperActions.SET_SYNC_DIRECTION,
    clearFields,
    connectorEntities,
    contextDispatch,
    reduxDispatch,
    row,
  ]);

  const tableDataHandlers = useMemo(() => {
    // Different dispatches are used if the action was dispatched from inside
    // the table (internal) or outside the table (external).
    return {
      addRow: ({ externalUpdate, autopopulate }: { externalUpdate: boolean; autopopulate?: boolean }) => {
        if (mode === FastMapperMode.ADD) {
          const action = { type: AddMappingActions.ADD, autopopulate };

          (externalUpdate ? dispatch : contextDispatch)(action);
        }
      },
      addFullMapping: (mapping: any) => {
        contextDispatch({
          ...mapping,
          type: AddMappingActions.ADD_FULL_MAPPING,
        });
      },
      addSyncariField: (mapping: any) => {
        contextDispatch({
          payload: mapping,
          id: mapping.id,
          type: AddMappingActions.ADD_FIELD,
        });
      },
      deleteRow: ({ id, externalUpdate }: { id: string; externalUpdate: boolean }) => {
        if (mode === FastMapperMode.ADD) {
          const action = { type: AddMappingActions.DELETE, id };

          (externalUpdate ? dispatch : contextDispatch)(action);
        }
      },
      resetRow: ({ id }: { id: string }) => {
        if (mode === FastMapperMode.BROWSE) {
          const action = { type: BrowseMappingActions.RESET_ROW, id };

          contextDispatch(action);
        }
      },
    };
  }, [contextDispatch, mode]);

  const loading = useMemo(() => {
    if (row) {
      if (fieldId === MapperFields.SYNAPSE_ENTITY_ID) {
        return Boolean(row.synapseId && connectorEntities?.[row.synapseId]?.status === FETCH_STATUS.LOADING);
      } else if (fieldId === MapperFields.SYNAPSE_FIELD_ID) {
        return Boolean(row && connectorFields?.[row.synapseEntityId]?.status === FETCH_STATUS.LOADING);
      } else if (fieldId === MapperFields.SYNCARI_ENTITY_FIELD_ID) {
        return Boolean(row && connectorFields?.[entityId]?.status === FETCH_STATUS.LOADING);
      }
    }
    return false;
  }, [fieldId, row, connectorEntities, connectorFields, entityId]);

  const editNewField = useCallback(
    (ref: RefObject<HTMLDivElement>) => {
      const parentBoundingBox = ref.current?.getBoundingClientRect();
      const data = find(contextState.newSyncariFields, { id: row?.syncariFieldId });

      if (row && data && parentBoundingBox) {
        const { displayName, apiName, dataType, isRequired, isMultivalued } = data;

        reduxDispatch(
          showCreateField({
            id: row.id,
            visible: true,
            mode: CreateFieldModalMode.EDIT,
            position: {
              top: parentBoundingBox.top + parentBoundingBox.height + 8,
              left: parentBoundingBox.left - (ref.current?.offsetLeft ?? 0),
              width: parentBoundingBox?.width + (ref.current?.offsetLeft ?? 0) * 2,
            },
            data: {
              displayName,
              apiName,
              dataType,
              isRequired,
              isMultivalued,
            },
          })
        );
      }
    },
    [reduxDispatch, row, contextState]
  );

  return {
    visible,
    state,
    getSerializedValues,
    dispatch,
    entityId,

    fieldData: fieldId && picklistData[fieldId],
    picklistData,
    picklistOnChange: fieldId && picklistChangeHandlers[fieldId],
    picklistChangeHandlers,
    tableDataHandlers,
    loading,
    addNewCustomField,
    editNewField,
  };
};
