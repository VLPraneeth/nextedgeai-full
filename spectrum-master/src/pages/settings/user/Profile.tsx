//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, RouteComponentProps } from '@reach/router';
import { Col, Menu, Row } from 'antd';
import { ClickParam } from 'antd/lib/menu';
import { Suspense, useEffect } from 'react';

import RouteSpin from 'components/RouteSpin';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

import './Profile.less';

export interface SettingsProps extends RouteComponentProps {
  children?: React.ReactNode;
}

const tn = tNamespaced('Profile');
const profileKeys = {
  editProfile: 'edit-profile',
};

const Profile = ({ location, children, ...rest }: SettingsProps) => {
  const onSideNavClick = (param: ClickParam) => {
    navigate(replaceToken(RouteConstants.PROFILE_TYPE, { type: param.key }), { replace: true });
  };
  const pathKey = location?.pathname.split('/').pop();

  useEffect(() => {
    if (!pathKey || !Object.values(profileKeys).includes(pathKey)) {
      // Invalid path, redirect to default profile page
      navigate(replaceToken(RouteConstants.PROFILE_TYPE, { type: profileKeys.editProfile }), { replace: true });
    }
  }, [location, pathKey]);

  return (
    <Row align={'top'} className="h-full synri-top-row">
      <Col className="h-full" span={4}>
        <Menu
          mode="inline"
          selectedKeys={[pathKey || profileKeys.editProfile]}
          onClick={onSideNavClick}
          className="h-full">
          <Menu.Item key={profileKeys.editProfile}>
            <span className="settings-label">{tn('profile')}</span>
          </Menu.Item>
        </Menu>
      </Col>
      <Col span={19}>
        <div className="settings-section">
          <Suspense fallback={<RouteSpin />}>{children}</Suspense>
        </div>
      </Col>
    </Row>
  );
};

export default Profile;
