import { Action } from 'redux';
import { ThunkAction as BaseThunkAction } from 'redux-thunk';

import { FilterValue } from 'components/inputs/types';
import { PaginatedApi, PromiseThunkAction } from 'store/types';
import { deleteRequest, get, patch, post, postBigInt, put } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { packageData } from 'utils/ErrorUtils';
import { replaceToken } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import { RootState } from '../../reducers';
import {
  bookmarkEntityFilterFailed,
  bookmarkEntityFilterFulfilled,
  bookmarkEntityFilterPending,
  createEntityFilterFailed,
  createEntityFilterFulfilled,
  createEntityFilterPending,
  createRecordFailed,
  createRecordFulfilled,
  createRecordPending,
  deleteEntityFailed,
  deleteEntityFilterFailed,
  deleteEntityFilterFulfilled,
  deleteEntityFilterPending,
  deleteEntityFulfilled,
  deleteEntityPending,
  deleteRecordDataFailed,
  deleteRecordDataFulfilled,
  deleteRecordDataPending,
  getEntityFiltersFailed,
  getEntityFiltersFulfilled,
  getEntityFiltersPending,
  getEntityRecordDetailFailed,
  getEntityRecordDetailFulfilled,
  getEntityRecordDetailPending,
  getEntityRecordsFailed,
  getEntityRecordsFulfilled,
  getEntityRecordsPending,
  saveEntityFilterFailed,
  saveEntityFilterFulfilled,
  saveEntityFilterPending,
  updateRecordDataFailed,
  updateRecordDataFulfilled,
  updateRecordDataPending,
} from './actions';
import {
  DataStudioFiltersResponse,
  DataStudioRecordDetailResponse,
  DataStudioRecordsResponse,
  EntityFilter,
  EntityRecord,
  ResponseError,
} from './types';

type ThunkAction = BaseThunkAction<void, RootState, unknown, Action<string>>;

type Predicate = FilterValue;

interface GetEntityRecordsArgs extends PaginatedApi {
  entityId: SyncariID;
  predicate?: Predicate;
  orderBy?: string;
  sortDirection?: string;
  page?: string;
}

export const getEntityRecords = ({
  entityId,
  predicate,
  cursor,
  count = 100,
  direction = 'next',
  orderBy,
  sortDirection,
  page,
}: GetEntityRecordsArgs): ThunkAction => (dispatch) => {
  dispatch(getEntityRecordsPending(entityId));

  const preparedPredicate = predicate ? packageData(predicate) : undefined;

  return postBigInt<DataStudioRecordsResponse>(
    makeUrl(
      DataUrlConstants.GET_ENTITY_RECORDS_LIST,
      { entityId },
      {
        cursor,
        count,
        direction,
        orderBy,
        sortDirection,
        page,
      }
    ),
    preparedPredicate,
    {
      headers: {
        'Content-Type': 'text/plain',
      },
    }
  )
    .then((resp) => {
      dispatch(getEntityRecordsFulfilled(entityId, resp.data));
    })
    .catch((err) => {
      dispatch(getEntityRecordsFailed(entityId, getErrorMessage(err)));
    });
};

interface GetEntityFiltersArgs extends PaginatedApi {
  entityId?: string | null;
  bookmarked?: boolean;
  orderBy?: string;
  sortDirection?: string;
}

export const getEntityFilters = ({
  entityId = null,
  bookmarked,
  cursor,
  count = 100,
  direction = 'next',
  orderBy,
  sortDirection,
}: GetEntityFiltersArgs): ThunkAction => (dispatch) => {
  dispatch(getEntityFiltersPending(entityId, bookmarked));

  return get<DataStudioFiltersResponse>(
    makeUrl(DataUrlConstants.GET_ENTITY_FILTERS_LIST, null, {
      entityId,
      cursor,
      count,
      direction,
      bookmarked,
      ...(orderBy && { orderBy }),
      ...(sortDirection && { sortDirection }),
    })
  )
    .then((resp) => {
      dispatch(getEntityFiltersFulfilled(entityId, resp.data, bookmarked));
    })
    .catch((err) => {
      dispatch(getEntityFiltersFailed(entityId, getErrorMessage(err), bookmarked));
    });
};

