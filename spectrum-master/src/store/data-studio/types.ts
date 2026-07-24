import { FilterValue, InputDataType } from 'components/inputs/types';
import { FetchStatus } from 'store/types';

const ALL_FILTERS_ENTITY = 'all';

export const getFiltersListKey = (entityId?: string | null, bookmarked?: boolean) => {
  // without an entityId, use "all"
  const entity = entityId || ALL_FILTERS_ENTITY;
  return bookmarked ? `${entity}::bookmarked` : entity;
};

export enum SyncariExtendedRecordDeletedKeys {
  IS_DELETED = 'IsDeleted',
  DATA_STUDIO_IS_DELETED = 'datastudio_isDeleted',
}

export interface PageInfo {
  start: string | null;
  end: string | null;
  hasMore: boolean;
  pageNumber: number;
  maxPageNumber: number;
  message?: string;
  filteredCount?: number;
  totalCount?: number;
  sorting?: (string | { columnName: string; ascending: boolean })[];
}

export interface FieldMetadata {
  fieldId: string;
  dataType: InputDataType;
  label: string;
  canDisplay: boolean;
  canEdit: boolean;
  canFilter: boolean;
  isSystem?: boolean;
}

export interface EntityRecordsMetadata {
  selectedColumns: string[];
  fields: Record<string, FieldMetadata>;
}

export interface EntityRecord {
  deleted: boolean;
  idMapping: Record<string, string>;
  lastModified: number;
  id: string;
  syncariId: string;
  syncariTimestamp: number | string;
  values: Record<string, any>;
}

export interface DataStudioRecordsResponse {
  metadata: EntityRecordsMetadata;
  pageInfo: PageInfo;
  records: EntityRecord[];
}

export interface DataStudioRecordDetailResponse {
  metadata: Record<string, EnhancedFieldMetadata>;
  record: EntityRecord;
}

// enhanced with key after reducerm apiName added in useEntityRecord
export type EnhancedFieldMetadata = FieldMetadata & { key: string; apiName?: string };
export type EnhancedEntityRecordsMetadata = Omit<EntityRecordsMetadata, 'fields'> & {
  fields: Record<string, EnhancedFieldMetadata>;
};
export type DataStudioRecordsData = {
  pageInfo: DataStudioRecordsResponse['pageInfo'];
  records: string[];
};

export type Predicate = FilterValue;

