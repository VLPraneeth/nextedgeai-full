import { useLocation } from '@reach/router';
import { isArray } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import { Error403 } from 'pages/errors/Error403';
import { FetchStatus } from 'store/types';
import { navigateTo } from 'utils/AppUtil';
import { tCommon as tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { compareRouteToPathname } from 'utils/UrlUtil';

import useToastForFetchStatusChange from './useToastForFetchStatusChange';
import { PermissionsComparisonOperator, useUserHasPermission } from './useUserHasPermission';

export interface RouteRedirectConfig {
  route: string | string[];
  routePermissions: AllPermissions | AllPermissions[];
  redirectTo: string;
}

export interface ForbiddenRedirectProps {
  studioPermissions: AllPermissions | AllPermissions[];
  operator?: PermissionsComparisonOperator;
  routeRedirects?: RouteRedirectConfig[];
}

export const useForbiddenRedirect = ({
  studioPermissions,
  operator = PermissionsComparisonOperator.OR,
  routeRedirects,
}: ForbiddenRedirectProps) => {
  const { userHasPermission } = useUserHasPermission(operator);
  const location = useLocation();

  const [shouldThrow403, setShouldThrow401] = useState(false);
  const [hasCheckedStudioRoot, setHasCheckedStudioRoot] = useState(false);
  const [redirectStatus, setRedirectStatus] = useState<FetchStatus>('idle');

  useToastForFetchStatusChange(redirectStatus, {
    error: tc('permission_error'),
  });

  // If the user doesn't have permission to view the root of the studio, then
  // return the 401 component from the hook.
  useEffect(() => {
    setHasCheckedStudioRoot(true);

    if (userHasPermission(studioPermissions) === false) {
      setShouldThrow401(true);
    }
  }, [studioPermissions, userHasPermission]);

  // If the user has access to the root of the studio but needs an addition set
  // of permissions to view a subroute, then handle the redirect logic here.
  useEffect(() => {
    if (hasCheckedStudioRoot && !shouldThrow403 && routeRedirects) {
      const config = routeRedirects.find((config) => {
        const roots = isArray(config.route) ? config.route : [config.route];

        return roots.some((root) => compareRouteToPathname(root, location.pathname));
      });

      if (config && userHasPermission(config.routePermissions) === false) {
        setRedirectStatus('error');
        navigateTo(config.redirectTo);
      }
    }
  }, [hasCheckedStudioRoot, location.pathname, routeRedirects, shouldThrow403, userHasPermission]);

  return useMemo(() => {
    // TODO: get rid of this NODE_ENV check; it was put here to fix tests
    return shouldThrow403 && process.env.NODE_ENV !== 'test' ? <Error403 /> : undefined;
  }, [shouldThrow403]);
};
