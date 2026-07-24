import {
  TagAction,
  GET_TAGS_PENDING,
  GET_TAGS_FULFILLED,
  GET_TAGS_FAILED,
  ADD_TAG_PENDING,
  ADD_TAG_FULFILLED,
  ADD_TAG_FAILED,
  REMOVE_TAG_PENDING,
  REMOVE_TAG_FULFILLED,
  REMOVE_TAG_FAILED,
} from './types';

export const getTagsPending = (params: any): TagAction => ({ type: GET_TAGS_PENDING, params });
export const getTagsFulfilled = (params: any, payload: any): TagAction => ({
  type: GET_TAGS_FULFILLED,
  params,
  payload,
});
export const getTagsFailed = (params: any, error: Error): TagAction => ({ type: GET_TAGS_FAILED, params, error });

export const addTagPending = (params: any): TagAction => ({ type: ADD_TAG_PENDING, params });
export const addTagFulfilled = (params: any, payload: any): TagAction => ({ type: ADD_TAG_FULFILLED, params, payload });
export const addTagFailed = (params: any, error: Error): TagAction => ({ type: ADD_TAG_FAILED, params, error });

export const removeTagPending = (params: any): TagAction => ({ type: REMOVE_TAG_PENDING, params });
export const removeTagFulfilled = (params: any, payload: any): TagAction => ({
  type: REMOVE_TAG_FULFILLED,
  params,
  payload,
});
export const removeTagFailed = (params: any, error: Error): TagAction => ({ type: REMOVE_TAG_FAILED, params, error });
