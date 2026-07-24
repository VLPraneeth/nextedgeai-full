import { useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';

import {
  selectCurrentInstance,
  selectOrg,
  selectSyncStudioUserPrefs,
  selectUserCapabilities,
  selectUserData,
  selectUserTimezone,
} from './selectors';

export const useIsTrialUser = () => {
  const isInTrialInstance = useEnhancedSelector((state) => state.user.currentInstanceType === 'trial');

  // Local storage used for testing UI changes for trial instances
  const simulateTrial = localStorage.getItem(AppConstants.SIMULATE_TRIAL_INSTANCE) === 'true';

  return simulateTrial || isInTrialInstance;
};

export const useUserCapabilities = () => useEnhancedSelector(selectUserCapabilities);
export const useUserData = () => useEnhancedSelector(selectUserData);
export const useUserTimezone = () => useEnhancedSelector(selectUserTimezone);

export const useSelectOrg = () => useEnhancedSelector(selectOrg);
export const useSelectCurrentInstance = () => useEnhancedSelector(selectCurrentInstance);

export const useSelectSyncStudioFieldFilterForEntity = (entityId: string) => {
  const syncStudioPreferences = useEnhancedSelector(selectSyncStudioUserPrefs);
  return {
    filterSelections: syncStudioPreferences?.filterSelections[entityId] || EMPTY_ARRAY,
    hiddenFields: syncStudioPreferences?.hiddenFields[entityId] || EMPTY_ARRAY,
  };
};
