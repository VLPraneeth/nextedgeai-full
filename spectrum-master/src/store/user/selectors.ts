import { trim } from 'lodash';
import { createSelector } from 'reselect';

import { useEnhancedSelector as useSelector } from 'hooks/redux';
import { EMPTY_OBJECT } from 'store/constants';
import { Instance } from 'store/instances/slice';
import { RootState } from 'store/types';

import { UserRoles } from './types';

export const selectOrgId = (state: RootState) => state.user.orgId;
export const selectOrgName = (state: RootState) => state.user.orgName;
export const selectOrgLogo = (state: RootState) => state.user.orgLogo;

export const selectUserData = (state: RootState) => state.user;
export const selectUserId = (state: RootState) => state.user.id;
export const selectUserPasswordExpired = (state: RootState) => state.user.passwordExpired;
export const selectUserFirstName = (state: RootState) => state.user.firstName;
export const selectUserLastName = (state: RootState) => state.user.lastName;
export const selectUserEmail = (state: RootState) => state.user.email;
export const selectUserTimezone = (state: RootState) => state.user.timeZone;

export const selectCurrentInstanceName = (state: RootState) => state.user.currentInstanceName;
export const selectCurrentInstanceId = (state: RootState) => state.user.currentInstanceNextEdgeId;
export const selectCurrentInstanceType = (state: RootState) => state.user.currentInstanceType;

export const selectUserRoles = (state: RootState) => state.user.userRoles;
export const selectAllRoles = (state: RootState) => state.user.allRoles;

export const selectOrg = createSelector([selectOrgId, selectOrgName, selectOrgLogo], (id, name, logo) => ({
  id,
  name,
  logo,
}));

export const selectCurrentInstance = createSelector(
  [selectCurrentInstanceName, selectCurrentInstanceId, selectCurrentInstanceType],
  (name, id, type) => ({
    name,
    id,
    type,
  })
);

export const useSelectCurrentInstance = () => useSelector(selectCurrentInstance);

export const selectFetchingInstances = (state: RootState) => state.user.fetchingUserInstances;
export const selectAllUserInstances = (state: RootState) => state.user.instances;

export const useFilteredInstancesByOrg = (filterText: string) => {
  const allInstances = useSelector(selectAllUserInstances);

  const filter = filterText.trim().toLowerCase();
  const filteredInstances = !filter
    ? allInstances
    : allInstances.filter((instance) => {
        const name = instance.displayName?.toLowerCase() || '';
        const orgName = instance.orgName?.toLowerCase() || '';
        const instanceId = instance.syncariId?.toLowerCase() || '';

        return [name, orgName, instanceId].some((s) => s.includes(filter));
      });

  const instancesByOrg: Record<string, { id: string; name: string | null; instances: Instance[] }> = {};

  const filteredInstancesByOrg = filteredInstances.reduce((acc, instance) => {
    const { orgId, orgName } = instance;

    if (orgId !== null) {
      if (!(orgId in acc)) {
        acc[orgId] = {
          id: orgId,
          name: orgName,
          instances: [instance],
        };
      } else {
        acc[orgId].instances.push(instance);
      }
    }

    return acc;
  }, instancesByOrg);

  return [allInstances, filteredInstancesByOrg, filteredInstances] as const;
};

export const selectUserGhosted = (state: RootState) => state.user.ghosted;

export const selectUserFullName = createSelector([selectUserFirstName, selectUserLastName], (first, last) =>
  [first, last]
    .filter(Boolean)
    .map((str) => (str as string).trim())
    .join(' ')
);

export const selectUserName = createSelector(
  [selectUserFirstName, selectUserEmail],

  (firstName, email) => {
    firstName = trim(firstName);
    return firstName.length > 0 ? firstName : email;
  }
);

export const selectArcadeTarget = (state: RootState) => state.user.versionMetadata.arcadeTarget;

// extracts the role for this user on the current instance
export const selectCurrentInstanceUserRoles = createSelector(
  [selectUserRoles, selectCurrentInstanceId],
  (userRoles, currentInstanceId) => {
    const roles = currentInstanceId in userRoles ? userRoles[currentInstanceId] : [];
    return roles;
  }
);

// We're going to use user roles for now, we don't have capabilities yet
export const selectUserCapabilities = selectCurrentInstanceUserRoles;

// Users data, not current user
export const selectUser = (userId: string) => (state: RootState) => state.user.users.find((user) => user.id === userId);

export const selectUserRolesForUser = (state: RootState, userId: string): UserRoles =>
  state.user.users.find((user) => user.id === userId)?.userRoles || EMPTY_OBJECT;

export const selectIsUpdatingUser = (state: RootState, { userId }: { userId: string }) =>
  state.user.userUpdatesPending.includes(userId);

// User Preference Selectors
export const selectSchemaStudioEntityColumnsPrefs = (state: RootState) =>
  state.user.userPref.schemaStudio?.allEntityColumns || [];
export const selectSchemaStudioFieldColumnsPrefs = (state: RootState) =>
  state.user.userPref.schemaStudio?.allFieldColumns || [];

export const selectDataStudioColumnsForEntity = (state: RootState, entityId: string) =>
  state.user.userPref.dataStudio?.allColumns?.[entityId];

export const selectSyncStudioUserPrefs = (state: RootState) => state.user.userPref.syncStudio;
export const selectErrorNotificationUserPrefs = (state: RootState) => state.user.userPref.errorNotification;
export const selectThoughtspotToken = (state: RootState) => state.user.thoughtspotToken;
