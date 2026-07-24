import produce, { Draft } from 'immer';
import objectHash from 'object-hash';

import AppConstants from 'utils/AppConstants';
import { safeJoin } from 'utils/StringUtil';

import {
  DataStudioState,
  DataStudioActionType,
  getFiltersListKey,
  GET_ENTITY_RECORD_DETAIL_PENDING,
  GET_ENTITY_RECORD_DETAIL_FULFILLED,
  GET_ENTITY_RECORD_DETAIL_FAILED,
  GET_ENTITY_RECORDS_PENDING,
  GET_ENTITY_RECORDS_FULFILLED,
  GET_ENTITY_RECORDS_FAILED,
  GET_ENTITY_FILTERS_PENDING,
  GET_ENTITY_FILTERS_FULFILLED,
  GET_ENTITY_FILTERS_FAILED,
  CREATE_ENTITY_FILTER_PENDING,
  CREATE_ENTITY_FILTER_FULFILLED,
  CREATE_ENTITY_FILTER_FAILED,
  SAVE_ENTITY_FILTER_PENDING,
  SAVE_ENTITY_FILTER_FULFILLED,
  SAVE_ENTITY_FILTER_FAILED,
  BOOKMARK_ENTITY_FILTER_PENDING,
  BOOKMARK_ENTITY_FILTER_FULFILLED,
  BOOKMARK_ENTITY_FILTER_FAILED,
  DELETE_ENTITY_FILTER_PENDING,
  DELETE_ENTITY_FILTER_FULFILLED,
  DELETE_ENTITY_FILTER_FAILED,
  DELETE_ENTITY_PENDING,
  DELETE_ENTITY_FULFILLED,
  DELETE_ENTITY_FAILED,
  UPDATE_RECORD_DATA_PENDING,
  UPDATE_RECORD_DATA_FULFILLED,
  UPDATE_RECORD_DATA_FAILED,
  DELETE_RECORD_DATA_PENDING,
  DELETE_RECORD_DATA_FULFILLED,
  DELETE_RECORD_DATA_FAILED,
  UPDATE_ADHOC_FILTER,
  DELETE_ADHOC_FILTER,
  CLEAR_UPDATE_RECORD_DATA_ERRORS,
  CLEAR_FILTER_UPSERT_STATUS,
  CREATE_RECORD_PENDING,
  CREATE_RECORD_FULFILLED,
  CREATE_RECORD_FAILED,
} from './types';

const { FETCH_STATUS } = AppConstants;

export const initialState: DataStudioState = {
  entityRecords: {},
  entityConfiguredColumns: {},
  entityFieldsMetadata: {},

  entityRecordStatus: {},
  entityRecordErrors: {},

  entityRecordsListStatus: {},
  entityRecordsListData: {},
  entityRecordsListError: {},

  entityFiltersListData: {},
  filtersData: {},
  filtersStatus: {},
  filterBookmarkingStatus: {},
  filterCreatingStatus: {},
  filterUpdatingStatus: {},
  filterDeletingStatus: {},
  entityDeletingStatus: {},
  entityDeleteError: {},
  createRecordStatus: {},
  createRecordErrors: {},
  updateRecordDataStatus: {},
  updateRecordDataErrors: {},

  deleteRecordDataStatus: {},
  deleteRecordDataErrors: {},
};

// This is a helper to prepend an item to an array without duplication
const prependItemUniquely = <T extends unknown = unknown>(xs: T[], x: T) => {
  return Array.from(new Set([x, ...xs]));
};

// copies the item key into the value objects
function enhanceValueWithKey<Key extends string, Value extends object = Record<string, unknown>>(
  obj: Record<Key, Value>
): Record<Key, Value & { key: Key }> {
  const enhancedPairs = Object.entries<Value>(obj).map(([key, value]) => [key, { ...value, key }]);
  return Object.fromEntries(enhancedPairs);
}

const getEntityRecordKey = (entityId: string, recordId: string) => safeJoin(':')(entityId, recordId);