export const createEntityFilter = (
  entityId: string,
  criteria: EntityFilter['criteria'],
  name: string,
  description: string,
  tags: string[],
  bookmarked: boolean
): ThunkAction => (dispatch) => {
  const params = {
    syncariEntityId: entityId,
    criteria,
    name,
    description,
    tags,
    bookmarked,
  };

  dispatch(createEntityFilterPending(entityId));

  return post(DataUrlConstants.CREATE_ENTITY_FILTER, params)
    .then((resp) => {
      dispatch(createEntityFilterFulfilled(entityId, resp.data));
    })
    .catch((err) => {
      dispatch(createEntityFilterFailed(entityId, err));
    });
};

export const updateEntityFilter = (
  filterId: string,
  filter: EntityFilter
): PromiseThunkAction<{ success: boolean }> => (dispatch) => {
  dispatch(saveEntityFilterPending(filterId));

  return put(replaceToken(DataUrlConstants.UPDATE_ENTITY_FILTER, { filterId }), filter)
    .then((resp) => {
      dispatch(saveEntityFilterFulfilled(filterId, resp.data));
      return { success: true };
    })
    .catch((err) => {
      dispatch(saveEntityFilterFailed(filterId, err));
      return { success: false };
    });
};

export const deleteEntityFilter = (filterId: string): ThunkAction => (dispatch) => {
  dispatch(deleteEntityFilterPending(filterId));

  return deleteRequest(replaceToken(DataUrlConstants.DELETE_ENTITY_FILTER, { filterId }))
    .then(() => {
      dispatch(deleteEntityFilterFulfilled(filterId));
    })
    .catch((err) => {
      dispatch(deleteEntityFilterFailed(filterId, err));
    });
};

export const deleteEntity = (entityId: string): ThunkAction => (dispatch) => {
  dispatch(deleteEntityPending(entityId));

  return deleteRequest(replaceToken(DataUrlConstants.DELETE_ENTITY, { entityId }))
    .then(() => {
      dispatch(deleteEntityFulfilled(entityId));
    })
    .catch((err) => {
      dispatch(deleteEntityFailed(entityId, getErrorMessage(err)));
    });
};

export const bookmarkEntityFilter = (filterId: string, bookmarked: boolean): ThunkAction => (dispatch) => {
  dispatch(bookmarkEntityFilterPending(filterId));

  return patch(replaceToken(DataUrlConstants.BOOKMARK_ENTITY_FILTER, { filterId }), bookmarked, {
    headers: { 'content-type': 'application/json' },
  })
    .then(() => {
      dispatch(bookmarkEntityFilterFulfilled(filterId, bookmarked));
    })
    .catch((err) => {
      dispatch(bookmarkEntityFilterFailed(filterId, getErrorMessage(err)));
    });
};

interface FieldErrorObject {
  code: string;
  message: string;
}

export type UpdateRecordDataResponse = {
  record: EntityRecord;
  errors: {
    fields?: Record<string, FieldErrorObject[]>;
    record?: any[];
  };
};

export const updateRecordData = (
  entityId: string,
  recordId: string,
  recordData: Record<string, any>
): PromiseThunkAction<UpdateRecordDataResponse> => (dispatch) => {
  dispatch(updateRecordDataPending(entityId, recordId));

  return put<UpdateRecordDataResponse>(
    replaceToken(DataUrlConstants.UPDATE_RECORD_DATA, { entityId, recordId }),
    recordData
  )
    .then((resp) => {
      const { errors, record } = resp.data;

      // Check if there are field errors
      const hasFieldErrors = errors?.fields && Object.keys(errors.fields).length > 0;
      const hasRecordErrors = errors?.record && errors.record.length > 0;

      if (hasFieldErrors || hasRecordErrors) {
        // Dispatch with the nested errors structure
        dispatch(updateRecordDataFailed(entityId, recordId, resp.data.errors as Record<string, string>));
      } else {
        dispatch(updateRecordDataFulfilled(entityId, recordId, record));
      }

      return resp.data;
    })
    .catch((err) => {
      const errorResponse = err.response?.data || err;

      // Structure the error response to match the expected format
      const formattedError: UpdateRecordDataResponse = {
        record: {} as EntityRecord,
        errors: {
          fields: errorResponse?.errors?.fields || {},
          record: errorResponse?.errors?.record || [],
        },
      };

      dispatch(updateRecordDataFailed(entityId, recordId, undefined, getErrorMessage(err)));
      return formattedError;
    });
};

