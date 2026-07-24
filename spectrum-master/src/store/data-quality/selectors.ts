import { RootState } from 'store/types';
import AppConstants from 'utils/AppConstants';

export const selectDfiRulesForEntity = (state: RootState, entityId: string = '') =>
  state.dataQuality.dfiRulesByEntity[entityId];

export const selectDfiRulesStatusForEntity = (state: RootState, entityId: string = '') =>
  state.dataQuality.dfiRulesStatusByEntity[entityId] || AppConstants.FETCH_STATUS.IDLE;

export const selectDfiRulesErrorForEntity = (state: RootState, entityId: string = '') =>
  state.dataQuality.dfiRulesErrorByEntity[entityId];

export const selectDfiRulesReculatingForEntity = (state: RootState, entityId: string = '') =>
  state.dataQuality.dfiRulesRecalculatingByEntity[entityId];
