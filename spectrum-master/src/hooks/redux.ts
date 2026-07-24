import { batch, useDispatch, useSelector } from 'react-redux';
import { ThunkDispatch } from 'redux-thunk';

import { AllActionTypes } from 'store/allActions.types';

import { RootState } from '../reducers';

// automatically attaches the RootState for easier use
export const useEnhancedSelector = <R extends any = {}>(
  selector: (state: RootState) => R,
  eqFn?: (left: R, right: R) => boolean
) => useSelector<RootState, R>(selector, eqFn);

export type SyncariThunkDispatch = ThunkDispatch<RootState, any, AllActionTypes>;
export const useEnhancedDispatch = (): SyncariThunkDispatch => {
  return useDispatch<SyncariThunkDispatch>();
};

export const useEnhancedBatchDispatch = (): [typeof batch, SyncariThunkDispatch] => {
  const dispatch = useEnhancedDispatch();
  return [batch, dispatch];
};
