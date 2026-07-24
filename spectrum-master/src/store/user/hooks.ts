import { useCallback, useMemo } from 'react';

import { useEnhancedSelector, useEnhancedSelector as useSelector } from 'hooks/redux';
import { can } from 'utils/AppUtil';
import CapConstants from 'utils/CapConstants';
import { syncariEmailRegEx } from 'utils/RegexUtil';
import { isNotNull } from 'utils/TypeUtils';

import { selectUserCapabilities, selectUserGhosted } from './selectors';

export const useUserRolesForCurrentInstance = () => {
  const ghosted = useSelector(selectUserGhosted);
  const isGhostUser = useSelector((state) => state.user.isGhostUser);
  const isSuperAdmin = useSelector((state) => state.user.isSuperAdmin);
  const orgAdmin = useSelector((state) => state.user.orgAdmin);
  const selectedUserCapabilities = useSelector(selectUserCapabilities);

  // Note were currently user roles as capabilities now. The long term goal
  // is capabilities will represent a set of roles, subscriptions, , environment, etc...
  const userCapabilities = useMemo(
    () =>
      [
        ...selectedUserCapabilities,
        ghosted ? CapConstants.GHOSTED : null,
        isGhostUser ? CapConstants.IS_GHOST_USER : null,
        isSuperAdmin ? CapConstants.SUPER_ADMIN : null,
        orgAdmin ? CapConstants.ADMIN : null,
      ].filter(isNotNull),
    [ghosted, isGhostUser, isSuperAdmin, orgAdmin, selectedUserCapabilities]
  );

  const userCan = useCallback(
    (requiredCapabilities: Parameters<typeof can>[1]) => can(userCapabilities, requiredCapabilities),
    [userCapabilities]
  );

  return useMemo(
    () => ({
      userCan,
      roles: {
        superAdmin: userCapabilities.includes(CapConstants.SUPER_ADMIN),
        admin: userCapabilities.includes(CapConstants.ADMIN),
        instanceAdmin: userCapabilities.includes(CapConstants.INSTANCE_ADMIN),
        orgAdmin: userCapabilities.includes(CapConstants.ADMIN),
      },
    }),
    [userCan, userCapabilities]
  );
};

export const useUserInfo = () => {
  const { email } = useEnhancedSelector((state) => state.user);

  return {
    isSyncariUser: syncariEmailRegEx.test(email),
  };
};