export interface EntityFilter {
  [index: string]: any;
  id: string;
  name: string;
  description?: string;
  criteria: Predicate;
  syncariEntityId: string;
  tags: null | string[];
  bookmarked: boolean;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface DataStudioFiltersResponse {
  pageInfo: PageInfo;
  filters: EntityFilter[];
}

export const GET_ENTITY_RECORDS_PENDING = 'studio/data/entity/records/list/pending';
export const GET_ENTITY_RECORDS_FULFILLED = 'studio/data/entity/records/list/fulfilled';
export const GET_ENTITY_RECORDS_FAILED = 'studio/data/entity/records/list/failed';

interface GetEntityRecordsPendingAction {
  type: typeof GET_ENTITY_RECORDS_PENDING;
  payload: {
    entityId: string;
  };
}

interface GetEntityRecordsFulfilledAction {
  type: typeof GET_ENTITY_RECORDS_FULFILLED;
  payload: {
    entityId: string;
    data: DataStudioRecordsResponse;
  };
}

interface GetEntityRecordsFailedAction {
  type: typeof GET_ENTITY_RECORDS_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

export const GET_ENTITY_RECORD_DETAIL_PENDING = 'studio/data/entity/records/detail/pending';
export const GET_ENTITY_RECORD_DETAIL_FULFILLED = 'studio/data/entity/records/detail/fulfilled';
export const GET_ENTITY_RECORD_DETAIL_FAILED = 'studio/data/entity/records/detail/failed';

interface GetEntityRecordDetailPendingAction {
  type: typeof GET_ENTITY_RECORD_DETAIL_PENDING;
  payload: {
    entityId: string;
    recordId: string;
  };
}

interface GetEntityRecordDetailFulfilledAction {
  type: typeof GET_ENTITY_RECORD_DETAIL_FULFILLED;
  payload: {
    entityId: string;
    recordId: string;
    data: DataStudioRecordDetailResponse;
  };
}

interface GetEntityRecordDetailFailedAction {
  type: typeof GET_ENTITY_RECORD_DETAIL_FAILED;
  payload: {
    entityId: string;
    recordId: string;
    error: ResponseError;
  };
}

export const GET_ENTITY_FILTERS_PENDING = 'studio/data/entity/filters/list/pending';
export const GET_ENTITY_FILTERS_FULFILLED = 'studio/data/entity/filters/list/fulfilled';
export const GET_ENTITY_FILTERS_FAILED = 'studio/data/entity/filters/list/failed';

interface GetEntityFiltersPendingAction {
  type: typeof GET_ENTITY_FILTERS_PENDING;
  payload: {
    entityId?: string | null;
    bookmarked?: boolean;
  };
}

interface GetEntityFiltersFulfilledAction {
  type: typeof GET_ENTITY_FILTERS_FULFILLED;
  payload: {
    entityId?: string | null;
    bookmarked?: boolean;
    data: DataStudioFiltersResponse;
  };
}

interface GetEntityFiltersFailedAction {
  type: typeof GET_ENTITY_FILTERS_FAILED;
  payload: {
    entityId?: string | null;
    bookmarked?: boolean;
    error: ResponseError;
  };
}

export const CREATE_ENTITY_FILTER_PENDING = 'studio/data/entity/filters/create/pending';
export const CREATE_ENTITY_FILTER_FULFILLED = 'studio/data/entity/filters/create/fulfilled';
export const CREATE_ENTITY_FILTER_FAILED = 'studio/data/entity/filters/create/failed';

interface CreateEntityFilterPendingAction {
  type: typeof CREATE_ENTITY_FILTER_PENDING;
  payload: {
    entityId: string;
  };
}

interface CreateEntityFilterFulfilledAction {
  type: typeof CREATE_ENTITY_FILTER_FULFILLED;
  payload: {
    entityId: string;
    filter: EntityFilter;
  };
}

interface CreateEntityFilterFailedAction {
  type: typeof CREATE_ENTITY_FILTER_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

export const SAVE_ENTITY_FILTER_PENDING = 'studio/data/entity/filters/save/pending';
export const SAVE_ENTITY_FILTER_FULFILLED = 'studio/data/entity/filters/save/fulfilled';
export const SAVE_ENTITY_FILTER_FAILED = 'studio/data/entity/filters/save/failed';

interface SaveEntityFilterPendingAction {
  type: typeof SAVE_ENTITY_FILTER_PENDING;
  payload: {
    filterId: string;
  };
}

interface SaveEntityFilterFulfilledAction {
  type: typeof SAVE_ENTITY_FILTER_FULFILLED;
  payload: {
    filterId: string;
    filter: EntityFilter;
  };
}

interface SaveEntityFilterFailedAction {
  type: typeof SAVE_ENTITY_FILTER_FAILED;
  payload: {
    filterId: string;
    error: ResponseError;
  };
}

export const DELETE_ENTITY_FILTER_PENDING = 'studio/data/entity/filters/delete/pending';
export const DELETE_ENTITY_FILTER_FULFILLED = 'studio/data/entity/filters/delete/fulfilled';
export const DELETE_ENTITY_FILTER_FAILED = 'studio/data/entity/filters/delete/failed';

interface DeleteEntityFilterPendingAction {
  type: typeof DELETE_ENTITY_FILTER_PENDING;
  payload: {
    filterId: string;
  };
}

interface DeleteEntityFilterFulfilledAction {
  type: typeof DELETE_ENTITY_FILTER_FULFILLED;
  payload: {
    filterId: string;
  };
}

interface DeleteEntityFilterFailedAction {
  type: typeof DELETE_ENTITY_FILTER_FAILED;
  payload: {
    filterId: string;
    error: ResponseError;
  };
}

export const DELETE_ENTITY_PENDING = 'studio/data/entity/delete/pending';
export const DELETE_ENTITY_FULFILLED = 'studio/data/entity/delete/fulfilled';
export const DELETE_ENTITY_FAILED = 'studio/data/entity/delete/failed';

interface DeleteEntityPendingAction {
  type: typeof DELETE_ENTITY_PENDING;
  payload: {
    entityId: string;
  };
}

interface DeleteEntityFulfilledAction {
  type: typeof DELETE_ENTITY_FULFILLED;
  payload: {
    entityId: string;
  };
}

interface DeleteEntityFailedAction {
  type: typeof DELETE_ENTITY_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

export const BOOKMARK_ENTITY_FILTER_PENDING = 'studio/data/entity/filters/bookmark/pending';
export const BOOKMARK_ENTITY_FILTER_FULFILLED = 'studio/data/entity/filters/bookmark/fulfilled';
export const BOOKMARK_ENTITY_FILTER_FAILED = 'studio/data/entity/filters/bookmark/failed';

interface BookmarkEntityFilterPendingAction {
  type: typeof BOOKMARK_ENTITY_FILTER_PENDING;
  payload: {
    filterId: string;
  };
}

interface BookmarkEntityFilterFulfilledAction {
  type: typeof BOOKMARK_ENTITY_FILTER_FULFILLED;
  payload: {
    filterId: string;
    bookmarked: boolean;
  };
}

interface BookmarkEntityFilterFailedAction {
  type: typeof BOOKMARK_ENTITY_FILTER_FAILED;
  payload: {
    filterId: string;
    error: ResponseError;
  };
}

export const UPDATE_RECORD_DATA_PENDING = 'studio/data/entity/record/update/pending';
export const UPDATE_RECORD_DATA_FULFILLED = 'studio/data/entity/record/update/fulfilled';
export const UPDATE_RECORD_DATA_FAILED = 'studio/data/entity/record/update/failed';

interface UpdateRecordDataPending {
  type: typeof UPDATE_RECORD_DATA_PENDING;
  payload: {
    entityId: string;
    recordId: string;
  };
}

export const CREATE_RECORD_PENDING = 'studio/data/entity/record/create/pending';
export const CREATE_RECORD_FULFILLED = 'studio/data/entity/record/create/fulfilled';
export const CREATE_RECORD_FAILED = 'studio/data/entity/record/create/failed';

interface CreateRecordPending {
  type: typeof CREATE_RECORD_PENDING;
  payload: {
    entityId: string;
  };
}

interface CreateRecordFulfilled {
  type: typeof CREATE_RECORD_FULFILLED;
  payload: {
    entityId: string;
    recordData: Record<string, any>;
  };
}

export interface FieldErrorObject {
  code: string;
  message: string;
}

export type ErrorsPayload =
  | {
      fields?: Record<string, FieldErrorObject[]>;
      record?: EntityRecord[];
    }
  | Record<string, string>; // Support both new and legacy formats

interface CreateRecordFailed {
  type: typeof CREATE_RECORD_FAILED;
  payload: {
    entityId: string;
    submitError?: ResponseError;
    fieldErrors?: Record<string, string>;
  };
}

interface UpdateRecordDataFulfilled {
  type: typeof UPDATE_RECORD_DATA_FULFILLED;
  payload: {
    entityId: string;
    recordId: string;
    recordData: EntityRecord;
  };
}

interface UpdateRecordDataFailed {
  type: typeof UPDATE_RECORD_DATA_FAILED;
  payload: {
    entityId: string;
    recordId: string;
    submitError?: ResponseError;
    fieldErrors?: Record<string, string>;
  };
}

export const DELETE_RECORD_DATA_PENDING = 'studio/data/entity/record/delete/pending';
export const DELETE_RECORD_DATA_FULFILLED = 'studio/data/entity/record/delete/fulfilled';
export const DELETE_RECORD_DATA_FAILED = 'studio/data/entity/record/delete/failed';

interface DeleteRecordDataPending {
  type: typeof DELETE_RECORD_DATA_PENDING;
  payload: {
    entityId: string;
    recordId: string;
  };
}

interface DeleteRecordDataFulfilled {
  type: typeof DELETE_RECORD_DATA_FULFILLED;
  payload: {
    entityId: string;
    recordId: string;
    deleteInEndSystems: boolean;
  };
}

interface DeleteRecordDataFailed {
  type: typeof DELETE_RECORD_DATA_FAILED;
  payload: {
    entityId: string;
    recordId: string;
    error?: ResponseError;
  };
}

export const UPDATE_ADHOC_FILTER = 'studio/data/entity/filters/adhoc/update';
export const DELETE_ADHOC_FILTER = 'studio/data/entity/filters/adhoc/delete';

interface UpdateAdhocEntityFilter {
  type: typeof UPDATE_ADHOC_FILTER;
  payload: { filter: EntityFilter };
}

interface DeleteAdhocEntityFilter {
  type: typeof DELETE_ADHOC_FILTER;
  payload: {
    filterId: string;
  };
}

export const CLEAR_UPDATE_RECORD_DATA_ERRORS = 'studio/data/entity/record/update/errors/clear';
export const CLEAR_CREATE_RECORD_ERRORS = 'studio/data/entity/record/create/errors/clear';
export const CLEAR_FILTER_UPSERT_STATUS = 'studio/data/entity/filters/upsertStatus/clear';

interface ClearUpdateRecordDataErrors {
  type: typeof CLEAR_UPDATE_RECORD_DATA_ERRORS;
  payload: {
    entityId: string;
    recordId: string;
  };
}

interface ClearCreateRecordErrors {
  type: typeof CLEAR_CREATE_RECORD_ERRORS;
  payload: {
    entityId: string;
  };
}

interface ClearFilterUpsertStatus {
  type: typeof CLEAR_FILTER_UPSERT_STATUS;
  payload: {
    entityId: string;
    filterId: string | undefined;
  };
}

export type DataStudioActionType =
  | BookmarkEntityFilterFailedAction
  | BookmarkEntityFilterFulfilledAction
  | BookmarkEntityFilterPendingAction
  | ClearFilterUpsertStatus
  | ClearUpdateRecordDataErrors
  | ClearCreateRecordErrors
  | CreateEntityFilterFailedAction
  | CreateEntityFilterFulfilledAction
  | CreateEntityFilterPendingAction
  | DeleteAdhocEntityFilter
  | DeleteEntityFailedAction
  | DeleteEntityFilterFailedAction
  | DeleteEntityFilterFulfilledAction
  | DeleteEntityFilterPendingAction
  | DeleteEntityFulfilledAction
  | DeleteEntityPendingAction
  | DeleteRecordDataFailed
  | DeleteRecordDataFulfilled
  | DeleteRecordDataPending
  | GetEntityFiltersFailedAction
  | GetEntityFiltersFulfilledAction
  | GetEntityFiltersPendingAction
  | GetEntityRecordDetailFailedAction
  | GetEntityRecordDetailFulfilledAction
  | GetEntityRecordDetailPendingAction
  | GetEntityRecordsFailedAction
  | GetEntityRecordsFulfilledAction
  | GetEntityRecordsPendingAction
  | SaveEntityFilterFailedAction
  | SaveEntityFilterFulfilledAction
  | SaveEntityFilterPendingAction
  | UpdateAdhocEntityFilter
  | UpdateRecordDataFailed
  | UpdateRecordDataFulfilled
  | UpdateRecordDataPending
  | CreateRecordFailed
  | CreateRecordFulfilled
  | CreateRecordPending;

export type ResponseError =
  | Error
  | {}
  | { data?: any; message?: string; errorMessage: string; status: number; statusText: string };

export interface FlattenedEntityFiltersResponse extends Omit<DataStudioFiltersResponse, 'filters'> {
  /** this is a list of filter Ids, we'll store filters normalized */
  filters: string[];
}

export type EntityFilterWithHash = EntityFilter & { criteriaHash: string };

export interface DataStudioState {
  entityRecords: Record<string, Record<string, EntityRecord>>;
  entityFieldsMetadata: Record<string, Record<string, EnhancedFieldMetadata>>;
  entityConfiguredColumns: Record<string, string[]>;

