//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { UserRole } from 'store/access-control/types';
import { AllRolesAllInstance, UserState } from 'store/user/types';

export const validateFormData = (userRoles: UserState['userRoles'], orgAdmin: boolean) => {
  if (orgAdmin) {
    return true;
  } else {
    // ensure we have roles for each selected instance, and that we
    // have at least 1 instance selected
    const allUserRoles = Object.values(userRoles);
    return allUserRoles.length && allUserRoles.every((items) => items?.length > 0);
  }
};

export const getRolesForUser = (allInstanceRoles: AllRolesAllInstance, userId: string) => {
  let userRoles: Record<string, string[]> = {};
  let isOrgAdmin: boolean = false;

  Object.keys(allInstanceRoles).forEach((key) => {
    const instanceRoles: UserRole[] = allInstanceRoles[key];
    const userInstanceRoles: string[] = [];

    instanceRoles.forEach((role) => {
      if (role.users.length > 0) {
        const user = role.users.find((user) => user.id === userId);

        if (user) {
          userInstanceRoles.push(role.name);
          isOrgAdmin = user.orgAdmin;
        }
      }
    });

    if (userInstanceRoles.length > 0) {
      userRoles = { ...userRoles, [key]: userInstanceRoles };
    }
  });

  return { userRoles, isOrgAdmin };
};
