import produce from 'immer';

import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';

import { RootState } from '../../reducers';
import {
  DataStudioFiltersResponse,
  DataStudioRecordsData,
  EnhancedFieldMetadata,
  EntityRecord,
  ResponseError,
  SyncariExtendedRecordDeletedKeys,
  getFiltersListKey,
} from './types';

export type SelectListDataForEntityIdResult =
  | undefined
  | (Omit<DataStudioRecordsData, 'records'> & {
      records: EntityRecord[];
    });

export const selectListDataForEntityId = (state: RootState, entityId?: string): SelectListDataForEntityIdResult => {
  if (!entityId || !(entityId in state.dataStudio.entityRecordsListData)) {
    return;
  }

  const data = state.dataStudio.entityRecordsListData[entityId];

  const entityRecords = data.records.map((recordId) => {
    const record = state.dataStudio.entityRecords[entityId][recordId];
    return {
      ...record,

      // Note: Ideally these syncari system values should be in the right keys of the values object
      // since that is whats described in the metadata but we are doing it here in the short term...
      [SyncariExtendedRecordDeletedKeys.IS_DELETED]: record.values.isDeleted,
      [SyncariExtendedRecordDeletedKeys.DATA_STUDIO_IS_DELETED]: record.deleted,
    };
  });

  return {
    ...data,
    records: entityRecords,
  };
};

export const selectListErrorForEntityId = (state: RootState, entityId?: string): ResponseError | undefined =>
  entityId ? state.dataStudio.entityRecordsListError[entityId] : undefined;

export const selectListStatusForEntityId = (state: RootState, entityId?: string): FetchStatus => {
  const status = entityId ? state.dataStudio.entityRecordsListStatus[entityId] : undefined;

  return status ?? AppConstants.FETCH_STATUS.IDLE;
};

export const selectFiltersDataForEntityId = (
  state: RootState,
  entityId?: string | null,
  bookmarked?: boolean
): DataStudioFiltersResponse | undefined => {
  const key = getFiltersListKey(entityId, bookmarked);
  const filterData = state.dataStudio.entityFiltersListData[key];

  if (filterData) {
    return {
      ...filterData,
      filters: filterData.filters.map((filterId) => state.dataStudio.filtersData[filterId]),
    };
  }

  return undefined;
};

export const selectFiltersStatusForEntityId = (
  state: RootState,
  entityId?: string | null,
  bookmarked?: boolean
): FetchStatus => {
  const key = getFiltersListKey(entityId, bookmarked);
  return state.dataStudio.filtersStatus[key] ?? AppConstants.FETCH_STATUS.IDLE;
};

// simple tracking of inflight creation by entityId NOT filterId
export const selectFilterCreatingStatus = (state: RootState, entityId: string): FetchStatus => {
  return state.dataStudio.filterCreatingStatus[entityId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectFilterDeletingStatus = (state: RootState, filterId: string): FetchStatus => {
  const status = filterId ? state.dataStudio.filterDeletingStatus[filterId] : undefined;
  return status ?? AppConstants.FETCH_STATUS.IDLE;
};

export const selectEntityDeletingStatus = (state: RootState, entityId: string): FetchStatus => {
  const status = entityId ? state.dataStudio.entityDeletingStatus[entityId] : undefined;
  return status ?? AppConstants.FETCH_STATUS.IDLE;
};

export const selectFilterUpdatingStatus = (state: RootState, filterId: string): FetchStatus => {
  const status = filterId ? state.dataStudio.filterUpdatingStatus[filterId] : undefined;
  return status ?? AppConstants.FETCH_STATUS.IDLE;
};

export const selectFilterBookmarkingStatus = (state: RootState, filterId: string): FetchStatus => {
  const status = filterId ? state.dataStudio.filterBookmarkingStatus[filterId] : undefined;

  return status ?? AppConstants.FETCH_STATUS.IDLE;
};

export const selectFilterIsBookmarked = (state: RootState, filterId: string): boolean => {
  return filterId ? state.dataStudio.filtersData[filterId]?.bookmarked : false;
};

export const selectFieldsMetadataForEntity = (
  state: RootState,
  entityId?: string
): Record<string, EnhancedFieldMetadata> => {
  if (!entityId) {
    return EMPTY_OBJECT;
  }

  return state.dataStudio.entityFieldsMetadata[entityId] || EMPTY_OBJECT;
};

export const selectSelectedColumnsForEntity = (state: RootState, entityId?: string) => {
  if (!entityId) {
    return EMPTY_ARRAY;
  }

  return state.dataStudio.entityConfiguredColumns[entityId] || EMPTY_ARRAY;
};

export const selectRecordForEntity = (state: RootState, entityId?: string, recordId?: string) => {
  if (!entityId || !recordId) {
    return;
  }

  const record = state.dataStudio.entityRecords[entityId]?.[recordId];

  if (record) {
    return produce(record, (draft) => {
      // See note in selectListDataForEntityId selector
      draft.values[SyncariExtendedRecordDeletedKeys.IS_DELETED] = draft.values.isDeleted;
      draft.values[SyncariExtendedRecordDeletedKeys.DATA_STUDIO_IS_DELETED] = draft.deleted;
    });
  }
};

export const selectRecordStatus = (state: RootState, entityId?: string, recordId?: string) => {
  if (!entityId || !recordId) {
    return AppConstants.FETCH_STATUS.IDLE;
  }

  return state.dataStudio.entityRecordStatus[entityId]?.[recordId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectCreateRecordDataStatus = (state: RootState, entityId: string) => {
  return state.dataStudio.createRecordStatus[entityId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectCreateRecordDataErrors = (state: RootState, entityId: string) => {
  return state.dataStudio.createRecordErrors[entityId] || EMPTY_OBJECT;
};

export const selectUpdateRecordDataStatus = (state: RootState, entityId: string, recordId: string) => {
  return state.dataStudio.updateRecordDataStatus[entityId]?.[recordId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectUpdateRecordDataErrors = (state: RootState, entityId: string, recordId: string) => {
  return state.dataStudio.updateRecordDataErrors[entityId]?.[recordId] || EMPTY_OBJECT;
};

export const selectDeleteRecordDataStatus = (state: RootState, recordId: string) => {
  return state.dataStudio.deleteRecordDataStatus[recordId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectDeleteRecordDataErrors = (state: RootState, recordId: string) => {
  return state.dataStudio.deleteRecordDataErrors[recordId];
};
