//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce, { Draft } from 'immer';

import { AddTagFulfilled, ADD_TAG_FULFILLED, RemoveTagFulfilled, REMOVE_TAG_FULFILLED } from 'store/tags/types';
import AppConstants from 'utils/AppConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

import {
  EntityAction,
  EntityState,
  GET_CONNECTOR_ENTITIES_FAILED,
  GET_CONNECTOR_ENTITIES_FULFILLED,
  GET_CONNECTOR_ENTITIES_PENDING,
  GET_ENTITIES_FAILED,
  GET_ENTITIES_FULFILLED,
  GET_ENTITIES_PENDING,
  GET_ENTITY_FIELDS_FAILED,
  GET_ENTITY_FIELDS_FULFILLED,
  GET_ENTITY_FIELDS_PENDING,
  GET_ENTITY_MAPPING_FAILED,
  GET_ENTITY_MAPPING_FULFILLED,
  GET_ENTITY_MAPPING_PENDING,
  GET_ENTITY_RECORD_COUNTS_FAILED,
  GET_ENTITY_RECORD_COUNTS_FULFILLED,
  GET_ENTITY_RECORD_COUNTS_PENDING,
  GET_FIELD_MAPPING_FAILED,
  GET_FIELD_MAPPING_FULFILLED,
  GET_FIELD_MAPPING_PENDING,
  SAVE_ENTITY_MAPPING_FAILED,
  SAVE_ENTITY_MAPPING_FULFILLED,
  SAVE_ENTITY_MAPPING_PENDING,
  SAVE_FIELD_MAPPING_FAILED,
  SAVE_FIELD_MAPPING_FULFILLED,
  SAVE_FIELD_MAPPING_PENDING,
  SHOW_CONNECTOR_ENTITY_MODAL,
  SHOW_CONNECTOR_FIELD_MODAL,
  SHOW_QUICK_START_PUBLISH,
} from './types';

const { FETCH_STATUS } = AppConstants;

export const _getDefaultState = (): EntityState => {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.ENTITY),
    connectorEntitiesFetching: false,
    connectorEntityModalVisible: false,
    entities: undefined,
    entitiesFetching: false,
    entityRecordsCounts: {},
    entityRecordTotalCounts: {
      active: 0,
      deleted: 0,
    },
    entityRecordsCountsStatus: {},
    fieldsFetching: false,
    connectorEntitiesOnly: {},
    connectiorFields: {},
    connectorFieldsWithStatus: {},
  };
};

