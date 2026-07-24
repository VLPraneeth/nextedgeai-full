export const GET_TAGS_PENDING = 'GET_TAGS_PENDING';
export const GET_TAGS_FULFILLED = 'GET_TAGS_FULFILLED';
export const GET_TAGS_FAILED = 'GET_TAGS_FAILED';
export const ADD_TAG_PENDING = 'ADD_TAG_PENDING';
export const ADD_TAG_FULFILLED = 'ADD_TAG_FULFILLED';
export const ADD_TAG_FAILED = 'ADD_TAG_FAILED';
export const REMOVE_TAG_PENDING = 'REMOVE_TAG_PENDING';
export const REMOVE_TAG_FULFILLED = 'REMOVE_TAG_FULFILLED';
export const REMOVE_TAG_FAILED = 'REMOVE_TAG_FAILED';

export interface GetTagsPending {
  type: typeof GET_TAGS_PENDING;
  params: any;
}

export interface GetTagsFulfilled {
  type: typeof GET_TAGS_FULFILLED;
  // TODO: type this
  payload: any;
  params: any;
}

export interface GetTagsFailed {
  type: typeof GET_TAGS_FAILED;
  error: Error;
  params: any;
}

// TODO: better types!
export interface AddTagPending {
  type: typeof ADD_TAG_PENDING;
  params: any;
}

export interface AddTagFulfilled {
  type: typeof ADD_TAG_FULFILLED;
  params: any;
  payload: any;
}

export interface AddTagFailed {
  type: typeof ADD_TAG_FAILED;
  params: any;
  error: Error;
}

export interface RemoveTagPending {
  type: typeof REMOVE_TAG_PENDING;
  params: any;
}

export interface RemoveTagFulfilled {
  type: typeof REMOVE_TAG_FULFILLED;
  params: any;
  payload: any;
}

export interface RemoveTagFailed {
  type: typeof REMOVE_TAG_FAILED;
  params: any;
  error: Error;
}

export type TagAction =
  | GetTagsPending
  | GetTagsFulfilled
  | GetTagsFailed
  | AddTagPending
  | AddTagFulfilled
  | AddTagFailed
  | RemoveTagPending
  | RemoveTagFulfilled
  | RemoveTagFailed;

export interface TagState {
  tags: any[];
  tagsError?: Error | null;
  tagsFetching: boolean;
}
