import { FilterValue } from 'components/inputs/types';

import {
  DataStudioActionType,
  DataStudioRecordsResponse,
  DataStudioRecordDetailResponse,
  EntityFilter,
  EntityRecord,
  ResponseError,
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
  DELETE_ENTITY_FILTER_PENDING,
  DELETE_ENTITY_FILTER_FULFILLED,
  DELETE_ENTITY_FILTER_FAILED,
  DELETE_ENTITY_PENDING,
  DELETE_ENTITY_FULFILLED,
  DELETE_ENTITY_FAILED,
  BOOKMARK_ENTITY_FILTER_PENDING,
  BOOKMARK_ENTITY_FILTER_FULFILLED,
  BOOKMARK_ENTITY_FILTER_FAILED,
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
  DataStudioFiltersResponse,
  CREATE_RECORD_PENDING,
  CREATE_RECORD_FULFILLED,
  CREATE_RECORD_FAILED,
} from './types';

export const getEntityRecordDetailPending = (entityId: string, recordId: string): DataStudioActionType => ({
  type: GET_ENTITY_RECORD_DETAIL_PENDING,
  payload: {
    entityId,
    recordId,
  },
});

export const getEntityRecordDetailFulfilled = (
  entityId: string,
  recordId: string,
  data: DataStudioRecordDetailResponse
): DataStudioActionType => ({
  type: GET_ENTITY_RECORD_DETAIL_FULFILLED,
  payload: {
    entityId,
    recordId,
    data,
  },
});

export const getEntityRecordDetailFailed = (
  entityId: string,
  recordId: string,
  error: ResponseError
): DataStudioActionType => ({
  type: GET_ENTITY_RECORD_DETAIL_FAILED,
  payload: {
    entityId,
    recordId,
    error,
  },
});

export const getEntityRecordsPending = (entityId: string): DataStudioActionType => ({
  type: GET_ENTITY_RECORDS_PENDING,
  payload: {
    entityId,
  },
});

export const getEntityRecordsFulfilled = (entityId: string, data: DataStudioRecordsResponse): DataStudioActionType => ({
  type: GET_ENTITY_RECORDS_FULFILLED,
  payload: {
    entityId,
    data,
  },
});

export const getEntityRecordsFailed = (entityId: string, error: ResponseError): DataStudioActionType => ({
  type: GET_ENTITY_RECORDS_FAILED,
  payload: {
    entityId,
    error,
  },
});

export const getEntityFiltersPending = (entityId: string | null, bookmarked?: boolean): DataStudioActionType => ({
  type: GET_ENTITY_FILTERS_PENDING,
  payload: { entityId, bookmarked },
});

export const getEntityFiltersFulfilled = (
  entityId: string | null,
  data: DataStudioFiltersResponse,
  bookmarked?: boolean
): DataStudioActionType => ({
  type: GET_ENTITY_FILTERS_FULFILLED,
  payload: {
    entityId,
    bookmarked,
    data,
  },
});

export const getEntityFiltersFailed = (
  entityId: string | null,
  error: ResponseError,
  bookmarked?: boolean
): DataStudioActionType => ({
  type: GET_ENTITY_FILTERS_FAILED,
  payload: {
    entityId,
    bookmarked,
    error,
  },
});

export const createEntityFilterPending = (entityId: string): DataStudioActionType => ({
  type: CREATE_ENTITY_FILTER_PENDING,
  payload: {
    entityId,
  },
});

export const createEntityFilterFulfilled = (entityId: string, filter: EntityFilter): DataStudioActionType => ({
  type: CREATE_ENTITY_FILTER_FULFILLED,
  payload: {
    entityId,
    filter,
  },
});

export const createEntityFilterFailed = (entityId: string, error: ResponseError): DataStudioActionType => ({
  type: CREATE_ENTITY_FILTER_FAILED,
  payload: {
    entityId,
    error,
  },
});

export const saveEntityFilterPending = (filterId: string): DataStudioActionType => ({
  type: SAVE_ENTITY_FILTER_PENDING,
  payload: {
    filterId,
  },
});

export const saveEntityFilterFulfilled = (filterId: string, filter: EntityFilter): DataStudioActionType => ({
  type: SAVE_ENTITY_FILTER_FULFILLED,
  payload: {
    filterId,
    filter,
  },
});

export const saveEntityFilterFailed = (filterId: string, error: Error): DataStudioActionType => ({
  type: SAVE_ENTITY_FILTER_FAILED,
  payload: {
    filterId,
    error,
  },
});

export const deleteEntityFilterPending = (filterId: string): DataStudioActionType => ({
  type: DELETE_ENTITY_FILTER_PENDING,
  payload: { filterId },
});

export const deleteEntityFilterFulfilled = (filterId: string): DataStudioActionType => ({
  type: DELETE_ENTITY_FILTER_FULFILLED,
  payload: { filterId },
});

