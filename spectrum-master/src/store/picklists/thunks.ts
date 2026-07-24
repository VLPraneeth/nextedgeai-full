import { AxiosResponse } from 'axios';
import { Action } from 'redux';
import { ThunkAction } from 'redux-thunk';

import { PicklistValue } from 'components/inputs/types';
import { SyncariThunkDispatch } from 'hooks/redux';
import { get } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { RootState } from '../../reducers';
import {
  FetchPicklistValuesFailed,
  FetchPicklistValuesFulfilled,
  FetchPicklistValuesPending,
  GetPicklistValues,
  GetPicklistValuesFailed,
  GetPicklistValuesFulfilled,
} from './actions';

export function getPicklistValues(picklistParams: any): ThunkAction<void, RootState, unknown, Action<string>> {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(GetPicklistValues());
    get(
      makeUrl(DataUrlConstants.GET_PICKLIST_VALUES, undefined, {
        ...(picklistParams?.params || {}),
        dependantId: picklistParams.dependantId,
        dependantType: picklistParams.dependantType,
        sendPicklistGroup: picklistParams.sendPicklistGroup,
      })
    )
      .then((resp) => {
        dispatch(GetPicklistValuesFulfilled(picklistParams.key, resp.data));
      })
      .catch((error) => {
        dispatch(GetPicklistValuesFailed(getErrorMessage(error)));
      });
  };
}

export interface FetchPicklistValuesParams {
  id: string;
  dependantId: string;
  dependantType: string;
  sendPicklistGroup?: boolean;
  params?: Record<string, string>;
}

export type FetchPicklistValuesResponse = PicklistValue[];

// Memoized cache of our requests to reduce fetching
const picklistsInFlight = new Map<string, Promise<AxiosResponse<FetchPicklistValuesResponse>>>();

// V2 of the fetch picklist with explicit ids
export function fetchPicklistValues(
  params: FetchPicklistValuesParams
): ThunkAction<void, RootState, unknown, Action<string>> {
  return async (dispatch) => {
    const picklistId = params.id;
    if (!picklistsInFlight.has(picklistId)) {
      picklistsInFlight.set(
        picklistId,
        get<FetchPicklistValuesResponse>(
          makeUrl(DataUrlConstants.GET_PICKLIST_VALUES, undefined, {
            ...(params?.params || {}),
            dependantId: params.dependantId,
            dependantType: params.dependantType,
            sendPicklistGroup: params.sendPicklistGroup,
          })
        )
      );
    }

    dispatch(FetchPicklistValuesPending(picklistId));

    // Note: the `!` after our `.get()` call is need to tell TS that this type
    // cannot be undefined. We can be pretty sure here because we checked for it's
    // existance, and then if it was missing - set it to the fetcher above.
    // We could write our own guarded getter for Map to avoid the `!` in the
    // future
    try {
      const resp = await picklistsInFlight.get(picklistId)!;
      dispatch(FetchPicklistValuesFulfilled(picklistId, resp.data));
      picklistsInFlight.delete(picklistId);
    } catch (error) {
      picklistsInFlight.delete(picklistId);
      dispatch(FetchPicklistValuesFailed(picklistId, getErrorMessage(error)));
    }
  };
}