export type CreateRecordDataResponse = {
  record: EntityRecord;
  errors: {
    fields?: Record<string, FieldErrorObject[]>;
    record?: any[];
  };
};

export const createRecord = (
  entityId: string,
  recordData: Record<string, any>
): PromiseThunkAction<CreateRecordDataResponse> => async (dispatch) => {
  dispatch(createRecordPending(entityId));

  try {
    const resp = await post<CreateRecordDataResponse>(
      replaceToken(DataUrlConstants.CREATE_RECORD_DATA, { entityId }),
      recordData as Record<string, any>
    );

    const { errors, record } = resp.data;

    // Check if there are field errors
    const hasFieldErrors = errors?.fields && Object.keys(errors.fields).length > 0;
    const hasRecordErrors = errors?.record && errors.record.length > 0;

    if (hasFieldErrors || hasRecordErrors) {
      // Dispatch with the nested errors structure
      dispatch(createRecordFailed(entityId, resp.data.errors as Record<string, string>));
      return resp.data;
    } else {
      dispatch(createRecordFulfilled(entityId, record?.values || {}));
      return resp.data;
    }
  } catch (err) {
    const errorResponse = (err as any).response?.data || err;

    // Structure the error response to match the expected format
    const formattedError: CreateRecordDataResponse = {
      record: {} as EntityRecord,
      errors: {
        fields: errorResponse?.errors?.fields || {},
        record: errorResponse?.errors?.record || [],
      },
    };

    dispatch(createRecordFailed(entityId, formattedError.errors as Record<string, string>, errorResponse));
    return formattedError;
  }
};

type DeleteRecordSuccess = { success: true };
type DeleteRecordFailure = { success: false; message: string };

export const deleteRecordData = (
  entityId: string,
  recordId: string,
  deleteInEndSystems: boolean
): PromiseThunkAction<DeleteRecordSuccess | DeleteRecordFailure | void> => async (dispatch) => {
  dispatch(deleteRecordDataPending(entityId, recordId));

  try {
    await deleteRequest(makeUrl(DataUrlConstants.DELETE_RECORD_DATA, { entityId, recordId }, { deleteInEndSystems }));
    dispatch(deleteRecordDataFulfilled(entityId, recordId, deleteInEndSystems));
    return { success: true };
  } catch (err) {
    dispatch(deleteRecordDataFailed(entityId, recordId, getErrorMessage(err) as ResponseError));
    return { success: false, message: (err as Error).message };
  }
};

export type GetRecordDetailParams = {
  entityId: string;
  recordId: string;
};

export const getRecordDetail = ({
  entityId,
  recordId,
}: GetRecordDetailParams): PromiseThunkAction<DataStudioRecordDetailResponse | void> => (dispatch) => {
  dispatch(getEntityRecordDetailPending(entityId, recordId));

  return get<DataStudioRecordDetailResponse>(makeUrl(DataUrlConstants.GET_RECORD_DATA, { entityId, recordId }))
    .then((resp) => {
      dispatch(getEntityRecordDetailFulfilled(entityId, recordId, resp.data));

      return resp.data;
    })
    .catch((err) => {
      dispatch(getEntityRecordDetailFailed(entityId, recordId, getErrorMessage(err)));
      return err;
    });
};
