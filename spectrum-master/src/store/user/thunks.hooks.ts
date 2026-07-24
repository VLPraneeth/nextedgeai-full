import { useMemo } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { UserPrefKeys } from 'utils/AppConstants.types';

import { setUserPreference } from './thunks';

export const useSetUserPreference = () => {
  const dispatch = useEnhancedDispatch();
  return useMemo(
    () => (prefKey: UserPrefKeys, prefJson: any, refresh: boolean = false) =>
      (setUserPreference(prefKey, prefJson, refresh) as any)(dispatch),
    [dispatch]
  );
};
