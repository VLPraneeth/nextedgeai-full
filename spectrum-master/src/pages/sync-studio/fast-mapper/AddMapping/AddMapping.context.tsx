//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import ObjectID from 'bson-objectid';
import produce, { Draft } from 'immer';
import { find } from 'lodash';
import { createContext, useContext } from 'react';

import { Mapping } from 'store/fast-mapper/types';

import { MapperFields } from '../Mapper';
import { MappingState } from '../types';

export interface IMapperContext {
  dispatch: (action: any) => void;
  state: MappingState;
}

export const makeInitialAddMappingState = (): MappingState => ({ values: [], newSyncariFields: [] });

export enum AddMappingActions {
  RESET = 'addMapping/reset',
  ADD = 'addMapping/add',
  ADD_FULL_MAPPING = 'addMapping/addFullMapping',
  DELETE = 'addMapping/delete',
  SET_SYNAPSE = 'addMapping/setSynapse',
  SET_SYNAPSE_ENTITY = 'addMapping/setSynapseEntity',
  SET_SYNAPSE_FIELD = 'addMapping/setSynapseField',
  SET_SYNC_DIRECTION = 'addMapping/setSyncDirection',
  SET_SYNCARI_ENTITY_FIELD = 'addMapping/setSyncariEntityField',
  SET_FAILED_MAPPINGS = 'addMapping/setFailedMappings',
  ADD_FIELD = 'addMapping/addField',
}

// Use our local reducer for any local mapping changes.
export const addMappingReducer = produce((draft: Draft<MappingState>, action) => {
  switch (action.type) {
    case AddMappingActions.RESET:
      draft.values = [];
      break;
    case AddMappingActions.ADD:
      // Autopopulation is the behavior of carrying over the synapse name and entity name
      // when creating a new row after this information has been provided for a previous row.
      // There are cases in which we want to disable this behavior.
      const shouldAutopopulate = action.autopopulate ?? true;

      const last = shouldAutopopulate ? draft.values?.[draft.values.length - 1] : undefined;
      draft.values.push({
        id: ObjectID.generate(),
        [MapperFields.SYNAPSE_ID]: last ? last[MapperFields.SYNAPSE_ID] : '',
        [MapperFields.SYNAPSE_ENTITY_ID]: last ? last[MapperFields.SYNAPSE_ENTITY_ID] : '',
        [MapperFields.SYNAPSE_FIELD_ID]: '',
        [MapperFields.SYNC_DIRECTION_ID]: '',
        [MapperFields.SYNCARI_ENTITY_FIELD_ID]: '',
      });
      break;
    case AddMappingActions.ADD_FULL_MAPPING:
      {
        const { type, ...payload } = action;
        // Remove any empty rows
        draft.values = draft.values?.filter((value) => value.synapseId);
        draft.values.push({
          ...payload,
        });
      }
      break;
    case AddMappingActions.DELETE:
      draft.values = draft.values.filter((val) => {
        return val.id !== action.id;
      });
      break;
    case AddMappingActions.SET_SYNAPSE:
      {
        const value = find(draft.values, { id: action.id });
        if (value) {
          value[MapperFields.SYNAPSE_ID] = action.value;
          value[MapperFields.ERROR_MESSAGE] = '';
        }
      }
      break;
    case AddMappingActions.SET_SYNAPSE_ENTITY:
      {
        const value = find(draft.values, { id: action.id });
        if (value) {
          value[MapperFields.SYNAPSE_ENTITY_ID] = action.value;
          value[MapperFields.ERROR_MESSAGE] = '';
        }
      }
      break;
    case AddMappingActions.SET_SYNAPSE_FIELD:
      {
        const value = find(draft.values, { id: action.id });
        if (value) {
          value.synapseFieldId = action.value;
          value.synapseReadOnly = action.readOnly;
          value.errorMessage = '';
        }
      }
      break;
    case AddMappingActions.SET_SYNC_DIRECTION:
      {
        const value = find(draft.values, { id: action.id });
        if (value) {
          value.syncDirectionId = action.value;
          value.errorMessage = '';
        }
      }
      break;
    case AddMappingActions.SET_SYNCARI_ENTITY_FIELD:
      {
        const value = find(draft.values, { id: action.id });
        if (value) {
          value.syncariFieldId = action.value;
          value.syncariReadOnly = action.readOnly;
          value.errorMessage = '';
        }
      }
      break;
    case AddMappingActions.SET_FAILED_MAPPINGS:
      draft.values = draft.values.map((val) => {
        const error = action.error?.find((error: Mapping) => error.id === val.id);
        if (error) {
          const { errorMessage, safeErrorMessage } = error;
          return {
            ...val,
            safeErrorMessage,
            errorMessage,
          };
        }
        return val;
      });
      break;
    case AddMappingActions.ADD_FIELD:
      draft.newSyncariFields?.push(action.payload);
      const value = find(draft.values, { id: action.id });
      if (value) {
        value.syncariFieldId = action.payload.id;
        value.createNewSyncariField = true;
        value.syncariFieldDisplayName = action.payload.displayName;
        value.syncariFieldApiName = action.payload.apiName;
        value.syncariFieldDatatype = action.payload.dataType;
        value.syncariFieldIsRequired = action.payload.isRequired;
        value.syncariFieldIsMultiValued = action.payload.isMultivalued;
      }
      break;
    default:
      break;
  }
  return draft;
});

const AddMappingContext = createContext<IMapperContext>({
  dispatch: () => {},
  state: makeInitialAddMappingState(),
});

export interface AddMappingContextProviderProps {
  children: React.ReactNode;
  value: IMapperContext;
}

export const AddMappingContextProvider = ({ children, value }: AddMappingContextProviderProps) => {
  return <AddMappingContext.Provider value={value}>{children}</AddMappingContext.Provider>;
};

export const useAddMappingContext = () => {
  const { dispatch, state } = useContext(AddMappingContext);
  return {
    dispatch,
    state,
  };
};
