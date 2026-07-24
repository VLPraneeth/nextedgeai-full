//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce, { Draft } from 'immer';
import { cloneDeep, find, findIndex } from 'lodash';
import { createContext, useContext } from 'react';

import { Mapping } from 'store/fast-mapper/types';

import { MapperFields } from '../Mapper';
import { EditedMapping, MappingState } from '../types';

export interface IMapperContext {
  dispatch: (action: any) => void;
  state: MappingState;
}

export const makeInitialBrowseMappingState = (initialValues?: Mapping[]): MappingState => ({
  values: initialValues ?? [],
  editedValues: [],
});

export enum BrowseMappingActions {
  RESET = 'browseMapping/reset',
  RESET_ROW = 'browseMapping/resetRow',
  SET_SYNAPSE = 'browseMapping/setSynapse',
  SET_SYNAPSE_ENTITY = 'browseMapping/setSynapseEntity',
  SET_SYNAPSE_FIELD = 'browseMapping/setSynapseField',
  SET_SYNC_DIRECTION = 'browseMapping/setSyncDirection',
  SET_SYNCARI_ENTITY_FIELD = 'browseMapping/setSyncariEntityField',
  SET_FAILED_MAPPINGS = 'browseMapping/setFailedMappings',
}

// Use our local reducer for any local mapping changes.
export const browseMappingReducer = produce((draft: Draft<MappingState>, action) => {
  if (action.type !== BrowseMappingActions.RESET && action.type !== BrowseMappingActions.RESET_ROW) {
    const editedMapping = find(draft.editedValues, { existing: { id: action.id } });
    const existingMapping = find(draft.values, { id: action.id });

    // If a mapping has not already been edited, then add it to the edited list.
    if (!editedMapping && existingMapping) {
      const updatedValue: EditedMapping = {
        existing: cloneDeep(existingMapping),
        updated: cloneDeep(existingMapping),
      };

      draft.editedValues?.push(updatedValue);
    }
  }

  switch (action.type) {
    case BrowseMappingActions.RESET:
      draft.values = [];
      draft.editedValues = [];
      break;
    case BrowseMappingActions.RESET_ROW:
      const valueIndex = findIndex(draft.values, { id: action.id });

      const editedMapping = find(draft.editedValues, { existing: { id: action.id } });
      const editedMappingIndex = findIndex(draft.editedValues, { existing: { id: action.id } });

      if (editedMapping) {
        draft.values[valueIndex] = editedMapping?.existing;
        draft?.editedValues?.splice(editedMappingIndex, 1);
      }

      break;
    case BrowseMappingActions.SET_SYNAPSE:
      {
        const value = find(draft.values, { id: action.id });
        const editedMapping = find(draft.editedValues, { existing: { id: action.id } });

        if (value && editedMapping) {
          value[MapperFields.SYNAPSE_ID] = action.value;
          value[MapperFields.ERROR_MESSAGE] = '';
          value.edited = true;
        }
      }
      break;
    case BrowseMappingActions.SET_SYNAPSE_ENTITY:
      {
        const value = find(draft.values, { id: action.id });
        const editedMapping = find(draft.editedValues, { existing: { id: action.id } });

        if (value && editedMapping) {
          value[MapperFields.SYNAPSE_ENTITY_ID] = action.value;
          value[MapperFields.ERROR_MESSAGE] = '';
          value.edited = true;

          editedMapping.updated = value;
        }
      }
      break;
    case BrowseMappingActions.SET_SYNAPSE_FIELD:
      {
        const value = find(draft.values, { id: action.id });
        const editedMapping = find(draft.editedValues, { existing: { id: action.id } });

        if (value && editedMapping) {
          value.synapseFieldId = action.value;
          value.synapseReadOnly = action.readOnly;
          value[MapperFields.ERROR_MESSAGE] = '';
          value.edited = true;

          editedMapping.updated = value;
        }
      }
      break;
    case BrowseMappingActions.SET_SYNC_DIRECTION:
      {
        const value = find(draft.values, { id: action.id });
        const editedMapping = find(draft.editedValues, { existing: { id: action.id } });

        if (value && editedMapping) {
          value.syncDirectionId = action.value;
          value.errorMessage = '';
          value.edited = true;

          editedMapping.updated = value;
        }
      }
      break;
    case BrowseMappingActions.SET_SYNCARI_ENTITY_FIELD:
      {
        const value = find(draft.values, { id: action.id });
        const editedMapping = find(draft.editedValues, { existing: { id: action.id } });

        if (value && editedMapping) {
          value.syncariFieldId = action.value;
          value.syncariReadOnly = action.readOnly;
          value.syncariFieldDisplayName = action.displayName;
          value.syncariFieldDatatype = action.dataType;
          value.syncariFieldApiName = action.apiName;
          value.errorMessage = '';
          value.edited = true;

          editedMapping.updated = value;
        }
      }
      break;
    case BrowseMappingActions.SET_FAILED_MAPPINGS:
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
    default:
      break;
  }
  return draft;
});

const BrowseMappingContext = createContext<IMapperContext>({
  dispatch: () => {},
  state: makeInitialBrowseMappingState(),
});

export interface BrowseMappingContextProviderProps {
  children: React.ReactNode;
  value: IMapperContext;
}

export const BrowseMappingContextProvider = ({ children, value }: BrowseMappingContextProviderProps) => {
  return <BrowseMappingContext.Provider value={value}>{children}</BrowseMappingContext.Provider>;
};

export const useBrowseMappingContext = () => {
  const { dispatch, state } = useContext(BrowseMappingContext);
  return {
    dispatch,
    state,
  };
};
