//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useLocation } from '@reach/router';
import { Layout } from 'antd';
import { useState } from 'react';
import { useTransition, useSpring, animated } from 'react-spring';

import { HeaderProfileMenu } from 'components/header-profile-menu/HeaderProfileMenu';
import InstanceSwitcherMenuItem from 'components/InstanceSwitcherMenuItem';
import { LaunchTourButton } from 'components/launch-tour-button/LaunchTourButton';
import { NotificationPanel } from 'components/notifications/NotificationPanel';
import { NotificationPanelControls } from 'components/notifications/NotificationPanelControls';
import { NotificationPanelTrigger } from 'components/notifications/NotificationPanelTrigger';
import SyncStudioSearch from 'components/studio-search/SyncStudioSearch';
import useDimensions from 'hooks/useDimensions';
import RouteConstants from 'utils/RouteConstants';

import { Breadcrumbs } from './breadcrumbs';
import { BreadcrumbTitle } from './breadcrumbs/BreadcrumbTitle';
import HelpMenu from './HelpMenu';

import './MainHeader.less';

function MainHeader() {
  const [notificationsIsOpen, setNotificationsIsOpen] = useState(false);

  const _toggleDropdown = () => setNotificationsIsOpen((notificationsIsOpen) => !notificationsIsOpen);
  const _closeDropdown = () => setNotificationsIsOpen(false);

  // Our menu is split into 2 sections,
  // [Instance Switcher] [Bell] | [Help] [Launch tour] [User Menu]
  //
  // On the left, we have the instance switcher + bell. These items will
  // be animated to the left as the Notification Panel slides in.
  // Items on the right will be animated out and replaced
  // with the Notifications Panel Controls (mark as read / close).
  // In order to animate the left items to the left and then animate back to the right
  // properly, we need to measure the width of the Right side items. We use this width to
  // determine the minWidth of the div. When the panel is open, we push the minWidth up to
  // 300px so that the bell lines up with the panel, and then we animate the minWidth back down
  // to the original width. If we don't do this, the mounting/unmounting of the right side items
  // will cause layout bounces.
  const [userMenuItemsRef, userMenuItemsDimensions] = useDimensions();
  const userMenuItemsWidth = `${userMenuItemsDimensions.width || 150}px`;

  // this is used to animate the right side menu item wrapper minWidth
  // if the panel is open, set minWidth to 296px so that the bell slides to the left to
  // match the panel width. Otherwise, set minWidth to the width of the user items div
  // that we measured above
  const userMenuItemsContainerAnimStyles = useSpring({
    to: { minWidth: !notificationsIsOpen ? userMenuItemsWidth : '296px' },
  });

  // this handles the transitions as our right side menu items enter/leave the DOM
  const menuItemsTransition = useTransition(notificationsIsOpen, {
    from: { opacity: 0 },
    enter: { display: 'flex', opacity: 1 },
    leave: { opacity: 0 },
    // Delay animation stages so first is completely gone before the replacement fades in
    trail: 800,
    order: ['leave', 'enter', 'update'],
  });

  // animation for the notification panel open/close
  // use 349px so that it looks like it's attached at the right side instead of
  // showing the border and box-shadow
  const notificationTransitions = useTransition(notificationsIsOpen, {
    from: { transform: 'translateX(0px)' },
    enter: { transform: 'translateX(-349px)' },
    leave: { transform: 'translateX(0px)' },
  });

  const location = useLocation();
  const isSyncStudio = location.pathname?.includes(RouteConstants.SYNC_STUDIO);

  return (
    <Layout.Header className="main-header">
      <Breadcrumbs />
      <BreadcrumbTitle />

      <animated.div className="header-menu">
        {isSyncStudio && <SyncStudioSearch />}
        <InstanceSwitcherMenuItem />
        <NotificationPanelTrigger onClick={_toggleDropdown} isOpen={notificationsIsOpen} />

        <animated.div ref={userMenuItemsRef} style={userMenuItemsContainerAnimStyles}>
          {menuItemsTransition((animStyles, item) =>
            item ? (
              <animated.span className="header-dropdown-controls-container" style={animStyles}>
                <NotificationPanelControls onClose={_closeDropdown} />
              </animated.span>
            ) : (
              <animated.span className="header-help-profile-items-container" style={animStyles}>
                <HelpMenu />
                <LaunchTourButton /> {/* only appears for trial instances*/}
                <HeaderProfileMenu />
              </animated.span>
            )
          )}
        </animated.div>

        {notificationTransitions(
          (props, item) =>
            item && (
              <animated.div style={{ ...props, zIndex: 1000 }}>
                <NotificationPanel isOpen={notificationsIsOpen} handleClose={_closeDropdown} />
              </animated.div>
            )
        )}
      </animated.div>
    </Layout.Header>
  );
}

export default MainHeader;