export const deleteEntityFilterFailed = (filterId: string, error: Error): DataStudioActionType => ({
  type: DELETE_ENTITY_FILTER_FAILED,
  payload: { filterId, error },
});

export const deleteEntityPending = (entityId: string): DataStudioActionType => ({
  type: DELETE_ENTITY_PENDING,
  payload: { entityId },
});

export const deleteEntityFulfilled = (entityId: string): DataStudioActionType => ({
  type: DELETE_ENTITY_FULFILLED,
  payload: { entityId },
});

export const deleteEntityFailed = (entityId: string, error: ResponseError): DataStudioActionType => ({
  type: DELETE_ENTITY_FAILED,
  payload: { entityId, error },
});

export const bookmarkEntityFilterPending = (filterId: string): DataStudioActionType => ({
  type: BOOKMARK_ENTITY_FILTER_PENDING,
  payload: {
    filterId,
  },
});

export const bookmarkEntityFilterFulfilled = (filterId: string, bookmarked: boolean): DataStudioActionType => ({
  type: BOOKMARK_ENTITY_FILTER_FULFILLED,
  payload: {
    filterId,
    bookmarked,
  },
});

export const bookmarkEntityFilterFailed = (filterId: string, error: ResponseError): DataStudioActionType => ({
  type: BOOKMARK_ENTITY_FILTER_FAILED,
  payload: {
    filterId,
    error,
  },
});

export const createRecordPending = (entityId: string): DataStudioActionType => ({
  type: CREATE_RECORD_PENDING,
  payload: {
    entityId,
  },
});

export const createRecordFulfilled = (entityId: string, recordData: Record<string, any>): DataStudioActionType => ({
  type: CREATE_RECORD_FULFILLED,
  payload: {
    entityId,
    recordData,
  },
});

export const createRecordFailed = (
  entityId: string,
  fieldErrors?: Record<string, string>,
  submitError?: ResponseError
): DataStudioActionType => ({
  type: CREATE_RECORD_FAILED,
  payload: {
    entityId,
    fieldErrors,
    submitError,
  },
});

export const updateRecordDataPending = (entityId: string, recordId: string): DataStudioActionType => ({
  type: UPDATE_RECORD_DATA_PENDING,
  payload: {
    entityId,
    recordId,
  },
});

export const updateRecordDataFulfilled = (
  entityId: string,
  recordId: string,
  recordData: EntityRecord
): DataStudioActionType => ({
  type: UPDATE_RECORD_DATA_FULFILLED,
  payload: {
    entityId,
    recordId,
    recordData,
  },
});

export const updateRecordDataFailed = (
  entityId: string,
  recordId: string,
  fieldErrors?: Record<string, string>,
  submitError?: ResponseError
): DataStudioActionType => ({
  type: UPDATE_RECORD_DATA_FAILED,
  payload: {
    entityId,
    recordId,
    fieldErrors,
    submitError,
  },
});

export const deleteRecordDataPending = (entityId: string, recordId: string): DataStudioActionType => ({
  type: DELETE_RECORD_DATA_PENDING,
  payload: {
    entityId,
    recordId,
  },
});

export const deleteRecordDataFulfilled = (
  entityId: string,
  recordId: string,
  deleteInEndSystems: boolean
): DataStudioActionType => ({
  type: DELETE_RECORD_DATA_FULFILLED,
  payload: {
    entityId,
    recordId,
    deleteInEndSystems,
  },
});

export const deleteRecordDataFailed = (
  entityId: string,
  recordId: string,
  error: ResponseError
): DataStudioActionType => ({
  type: DELETE_RECORD_DATA_FAILED,
  payload: {
    entityId,
    recordId,
    error,
  },
});

export const updateAdhocEntityFilter = (
  entityId: string,
  filterId: string,
  criteria: FilterValue,
  moreFilterData: Partial<EntityFilter>
): DataStudioActionType => ({
  type: UPDATE_ADHOC_FILTER,
  payload: {
    filter: {
      id: filterId,
      name: '',
      criteria,
      syncariEntityId: entityId,
      tags: null,
      bookmarked: false,
      createdBy: '',
      createdAt: new Date().toISOString(),
      updatedBy: '',
      updatedAt: new Date().toISOString(),
      ...moreFilterData,
    },
  },
});

export const deleteAdhocEntityFilter = (filterId: string): DataStudioActionType => ({
  type: DELETE_ADHOC_FILTER,
  payload: {
    filterId,
  },
});

export const clearUpdateRecordDataErrors = (entityId: string, recordId: string): DataStudioActionType => ({
  type: CLEAR_UPDATE_RECORD_DATA_ERRORS,
  payload: {
    entityId,
    recordId,
  },
});

export const clearFilterUpsertStatus = (entityId: string, filterId: string | undefined): DataStudioActionType => ({
  type: CLEAR_FILTER_UPSERT_STATUS,
  payload: {
    entityId,
    filterId,
  },
});
