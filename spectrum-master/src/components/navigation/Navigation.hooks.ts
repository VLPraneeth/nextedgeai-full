import { useLocation } from '@reach/router';
import { Tooltip } from 'antd';
import { MutableRefObject, useEffect } from 'react';

import usePreviousValue from 'hooks/usePreviousValue';

export const useHideTooltipOnNavigation = (tooltipRef: MutableRefObject<Tooltip | null>) => {
  const { pathname } = useLocation();
  const previousPathname = usePreviousValue(pathname);

  useEffect(() => {
    if (previousPathname && pathname !== previousPathname) {
      tooltipRef.current?.setState({ visible: false });
    }
  }, [pathname, previousPathname, tooltipRef]);
};
