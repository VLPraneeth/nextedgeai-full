import { Avatar, Dropdown, Menu } from 'antd';
import { ClickParam } from 'antd/lib/menu';
import { useState } from 'react';

import ChangeAwareLink from 'components/ChangeAwareLink';
import { DropdownDisclosureArrow } from 'components/dropdown-disclosure-arrow/DropdownDisclosureArrow';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import InstanceFeatureModal from 'pages/instance-feature/InstanceFeatureModal';
import { showAboutPage } from 'store/user/actions';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { selectArcadeTarget } from 'store/user/selectors';
import { logout } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { t, tNamespaced } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';
import './HeaderProfileMenu.less';

const tn = tNamespaced('MainHeader');

export const HeaderProfileMenu = () => {
  const dispatch = useEnhancedDispatch();
  const username = useEnhancedSelector((state) => state.user.firstName || state.user.email);
  const arcadeTarget = useEnhancedSelector(selectArcadeTarget);
  const [isOpen, setIsOpen] = useState(false);
  const [instanceFeatureVisible, setInstanceFeatureVisible] = useState(false);
  const { userCan } = useUserRolesForCurrentInstance();

  const handleProfileMenuClick = (event: ClickParam) => {
    switch (event.key) {
      case 'about':
        dispatch(showAboutPage(true));
        break;
      case 'logout':
        dispatch(logout());
        break;
      case 'apiDocumentation':
        const url = AppConstants.API_URL.replace('$ARCADE_TARGET', arcadeTarget);
        window.open(url, '_target');
        break;
      case 'instance-feature':
        setInstanceFeatureVisible(true);
        break;
      case 'crashMe':
        throw new Error('Phone home test crash!');
      default:
        break;
    }
    setIsOpen(false);
  };
  const showFeatureModal = userCan([CapConstants.SUPER_ADMIN, CapConstants.GHOSTED, CapConstants.IS_GHOST_USER]);
  const isNotProduction = process.env.NODE_ENV !== 'production';
  const hasLowerMenuItems = isNotProduction || showFeatureModal;

  return (
    <span className="header-menu-item header-profile-menu">
      <Dropdown
        placement="bottomRight"
        align={{ offset: [0, 31] }}
        overlayClassName="profile-dropdown-menu"
        overlay={
          <Menu theme="light" onClick={handleProfileMenuClick} selectedKeys={[]}>
            <Menu.Item key="editProfile">
              <ChangeAwareLink to="/profile">{tn('profile')}</ChangeAwareLink>
            </Menu.Item>
            <Menu.Item key="about">{tn('about')}</Menu.Item>
            <Menu.Item key="logout">{tn('logout')}</Menu.Item>
            {hasLowerMenuItems ? <Menu.Divider data-testid="divider" /> : null}
            {showFeatureModal && <Menu.Item key="instance-feature">{t('InstanceFeatureModal.title')}</Menu.Item>}
            {isNotProduction ? <Menu.Item key="apiDocumentation">{tn('api_documentation')}</Menu.Item> : null}
            {isNotProduction ? <Menu.Item key="crashMe">{tn('crash_me')}</Menu.Item> : null}
          </Menu>
        }
        trigger={['click']}
        onVisibleChange={setIsOpen}>
        <a
          className="ant-dropdown-link header-profile-menu__trigger"
          data-userflow-tag={UserflowTags.Header.ProfileMenu}>
          <Avatar src={'/arcade/api/v1/user/photo'} alt="profile" size={'small'} style={{ marginRight: 5 }} />
          <span className="header-profile-menu__user-name" title={username}>
            {username}
          </span>
          <DropdownDisclosureArrow isOpen={isOpen} />
        </a>
      </Dropdown>
      <InstanceFeatureModal visible={instanceFeatureVisible} show={setInstanceFeatureVisible} />
    </span>
  );
};
