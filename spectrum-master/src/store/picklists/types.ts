import { PicklistValue, OperatorValue } from 'components/inputs/types';
import { SkullInput } from 'components/skull';
import { FetchStatus } from 'store/types';

export type ResponseError =
  | Error
  | {}
  | { data?: any; message?: string; errorMessage: string; status: number; statusText: string };

export const GET_PICKLIST_VALUES = 'GET_PICKLIST_VALUES';
export const GET_PICKLIST_VALUES_PENDING = 'GET_PICKLIST_VALUES_PENDING';
export const GET_PICKLIST_VALUES_FULFILLED = 'GET_PICKLIST_VALUES_FULFILLED';
export const GET_PICKLIST_VALUES_FAILED = 'GET_PICKLIST_VALUES_FAILED';

export const FETCH_PICKLIST_VALUES_PENDING = 'FETCH_PICKLIST_VALUES_PENDING';
export const FETCH_PICKLIST_VALUES_FULFILLED = 'FETCH_PICKLIST_VALUES_FULFILLED';
export const FETCH_PICKLIST_VALUES_FAILED = 'FETCH_PICKLIST_VALUES_FAILED';

export const INVALIDATE_PICKLIST_VALUES = 'INVALIDATE_PICKLIST_VALUES';

interface GetPicklistValues {
  type: typeof GET_PICKLIST_VALUES;
}

interface GetPicklistValuesPending {
  type: typeof GET_PICKLIST_VALUES_PENDING;
}

interface GetPicklistValuesFulfilled {
  type: typeof GET_PICKLIST_VALUES_FULFILLED;
  pickListValues: any;
  key: string;
}

interface GetPicklistValuesFailed {
  type: typeof GET_PICKLIST_VALUES_FAILED;
  error: ResponseError;
}

interface FetchPicklistValuesPending {
  type: typeof FETCH_PICKLIST_VALUES_PENDING;
  id: string;
}

interface FetchPicklistValuesFulfilled {
  type: typeof FETCH_PICKLIST_VALUES_FULFILLED;
  pickListValues: PicklistValue[];
  id: string;
}

interface FetchPicklistValuesFailed {
  type: typeof FETCH_PICKLIST_VALUES_FAILED;
  error: ResponseError;
  id: string;
}

interface InvalidatePicklistValues {
  type: typeof INVALIDATE_PICKLIST_VALUES;
}

export type PicklistsActionType =
  | GetPicklistValues
  | GetPicklistValuesPending
  | GetPicklistValuesFulfilled
  | GetPicklistValuesFailed
  | FetchPicklistValuesPending
  | FetchPicklistValuesFulfilled
  | FetchPicklistValuesFailed
  | InvalidatePicklistValues;

export interface PicklistsState {
  fetchingPicklistValues: boolean;
  fetchingPicklistValuesStatus: Record<string, FetchStatus>;
  picklistValues: Record<string, PicklistValue[] | OperatorValue[]>;

  [index: string]: any;
}

export interface PicklistAdditionalNodeConfig {
  currentNodeId: string;
  configName?: string;
  additionalConfigParams?: Record<string, string>;
  currentConfiguration?: Record<string, any>;
  graph?: any;
  additionalConfigs?: SkullInput[];
}

export type PicklistAdditionalNodeConfigParams = Omit<PicklistAdditionalNodeConfig, 'additionalConfigs'>;
