import { FC } from 'react';

import { AllPermissions } from 'utils/PermissionsConstants';

import { useEnhancedSelector } from './redux';

export enum PermissionsComparisonOperator {
  AND = 'AND',
  OR = 'OR',
}

export function useUserHasPermission(operator: PermissionsComparisonOperator = PermissionsComparisonOperator.OR) {
  const userPermissions = useEnhancedSelector((state) => state.user?.privileges);
  const userHasPermission = (permissions: AllPermissions | AllPermissions[]) => {
    if (userPermissions === null) {
      return false;
    }

    if (Array.isArray(permissions)) {
      switch (operator) {
        case PermissionsComparisonOperator.OR:
          return permissions?.some((permission) => userPermissions?.includes(permission));
        case PermissionsComparisonOperator.AND:
          return permissions?.every((permission) => userPermissions?.includes(permission));
      }
    }

    return userPermissions?.includes(permissions);
  };

  return { userHasPermission };
}

export type UserHasPermissionChildren = (
  userHasPermission: (permissions: AllPermissions | AllPermissions[]) => void
) => any;

export interface UserHasPermissionProps {
  operator: PermissionsComparisonOperator;
  children: UserHasPermissionChildren;
}

// Returns the result of the hook as a render prop so it can be used
// inside of class based components.
export const UserHasPermission: FC<UserHasPermissionProps> = ({
  operator = PermissionsComparisonOperator.OR,
  children,
}) => {
  const { userHasPermission } = useUserHasPermission(operator);

  return children(userHasPermission);
};