const reducer = produce((draft: Draft<EntityState>, action: EntityAction | RemoveTagFulfilled | AddTagFulfilled) => {
  switch (action.type) {
    case GET_ENTITIES_PENDING:
      draft.entitiesFetching = true;
      break;
    case GET_ENTITIES_FULFILLED:
      draft.entitiesFetching = false;
      Object.assign(draft, action.payload);
      break;
    case GET_ENTITIES_FAILED:
      draft.entitiesFetching = false;
      break;
    case GET_CONNECTOR_ENTITIES_PENDING:
      // We use the same api to for the graph behind the mapping modal.
      // Make sure that we do not show the loading screen for the graph
      // and only show it on the select component. It might be better
      // to have a separate action even they are hitting the same api.
      if (!action.detailed) {
        draft.connectorEntitiesOnly[action.connectorId] = {
          status: FETCH_STATUS.LOADING,
        };
      } else {
        draft.entitiesFetching = true;
      }
      break;
    case GET_CONNECTOR_ENTITIES_FULFILLED:
      if (!action.payload.detailed) {
        draft.connectorEntitiesOnly[action.payload.connectorId] = {
          data: action.payload.entities,
          status: FETCH_STATUS.SUCCESS,
        };
      } else {
        draft.connectorEntities = action.payload;
      }
      draft.entitiesFetching = false;
      break;
    case GET_CONNECTOR_ENTITIES_FAILED:
      if (!action.detailed) {
        draft.connectorEntitiesOnly[action.connectorId] = {
          status: FETCH_STATUS.ERROR,
        };
      } else {
        draft.entitiesFetching = false;
      }
      break;
    case GET_ENTITY_FIELDS_PENDING:
      draft.connectorFieldsWithStatus[action.entityId] = {
        status: FETCH_STATUS.LOADING,
      };
      break;
    case GET_ENTITY_FIELDS_FULFILLED:
      draft.connectorFieldsWithStatus[action.entityId] = {
        data: action.payload,
        status: FETCH_STATUS.SUCCESS,
      };
      break;
    case GET_ENTITY_FIELDS_FAILED:
      draft.connectorFieldsWithStatus[action.entityId] = {
        status: FETCH_STATUS.ERROR,
      };
      break;
    case SHOW_CONNECTOR_ENTITY_MODAL:
      draft.connectorEntityModalVisible = action.visible;
      draft.manageConnectorEntity = action.manageConnectorEntity;
      break;
    case SHOW_CONNECTOR_FIELD_MODAL:
      draft.connectorFieldModalVisible = action.visible;
      draft.manageConnectorField = action.manageConnectorField;
      draft.connectorFieldModalErrorMessage = '';
      break;
    case GET_ENTITY_MAPPING_PENDING:
      draft.connectorEntitiesFetching = true;
      break;
    case GET_ENTITY_MAPPING_FULFILLED:
      draft.connectorEntitiesFetching = false;
      draft.connectorEntities = action.payload;
      break;
    case GET_ENTITY_MAPPING_FAILED:
      draft.connectorEntitiesFetching = false;
      break;
    case GET_FIELD_MAPPING_PENDING:
      draft.connectorFieldModalErrorMessage = '';
      draft.connectorFieldsFetching = true;
      break;
    case GET_FIELD_MAPPING_FULFILLED:
      draft.connectorFieldsFetching = false;
      draft.connectorFields = action.payload;
      break;
    case GET_FIELD_MAPPING_FAILED:
      draft.connectorFieldsFetching = false;
      break;
    case SAVE_FIELD_MAPPING_PENDING:
      draft.connectorFieldModalErrorMessage = '';
      draft.connectorFieldModalVisible = true;
      break;
    case SAVE_FIELD_MAPPING_FULFILLED:
      draft.connectorFieldModalVisible = false;
      break;
    case SAVE_FIELD_MAPPING_FAILED:
      draft.connectorFieldModalVisible = true;
      // @ts-ignore
      draft.connectorFieldModalErrorMessage = action.payload?.message;
      break;
    case SAVE_ENTITY_MAPPING_PENDING:
      draft.entitiesFetching = true;
      break;
    case SAVE_ENTITY_MAPPING_FULFILLED:
      draft.entitiesFetching = false;
      break;
    case SAVE_ENTITY_MAPPING_FAILED:
      draft.entitiesFetching = false;
      break;
    case ADD_TAG_FULFILLED: {
      const newTag = action.params?.[0];

      // ensure we're working on an Entity, not a Field
      if (newTag.type.toUpperCase() === AppConstants.SCOPE.ENTITY) {
        const entityIdx = draft.entities?.findIndex((entity) => entity.id === newTag.taggedId) || -1;

        // if we found the entity, add the tag
        if (draft.entities && ~entityIdx) {
          draft.entities[entityIdx].tags.push(newTag.name);
        }
      }

      break;
    }
    case REMOVE_TAG_FULFILLED: {
      const newTag = action.params?.[0];

      // ensure we're working on an Entity, not a Field
      if (newTag.type.toUpperCase() === AppConstants.SCOPE.ENTITY) {
        const entityIdx = draft.entities?.findIndex((entity) => entity.id === newTag.taggedId) || -1;

        // if we found the entity, remove the tag
        if (draft.entities && ~entityIdx) {
          draft.entities[entityIdx].tags = draft.entities[entityIdx].tags.filter((tag) => tag !== newTag.name);
        }
      }

      break;
    }

    case GET_ENTITY_RECORD_COUNTS_PENDING:
      action.payload.entityApiNames.forEach((apiName) => {
        draft.entityRecordsCountsStatus[apiName] = AppConstants.FETCH_STATUS.LOADING;
      });
      break;
    case GET_ENTITY_RECORD_COUNTS_FULFILLED:
      const { countMap, totals } = action.payload.data;
      draft.entityRecordTotalCounts.active = totals.active;
      draft.entityRecordTotalCounts.deleted = totals.deleted;

      Object.entries(countMap).forEach(([apiName, count]) => {
        draft.entityRecordsCounts[apiName] = count;
        draft.entityRecordsCountsStatus[apiName] = AppConstants.FETCH_STATUS.SUCCESS;
      });
      break;
    case GET_ENTITY_RECORD_COUNTS_FAILED:
      action.payload.entityApiNames.forEach((apiName) => {
        draft.entityRecordsCountsStatus[apiName] = AppConstants.FETCH_STATUS.ERROR;
      });
      break;
    case SHOW_QUICK_START_PUBLISH:
      draft.quickStartPublishVisible = action.payload.visible;
      draft.quickStartPublishId = action.payload.quickStartId;
      break;
    default:
      break;
  }
}, _getDefaultState());

export default reducer;
