//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import classNames from 'classnames';
import cx from 'classnames';
import { useRef, MutableRefObject } from 'react';

import Link from 'components/Link';
import { useChangeAwareNavigation } from 'hooks/useNavigateTo';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import { AllPermissions } from 'utils/PermissionsConstants';

import { useHideTooltipOnNavigation } from './Navigation.hooks';

import './SideMenuItem.scss';

// items used in left side main navigation

type SideMenuItemProps = {
  selected: boolean;
  inactiveIcon?: React.FC;
  activeIcon?: React.FC;
  className?: string;
  title?: string;
  navigationStatus: string;
  path?: string;
  isCollapsed: boolean;
  permission?: AllPermissions | AllPermissions[];
  isForbiddenToastVisibleRef?: MutableRefObject<boolean | null>;
};

function SideMenuItem({
  inactiveIcon,
  activeIcon,
  className,
  title,
  navigationStatus,
  path,
  selected,
  isCollapsed,
  isForbiddenToastVisibleRef,
  permission,
}: SideMenuItemProps) {
  const cxMenuItem = classNames(
    `main-nav-menu-item__container`,
    className,
    `main-nav-menu-item__container--${navigationStatus}`,
    selected && `main-nav-menu-item__container--selected`
  );

  const tooltipRef = useRef<Tooltip | null>(null);
  useHideTooltipOnNavigation(tooltipRef);

  const navigateTo = useChangeAwareNavigation();

  // this is used for the collapse Button
  if (path === undefined) {
    return (
      <div aria-label={title}>
        <Tooltip
          mouseEnterDelay={1}
          overlayStyle={{ display: !isCollapsed ? 'none' : '' }}
          placement="right"
          ref={tooltipRef}
          title={title}>
          <div data-testid={`nav-menu-item-${title}`} className={cxMenuItem}>
            <Icon className="main-nav-menu-item__icon" component={selected ? activeIcon : inactiveIcon} />
            <span
              className={cx(
                'main-nav-menu-item__title',
                `main-nav-menu-item__title--${navigationStatus}`,
                selected && 'main-nav-menu-item__title--selected'
              )}>
              {!isCollapsed && title}
            </span>
          </div>
        </Tooltip>
      </div>
    );
  }

  return (
    <div data-testid={`nav-menu-item-${title}`} aria-label={title}>
      <Link
        title={title}
        tooltipRef={tooltipRef}
        isForbiddenToastVisibleRef={isForbiddenToastVisibleRef}
        isCollapsed={isCollapsed}
        permission={permission}
        permissionOperator={PermissionsComparisonOperator.AND}
        onClick={(e: React.MouseEvent<HTMLAnchorElement, MouseEvent>) => navigateTo(path!, e)}
        className={cxMenuItem}
        to={path!}>
        <Icon className="main-nav-menu-item__icon" component={selected ? activeIcon : inactiveIcon} />
        <span
          className={cx(
            'main-nav-menu-item__title',
            `main-nav-menu-item__title--${navigationStatus}`,
            selected && 'main-nav-menu-item__title--selected'
          )}>
          {title}
        </span>
      </Link>
    </div>
  );
}

export default SideMenuItem;