  entityRecordStatus: Record<string, Record<string, FetchStatus>>;
  entityRecordErrors: Record<string, Record<string, ResponseError>>;

  entityRecordsListStatus: Record<string, FetchStatus>;
  entityRecordsListData: Record<string, DataStudioRecordsData>;
  entityRecordsListError: Record<string, ResponseError>;

  entityFiltersListData: Record<string, FlattenedEntityFiltersResponse>;
  filtersStatus: Record<string, FetchStatus>;
  filtersData: Record<string, EntityFilterWithHash>;

  filterBookmarkingStatus: Record<string, FetchStatus>;
  filterCreatingStatus: Record<string, FetchStatus>;
  filterUpdatingStatus: Record<string, FetchStatus>;
  filterDeletingStatus: Record<string, FetchStatus>;

  entityDeletingStatus: Record<string, FetchStatus>;
  entityDeleteError: Record<string, ResponseError>;

  createRecordStatus: Record<string, Record<string, FetchStatus>>;
  createRecordErrors: Record<string, Record<string, Record<string, string>>>;

  updateRecordDataStatus: Record<string, Record<string, FetchStatus>>;
  updateRecordDataErrors: Record<string, Record<string, Record<string, string>>>;

  deleteRecordDataStatus: Record<string, FetchStatus>;
  deleteRecordDataErrors: Record<string, ResponseError>;
}
