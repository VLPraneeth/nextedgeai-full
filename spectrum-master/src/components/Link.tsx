import { useLocation } from '@reach/router';
import { message, Tooltip } from 'antd';
import cx from 'classnames';
import { ReactNode, MutableRefObject } from 'react';

import { PermissionsComparisonOperator, useUserHasPermission } from 'hooks/useUserHasPermission';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import ChangeAwareLink from './ChangeAwareLink';

interface LinkProps {
  children: ReactNode;
  to: string;
  tooltipRef: MutableRefObject<Tooltip | null>;
  title?: string;
  isForbiddenToastVisibleRef?: MutableRefObject<null | boolean>;
  isCollapsed?: boolean;
  permission?: AllPermissions | AllPermissions[];
  permissionErrorTooltip?: boolean | string;
  permissionOperator?: PermissionsComparisonOperator;
  className?: string;
  [rest: string]: any;
}

/**
 * Custom link that returns a reach router link
 * @param {AllPermissions | AllPermissions[]} permission array of permissions or permission string to enable permissions handling for child components
 * @param {boolean | string} permissionErrorTooltip boolean: wraps children in default permissions error tooltip. string: whaps children in custom tooltip. string is the message that is passed.
 */

export default function Link({
  to,
  tooltipRef,
  title,
  isCollapsed,
  isForbiddenToastVisibleRef,
  permission,
  children,
  className,
  permissionErrorTooltip,
  permissionOperator = PermissionsComparisonOperator.OR,
  ...rest
}: LinkProps) {
  const { userHasPermission } = useUserHasPermission(permissionOperator);
  const location = useLocation();

  const handleClick = () => {
    if (isForbiddenToastVisibleRef && !isForbiddenToastVisibleRef.current) {
      isForbiddenToastVisibleRef.current = true;
      message.error(tc('permission_error'), 3, () => {
        isForbiddenToastVisibleRef.current = false;
      });
    }
  };

  if (permission && !userHasPermission(permission)) {
    return (
      <Tooltip mouseEnterDelay={1} placement="right" title={tc('permission_error')}>
        <ChangeAwareLink {...rest} className={cx(className, 'disabled')} to={location.pathname} onClick={handleClick}>
          {children}
        </ChangeAwareLink>
      </Tooltip>
    );
  }

  return (
    <Tooltip
      mouseEnterDelay={1}
      overlayStyle={{ display: !isCollapsed ? 'none' : '' }}
      placement="right"
      ref={tooltipRef}
      title={title}>
      <ChangeAwareLink className={className} to={to}>
        {children}
      </ChangeAwareLink>
    </Tooltip>
  );
}
