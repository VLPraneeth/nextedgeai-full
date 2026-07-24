//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Children, cloneElement, ReactElement } from 'react';

import { PermissionsComparisonOperator, useUserHasPermission } from 'hooks/useUserHasPermission';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { tCommon } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import PermissionErrorTooltip from './tooltip/PermissionErrorTooltip';

export enum PermissionErrorModes {
  DisableChildComponent = 'DisableChildComponent',
  ReplaceWithText = 'ReplaceWithText',
}

interface CanProps {
  capability?: string[];
  children: ReactElement | ReactElement[];
  key?: string;
  errorMode?: PermissionErrorModes;
  restrict?: string[];
  permission?: AllPermissions | AllPermissions[];
  permissionOperator?: PermissionsComparisonOperator;
  permissionErrorTooltip?: string;
  customReplacedText?: string;
}

/**
 * Conditionally render UI based on specific capabilities of a User
 *
 * @param {string[]} capability array of capabilities that allow a user to see `children`
 * @param {ReactElement} children UI elements to be conditionally shown/hidden
 * @param {string} [key] {optional} string to enable use of <Can /> within Ant <Menu />
 * @param {string[]} restrict array of capabilities that prevent a user seeing `children`. Overrides capability match.
 * permissions
 * @param {AllPermissions | AllPermissions[]} permission array of permissions or permission string to enable permissions handling for child components
 * @param {PermissionErrorModes} mode determine how the can should handle the children component
 * @param {string} customReplacedText string: replaces children components with default error text. string: replaces children components with given text
 * @param {string} permissionErrorTooltip boolean: wraps children in default permissions error tooltip. string: whaps children in custom tooltip. string is the message that is passed.


 *
 * Key and any unlisted props attached (including from HOCs, wrappers, etc.) will
 * be passed to all immediate children
 */

// NOTE: capability will eventually be replaced with permissions

const Can = ({
  children,
  capability,
  key,
  errorMode = PermissionErrorModes.DisableChildComponent,
  restrict = [],
  customReplacedText,
  permission,
  permissionOperator = PermissionsComparisonOperator.OR,
  permissionErrorTooltip,
  ...restProps
}: CanProps) => {
  const { userCan } = useUserRolesForCurrentInstance();
  const { userHasPermission } = useUserHasPermission(permissionOperator);

  if ((capability && !userCan(capability)) || userCan(restrict)) {
    return null;
  }

  // permissions
  if (!!permission && !userHasPermission(permission)) {
    if (errorMode === PermissionErrorModes.ReplaceWithText) {
      if (customReplacedText) {
        return <p className="disabled-center-align">{customReplacedText}</p>;
      } else {
        return <p className="disabled-center-align">{tCommon('disabled_message')}</p>;
      }
    }
    // use error message in tooltip or custom message
    if (errorMode === PermissionErrorModes.DisableChildComponent) {
      return (
        <PermissionErrorTooltip title={permissionErrorTooltip}>
          {Children.map(children, (child) => {
            return cloneElement(child, {
              ...child.props,
              key,
              disabled: true,
              style: { pointerEvents: 'none' },
              ...restProps,
            });
          })}
        </PermissionErrorTooltip>
      );
    }

    return null;
  }

  return (
    <>
      {Children.map(children, (child) => {
        return cloneElement(child, { key, ...restProps, ...child.props });
      })}
    </>
  );
};

export default Can;