const reducer = produce((draft: Draft<DataStudioState>, action: DataStudioActionType) => {
  switch (action.type) {
    case GET_ENTITY_RECORD_DETAIL_PENDING: {
      const { entityId, recordId } = action.payload;

      if (!(entityId in draft.entityRecords)) {
        draft.entityRecords[entityId] = {};
      }

      if (!(entityId in draft.entityRecordErrors)) {
        draft.entityRecordErrors[entityId] = {};
      } else {
        // clear errors
        delete draft.entityRecordErrors[entityId]?.[recordId];
      }

      if (!(entityId in draft.entityRecordStatus)) {
        draft.entityRecordStatus[entityId] = {};
      }

      draft.entityRecordStatus[entityId][recordId] = AppConstants.FETCH_STATUS.LOADING;

      break;
    }
    case GET_ENTITY_RECORD_DETAIL_FULFILLED: {
      const { entityId, recordId, data } = action.payload;
      draft.entityRecords[entityId][recordId] = {
        ...draft.entityRecords[entityId][recordId],
        ...data.record,
      };
      draft.entityFieldsMetadata[entityId] = enhanceValueWithKey(data.metadata || {});
      draft.entityRecordStatus[entityId][recordId] = AppConstants.FETCH_STATUS.SUCCESS;

      break;
    }
    case GET_ENTITY_RECORD_DETAIL_FAILED: {
      const { entityId, recordId, error } = action.payload;
      draft.entityRecordStatus[entityId][recordId] = AppConstants.FETCH_STATUS.ERROR;
      draft.entityRecordErrors[entityId][recordId] = error;
      break;
    }
    case GET_ENTITY_RECORDS_PENDING: {
      const { entityId } = action.payload;

      if (!(entityId in draft.entityRecords)) {
        draft.entityRecords[entityId] = {};
      }

      if (!(entityId in draft.entityConfiguredColumns)) {
        draft.entityConfiguredColumns[entityId] = [];
      }

      if (!(entityId in draft.entityRecordStatus)) {
        draft.entityRecordStatus[entityId] = {};
      }

      delete draft.entityRecordsListError[entityId];
      draft.entityRecordsListStatus[action.payload.entityId] = FETCH_STATUS.LOADING;
      break;
    }
    case GET_ENTITY_RECORDS_FULFILLED:
      {
        const { entityId, data } = action.payload;
        const {
          metadata: { selectedColumns, fields },
          pageInfo,
          records,
        } = data;

        // store records
        records.forEach((record) => {
          draft.entityRecords[entityId][record.syncariId] = record;
          draft.entityRecordStatus[entityId][record.syncariId] = AppConstants.FETCH_STATUS.SUCCESS;
        });

        const enhancedFieldMetadata = enhanceValueWithKey(fields);
        draft.entityFieldsMetadata[entityId] = enhancedFieldMetadata;
        draft.entityConfiguredColumns[entityId] = selectedColumns;

        draft.entityRecordsListData[entityId] = {
          pageInfo,
          // store list of IDs to pull from records object
          records: records.map((record) => record.syncariId),
        };
        draft.entityRecordsListStatus[entityId] = FETCH_STATUS.SUCCESS;
      }
      break;
    case GET_ENTITY_RECORDS_FAILED:
      draft.entityRecordsListStatus[action.payload.entityId] = FETCH_STATUS.ERROR;
      draft.entityRecordsListError[action.payload.entityId] = action.payload.error;
      break;
    case GET_ENTITY_FILTERS_PENDING: {
      const { entityId, bookmarked } = action.payload;
      const filtersKey = getFiltersListKey(entityId, bookmarked);
      draft.filtersStatus[filtersKey] = FETCH_STATUS.LOADING;
      break;
    }
    case GET_ENTITY_FILTERS_FULFILLED: {
      const { entityId, bookmarked, data } = action.payload;
      const filtersKey = getFiltersListKey(entityId, bookmarked);

      draft.filtersStatus[filtersKey] = FETCH_STATUS.SUCCESS;
      // save list of filters
      draft.entityFiltersListData[filtersKey] = {
        pageInfo: data.pageInfo,
        filters: data.filters.map((filter) => filter.id),
      };

      // save normalized data
      data.filters.forEach((filter) => {
        draft.filtersData[filter.id] = { ...filter, criteriaHash: objectHash(filter.criteria) };
      });

      break;
    }
    case GET_ENTITY_FILTERS_FAILED: {
      const { entityId, bookmarked } = action.payload;
      const filtersKey = getFiltersListKey(entityId, bookmarked);
      draft.filtersStatus[filtersKey] = FETCH_STATUS.ERROR;
      break;
    }
    case CREATE_ENTITY_FILTER_PENDING:
      draft.filterCreatingStatus[action.payload.entityId] = FETCH_STATUS.LOADING;
      break;
    case CREATE_ENTITY_FILTER_FULFILLED: {
      const { entityId, filter } = action.payload;
      draft.filterCreatingStatus[entityId] = FETCH_STATUS.SUCCESS;
      draft.filtersData[filter.id] = {
        ...filter,
        criteriaHash: objectHash(filter.criteria),
      };

      const filtersKey = getFiltersListKey(entityId, filter.bookmarked);
      const allFiltersKey = getFiltersListKey(null, filter.bookmarked);

      [filtersKey, allFiltersKey]
        .filter((key) => key in draft.entityFiltersListData)
        .forEach((key) => {
          draft.entityFiltersListData[key].filters = prependItemUniquely<string>(
            draft.entityFiltersListData[key].filters,
            filter.id
          );

          // if this filter was bookmarked, we'll also add it to the full entity filter listing
          if (filter.bookmarked) {
            draft.entityFiltersListData[entityId].filters = prependItemUniquely<string>(
              draft.entityFiltersListData[entityId].filters,
              filter.id
            );
          }
        });

      break;
    }
    case CREATE_ENTITY_FILTER_FAILED:
      draft.filterCreatingStatus[action.payload.entityId] = FETCH_STATUS.ERROR;
      break;
    case DELETE_ENTITY_FILTER_PENDING:
      draft.filterDeletingStatus[action.payload.filterId] = FETCH_STATUS.LOADING;
      break;
    case DELETE_ENTITY_FILTER_FULFILLED: {
      const { filterId } = action.payload;
      draft.filterDeletingStatus[filterId] = FETCH_STATUS.SUCCESS;

      const filterToDelete = draft.filtersData[filterId];

      // we need to make sure that we've removed the filter from all possible lists
      // entity scoped filters, both bookmarked and not
      // all filters, both bookmarked and not
      [
        getFiltersListKey(filterToDelete.syncariEntityId, true),
        getFiltersListKey(filterToDelete.syncariEntityId, false),
        getFiltersListKey(null, true),
        getFiltersListKey(null, false),
      ]
        .filter((key) => key in draft.entityFiltersListData)
        .forEach((key) => {
          draft.entityFiltersListData[key].filters = draft.entityFiltersListData[key].filters.filter(
            (f) => f !== filterId
          );
        });

      delete draft.filtersData[filterId];
      break;
    }
    case DELETE_ENTITY_FAILED:
      draft.entityDeletingStatus[action.payload.entityId] = FETCH_STATUS.ERROR;
      draft.entityDeleteError[action.payload.entityId] = action.payload.error;
      break;
    case DELETE_ENTITY_PENDING:
      draft.entityDeletingStatus[action.payload.entityId] = FETCH_STATUS.LOADING;
      break;
    case DELETE_ENTITY_FULFILLED: {
      const { entityId } = action.payload;
      draft.entityDeletingStatus[entityId] = FETCH_STATUS.SUCCESS;
      break;
    }
    case DELETE_ENTITY_FILTER_FAILED:
      draft.filterDeletingStatus[action.payload.filterId] = FETCH_STATUS.ERROR;
      break;
    case SAVE_ENTITY_FILTER_PENDING: {
      const { filterId } = action.payload;
      draft.filterUpdatingStatus[filterId] = FETCH_STATUS.LOADING;
      break;
    }
    case SAVE_ENTITY_FILTER_FULFILLED: {
      const { filterId, filter } = action.payload;
      draft.filterUpdatingStatus[filterId] = FETCH_STATUS.SUCCESS;
      draft.filtersData[filterId] = {
        ...filter,
        criteriaHash: objectHash(filter.criteria),
      };

      const { id, syncariEntityId: entityId, bookmarked } = filter;

      const filtersKey = getFiltersListKey(entityId, bookmarked);
      const allFiltersKey = getFiltersListKey(null, bookmarked);

      // we need to update both the specific entity filter list as well as
      // the all entities filter list
      [filtersKey, allFiltersKey]
        .filter((key) => key in draft.entityFiltersListData)
        .forEach((key) => {
          draft.entityFiltersListData[filtersKey].filters = prependItemUniquely<string>(
            draft.entityFiltersListData[filtersKey].filters,
            id
          );

          // if this filter was bookmarked, we'll also add it to the full entity filter listing
          if (bookmarked) {
            draft.entityFiltersListData[key].filters = prependItemUniquely<string>(
              draft.entityFiltersListData[key].filters,
              id
            );
          }
        });
      break;
    }
    case SAVE_ENTITY_FILTER_FAILED: {
      const { filterId } = action.payload;
      draft.filterUpdatingStatus[filterId] = FETCH_STATUS.ERROR;
      break;
    }
    case BOOKMARK_ENTITY_FILTER_PENDING:
      draft.filterBookmarkingStatus[action.payload.filterId] = FETCH_STATUS.LOADING;
      break;
    case BOOKMARK_ENTITY_FILTER_FULFILLED: {
      const { bookmarked, filterId } = action.payload;
      draft.filterBookmarkingStatus[filterId] = FETCH_STATUS.SUCCESS;

      if (filterId in draft.filtersData) {
        const { syncariEntityId: entityId } = draft.filtersData[filterId];
        const filtersKey = getFiltersListKey(entityId, true);
        const allFiltersKey = getFiltersListKey(null, true);

        draft.filtersData[filterId].bookmarked = bookmarked;

        // we need to update both the specific entity filter list as well as
        // the all entities filter list
        [filtersKey, allFiltersKey]
          .filter((key) => key in draft.entityFiltersListData)
          .forEach((key) => {
            if (!bookmarked) {
              // this filter is no longer bookmarked, remove it from the bookmarked list
              draft.entityFiltersListData[key].filters = draft.entityFiltersListData[key].filters.filter(
                (f) => f !== filterId
              );
            } else {
              // add the filter to our bookmarks list
              draft.entityFiltersListData[key].filters = prependItemUniquely<string>(
                draft.entityFiltersListData[key].filters,
                filterId
              );
            }
          });
      }

      break;
    }
    case BOOKMARK_ENTITY_FILTER_FAILED:
      draft.filterBookmarkingStatus[action.payload.filterId] = FETCH_STATUS.ERROR;
      break;
    case CLEAR_UPDATE_RECORD_DATA_ERRORS: {
      const { entityId, recordId } = action.payload;

      if (draft.updateRecordDataErrors[entityId]?.[recordId]) {
        delete draft.updateRecordDataErrors[entityId][recordId];
      }

      break;
    }
    case CREATE_RECORD_PENDING: {
      const { entityId } = action.payload;

      draft.createRecordStatus[entityId] = {
        status: FETCH_STATUS.LOADING,
      };

      // Clear any previous errors
      if (draft.createRecordErrors[entityId]) {
        delete draft.createRecordErrors[entityId];
      }

      break;
    }

    case CREATE_RECORD_FULFILLED: {
      const { entityId } = action.payload;

      if (!draft.entityRecords[entityId]) {
        draft.entityRecords[entityId] = {};
      }

      draft.createRecordStatus[entityId] = {
        status: FETCH_STATUS.SUCCESS,
      };

      break;
    }

    case CREATE_RECORD_FAILED: {
      const { entityId, fieldErrors } = action.payload;

      draft.createRecordStatus[entityId] = {
        status: FETCH_STATUS.ERROR,
      };

      if (fieldErrors && Object.keys(fieldErrors).length) {
        draft.createRecordErrors[entityId] = {
          errors: fieldErrors,
        };
      }
      break;
    }
    case UPDATE_RECORD_DATA_PENDING: {
      const { entityId, recordId } = action.payload;

      if (!(entityId in draft.updateRecordDataStatus)) {
        draft.updateRecordDataStatus[entityId] = {};
      }

      draft.updateRecordDataStatus[entityId][recordId] = FETCH_STATUS.LOADING;

      // clear any errors when submitting again
      if (draft.updateRecordDataErrors[entityId]?.[recordId]) {
        delete draft.updateRecordDataErrors[entityId][recordId];
      }

      break;
    }
    case UPDATE_RECORD_DATA_FULFILLED: {
      const { entityId, recordId, recordData } = action.payload;

      // this shouldn't happen since we should've been loading first
      if (!(entityId in draft.updateRecordDataStatus)) {
        draft.updateRecordDataStatus[entityId] = {};
      }

      draft.updateRecordDataStatus[entityId][recordId] = FETCH_STATUS.SUCCESS;
      draft.entityRecords[entityId][recordId] = recordData;

      break;
    }
    case UPDATE_RECORD_DATA_FAILED: {
      const { entityId, recordId, fieldErrors } = action.payload;

      // this shouldn't happen since we should've been loading first
      if (!(entityId in draft.updateRecordDataStatus)) {
        draft.updateRecordDataStatus[entityId] = {};
      }

      draft.updateRecordDataStatus[entityId][recordId] = FETCH_STATUS.ERROR;

      if (fieldErrors && Object.keys(fieldErrors).length) {
        if (!(entityId in draft.updateRecordDataErrors)) {
          draft.updateRecordDataErrors[entityId] = {};
        }

        draft.updateRecordDataErrors[entityId][recordId] = fieldErrors;
      }

      break;
    }
    case DELETE_RECORD_DATA_PENDING: {
      const { entityId, recordId } = action.payload;
      const recordKey = getEntityRecordKey(entityId, recordId);

      draft.deleteRecordDataStatus[recordKey] = FETCH_STATUS.LOADING;

      // clear any errors when submitting again
      if (recordKey in draft.deleteRecordDataErrors) {
        delete draft.deleteRecordDataErrors[recordKey];
      }

      break;
    }
    case DELETE_RECORD_DATA_FULFILLED: {
      const { entityId, recordId, deleteInEndSystems } = action.payload;
      const recordKey = getEntityRecordKey(entityId, recordId);
      draft.deleteRecordDataStatus[recordKey] = FETCH_STATUS.SUCCESS;

      const recordToUpdate = draft.entityRecords[entityId][recordId];
      if (recordToUpdate) {
        recordToUpdate.values.isDeleted = recordToUpdate.deleted = true;

        // when deleting from Syncari, we need to remove the item from our list
        if (!deleteInEndSystems && draft.entityRecordsListData[entityId]?.records) {
          draft.entityRecordsListData[entityId].records = draft.entityRecordsListData[entityId].records.filter(
            (id) => id !== recordId
          );
        }
      }

      break;
    }
    case UPDATE_ADHOC_FILTER: {
      const { filter } = action.payload;
      draft.filtersData[filter.id] = {
        ...filter,
        criteriaHash: objectHash(filter.criteria),
      };
      break;
    }
    case DELETE_ADHOC_FILTER:
      const { filterId } = action.payload;

      if (filterId in draft.filtersData) {
        delete draft.filtersData[filterId];
      }
      break;
    case DELETE_RECORD_DATA_FAILED: {
      const { entityId, recordId, error } = action.payload;
      const recordKey = getEntityRecordKey(entityId, recordId);

      draft.deleteRecordDataStatus[recordKey] = FETCH_STATUS.ERROR;
      if (error) {
        draft.deleteRecordDataErrors[recordKey] = error;
      }
      break;
    }
    case CLEAR_FILTER_UPSERT_STATUS: {
      const { entityId, filterId } = action.payload;

      if (entityId) {
        delete draft.filterCreatingStatus[entityId];
      }
      if (filterId) {
        delete draft.filterUpdatingStatus[filterId];
      }
    }
  }
}, initialState);

export default reducer;
