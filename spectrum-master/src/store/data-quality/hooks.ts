import { useEffect, useMemo } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { DQS_ROOT_DASHBOARD_ID } from 'pages/data-quality-studio';
import AppConstants from 'utils/AppConstants';

import {
  selectDfiRulesErrorForEntity,
  selectDfiRulesForEntity,
  selectDfiRulesReculatingForEntity,
  selectDfiRulesStatusForEntity,
} from './selectors';
import { getDfiRulesForEntity } from './thunks';

export const useSelectDfiEditingRule = (entityId?: string) =>
  useEnhancedSelector((state) => {
    if (entityId) {
      return state.dataQuality.dfiRulesByEntity[entityId]?.rules?.find(
        (rule) => rule.id === state.dataQuality.dfiRuleDetailsRuleId
      );
    }
  });

export const useSelectDfiRulesForEntity = (entityId?: string) =>
  useEnhancedSelector((state) => selectDfiRulesForEntity(state, entityId));

export const useSelectDfiRulesRecalculatingProgressForEntity = (entityId?: string) => {
  const result = useEnhancedSelector((state) => selectDfiRulesReculatingForEntity(state, entityId)) || {
    progressPercentage: 0,
    completed: true,
  };

  return {
    ...result,
    recalculating: !result.completed,
  };
};

export const useDfiRulesForEntity = (entityId?: string) => {
  const dispatch = useEnhancedDispatch();

  const responseData = useSelectDfiRulesForEntity(entityId);
  const status = useEnhancedSelector((state) => selectDfiRulesStatusForEntity(state, entityId));
  const error = useEnhancedSelector((state) => selectDfiRulesErrorForEntity(state, entityId));

  useEffect(() => {
    if (entityId && entityId !== DQS_ROOT_DASHBOARD_ID) {
      dispatch(getDfiRulesForEntity({ entityId }));
    }
  }, [dispatch, entityId]);

  return useMemo(() => {
    return {
      data: responseData,
      error,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      status,
    };
  }, [responseData, error, status]);
};
