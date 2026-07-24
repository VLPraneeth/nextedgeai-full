import { PicklistValue } from 'components/inputs/types';

import {
  PicklistsActionType,
  ResponseError,
  GET_PICKLIST_VALUES,
  GET_PICKLIST_VALUES_PENDING,
  GET_PICKLIST_VALUES_FULFILLED,
  GET_PICKLIST_VALUES_FAILED,
  FETCH_PICKLIST_VALUES_PENDING,
  FETCH_PICKLIST_VALUES_FULFILLED,
  FETCH_PICKLIST_VALUES_FAILED,
  INVALIDATE_PICKLIST_VALUES,
} from './types';

export const GetPicklistValues = (): PicklistsActionType => ({
  type: GET_PICKLIST_VALUES,
});

export const GetPicklistValuesPending = (): PicklistsActionType => ({
  type: GET_PICKLIST_VALUES_PENDING,
});

export const GetPicklistValuesFulfilled = (key: string, pickListValues: any): PicklistsActionType => ({
  type: GET_PICKLIST_VALUES_FULFILLED,
  key,
  pickListValues,
});

export const GetPicklistValuesFailed = (error: ResponseError): PicklistsActionType => ({
  type: GET_PICKLIST_VALUES_FAILED,
  error,
});

export const FetchPicklistValuesPending = (id: string): PicklistsActionType => ({
  type: FETCH_PICKLIST_VALUES_PENDING,
  id,
});

export const FetchPicklistValuesFulfilled = (id: string, pickListValues: PicklistValue[]): PicklistsActionType => ({
  type: FETCH_PICKLIST_VALUES_FULFILLED,
  id,
  pickListValues,
});

export const FetchPicklistValuesFailed = (id: string, error: ResponseError): PicklistsActionType => ({
  type: FETCH_PICKLIST_VALUES_FAILED,
  error,
  id,
});

export const invalidatePicklistValues = (): PicklistsActionType => ({
  type: INVALIDATE_PICKLIST_VALUES,
});
