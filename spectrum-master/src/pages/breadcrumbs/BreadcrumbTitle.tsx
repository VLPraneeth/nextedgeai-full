import { globalHistory } from '@reach/router';
import { useEffect } from 'react';

import { useBreadcrumb } from './useBreadcrumb';

export const BreadcrumbTitle = () => {
  // Update the title for any url changes
  const { refreshTitle, urlNameMap } = useBreadcrumb();
  useEffect(() => {
    const unlisten = globalHistory.listen(() => {
      refreshTitle();
    });
    return () => unlisten();
  }, [refreshTitle]);

  // Update the title when a new entry is added
  useEffect(() => {
    refreshTitle();
  }, [refreshTitle, urlNameMap]);

  return null;
};
