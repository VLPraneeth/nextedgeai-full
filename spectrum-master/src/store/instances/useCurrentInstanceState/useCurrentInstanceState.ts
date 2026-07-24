import { useCallback, useEffect } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useIsTrialUser } from 'store/user/selector.hooks';

import { getInstanceState, InstanceQuotaType, InstanceState } from '../slice';

export interface EnhancedInstanceState {
  expiryDate: string;
  id: string;
  isTrial: boolean;
  numberOfRecordsLeft: number;
  pipelineCount: number;
  publishLimitExpired: boolean;
  recordLimit: number;
  recordLimitExpired: boolean;
  refresh?: () => void;
  synapseCount: number;
  trialDaysLeft: number;
  trialExpired: boolean;
}

/**
 * Hook for accessing relevant properties of the current user's current instance
 *
 * @returns `EnhancedInstanceState` object
 */
export const useCurrentInstanceState = (): EnhancedInstanceState => {
  const dispatch = useEnhancedDispatch();
  const currentInstanceState = useEnhancedSelector<InstanceState>((state) => state.instance.currentInstanceState);
  const userInstanceId = useEnhancedSelector((state) => state.user.currentInstanceNextEdgeId);
  const isTrialUser = useIsTrialUser();

  const hasInstanceId = Boolean(userInstanceId);
  const noCurrentInstanceState = !currentInstanceState.instance?.syncariId;
  const requestInstanceState = hasInstanceId && isTrialUser && noCurrentInstanceState;

  const _getInstanceState = useCallback(() => dispatch(getInstanceState(userInstanceId)), [dispatch, userInstanceId]);

  useEffect(() => {
    if (requestInstanceState) {
      _getInstanceState();
    }
  }, [_getInstanceState, requestInstanceState]);

  const enhancedInstanceState: EnhancedInstanceState = makeEnhancedInstanceState(currentInstanceState);
  enhancedInstanceState.refresh = _getInstanceState;

  // FOR TESTING ONLY
  // Uncomment any lines to simulate various trial states
  // enhancedInstanceState.publishLimitExpired = true;
  // enhancedInstanceState.recordLimitExpired = true;
  // enhancedInstanceState.trialExpired = true;

  return enhancedInstanceState;
};

/**
 * Pure function that returns a subset of properties as a flat object from provided InstanceState.
 * It does not modify the original object.
 *
 * @param instanceState
 * @returns `EnhancedInstanceState` object
 */
export const makeEnhancedInstanceState = (instanceState: InstanceState): EnhancedInstanceState => {
  // prettier-ignore
  // retain tabs for readability
  return {
    expiryDate:           instanceState.expiryDate || '',
    id:                   instanceState.instance?.syncariId || '',
    isTrial:              Boolean(instanceState.instance?.trial),
    numberOfRecordsLeft:  instanceState.numberOfRecordsLeft || 0,
    pipelineCount:        instanceState.numberofPipelines ?? 0,
    publishLimitExpired:  instanceState.publishLimitExpired || false,
    recordLimit:          Number(instanceState.instance?.quota?.find(quota => quota.type === InstanceQuotaType.records)?.value) || 0,
    recordLimitExpired:   Boolean(instanceState.recordLimitExpired),
    synapseCount:         instanceState.numberofSynapses ?? 0,
    trialDaysLeft:        instanceState.trialDaysLeft || 0,
    trialExpired:         Boolean(instanceState.trialExpired),
  };
};
