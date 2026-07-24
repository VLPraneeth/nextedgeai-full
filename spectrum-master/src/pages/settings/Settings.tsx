//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, RouteComponentProps, Router } from '@reach/router';
import { Icon, Menu } from 'antd';
import { ClickParam } from 'antd/lib/menu';
import SubMenu from 'antd/lib/menu/SubMenu';
import { useEffect, useMemo, useState } from 'react';

import { ReactComponent as PipelineIcon } from 'assets/icons/pipeline.svg';
import { ReactComponent as SsoIcon } from 'assets/icons/sso.svg';
import { ReactComponent as CredentialsIcon } from 'assets/images/settings/credentials-icon.svg';
import { ReactComponent as DataStoreIcon } from 'assets/images/settings/datastore-icon.svg';
import { ReactComponent as InsightsSharingIcon } from 'assets/images/settings/insights-sharing-icon.svg';
import { ReactComponent as ErrorNotificationIcon } from 'assets/images/settings/notification-icon.svg';
import { ReactComponent as SpecterIcon } from 'assets/images/settings/specter-icon.svg';
import { ReactComponent as SubscriptionIcon } from 'assets/images/settings/subscription-icon.svg';
import Can from 'components/Can';
import Redirect from 'components/Redirect';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import CapConstants from 'utils/CapConstants';
import { t, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { getPathKey, replaceToken } from 'utils/UrlUtil';
import { useAbacEnabled } from './utils/useAbacEnabled';
import { useBrandingEnabled } from './utils/useBrandingEnabled';

import RoleBasedAccessControl from './access-control/role-based/RoleBasedAccessControl';
import CredentialList from './credential/CredentialList';
import ConfigureDataStore from './datastore/ConfigureDataStore';
import InsightsSharing from './InsightsSharing';
import InstanceList from './instance/InstanceList';
import ErrorNotifications from './notifications/ErrorNotifications';
import RealtimePipelineSettings from './realtime-pipeline/RealtimePipelineSettings';
import Specter from './specter/Specter';
import SsoConfig from './SsoConfig';
import SubscriptionList from './subscription/SubscriptionList';
import SubscriptionProfile from './subscription/SubscriptionProfile';
import UserList from './user/UserList';
import Branding from './Branding';
import { ReactComponent as BrandingLogo } from 'assets/icons/paint-palette-brush.svg';

import AttributeBasedAccessControl from './access-control/attribute-based/AttributeBasedAccessControl';
import DebugFlag from './debug-flag/DebugFlag';
import DataFixQueries from './data-fix/DataFixQueries';
import GhostAccess from './ghost-access/GhostAccess';
import './Settings.less';
import { getIconFromPath } from 'components/icons/Icons';
import { wrapIcon } from 'utils/IconUtils';

export interface SettingsProps extends RouteComponentProps {
  children?: React.ReactNode;
}

const tn = tNamespaced('Settings');

const Settings = ({ location, children }: SettingsProps) => {
  const [selectedKey, setSelectedKey] = useState('');
  const { roles } = useUserRolesForCurrentInstance();
  const abacEnabled = useAbacEnabled();
  const brandingEnabled = useBrandingEnabled();

  const settingsKeys = useMemo(() => {
    const accessControlKeys = {
      accessControl: 'access-control',
      roleBasedAccessControl: 'access-control/role-based',
      roleBasedAccessControlView: 'access-control/role-based/id/view',
      attributeBasedAccessControl: 'access-control/attribute-based',
      realtimePipelines: 'realtime-pipelines',
    };

    const errorNotificationsKeys = {
      errorNotifications: 'notifications',
      errorNotificationsWebhook: 'notifications/webhook',
      errorNotificationsEmail: 'notifications/email',
      errorNotificationsWebhookNew: 'notifications/webhook/add',
      errorNotificationsWebhookEdit: 'notifications/webhook/edit',
      errorNotificationsEmailNew: 'notifications/email/add',
      errorNotificationsEmailEdit: 'notifications/email/edit',
    };

    return {
      subProfile: 'subscription-profile',
      subscription: 'subscription',
      branding: 'branding',
      users: 'user',
      ...accessControlKeys,
      instance: 'instance',
      debugFlag: 'debug-flag',
      specter: 'specter',
      creds: 'credential',
      dataStore: 'datastore',
      dataStoreConfigure: 'datastore/configure',
      dataFix: 'data-fix',
      ghostAccess: 'ghost-access',
      sso: 'sso',
      ...errorNotificationsKeys,
      insightsSharing: 'insights-sharing',
    };
  }, []);

  const onSideNavClick = (param: ClickParam) => {
    navigate(replaceToken(RouteConstants.SETTINGS_TYPE, { type: param.key }));
    setSelectedKey(param.key);
  };

  useEffect(() => {
    if (!location) {
      return;
    }

    if (location.pathname === RouteConstants.SETTINGS) {
      // Redirect to default settings page
      if (roles.instanceAdmin) {
        // Instance Admins cannot access subscription profile, default to Instances
        setSelectedKey(settingsKeys.instance);
        navigate(replaceToken(RouteConstants.SETTINGS_TYPE, { type: settingsKeys.instance }));
      } else {
        // Org Admins and Super Admins default to Subscription Profile
        setSelectedKey(settingsKeys.subProfile);
        navigate(replaceToken(RouteConstants.SETTINGS_TYPE, { type: settingsKeys.subProfile }));
      }
    } else {
      // Set the key to the matching sub url
      // eslint-disable-next-line no-useless-escape
      const pathname = location.pathname;
      let key = getPathKey(pathname.replace(/^\/[^\/]+\//, ''));
      // To match the path with dynamic id for edit routes
      if (pathname.match(/[a-zA-Z0-9]+\/edit$/)) {
        key = key.replace(/[a-zA-Z0-9]+\/edit$/, 'edit');
      }

      if (Object.values(settingsKeys).includes(key)) {
        if (key.includes(settingsKeys.errorNotifications)) {
          setSelectedKey(settingsKeys.errorNotifications);
        } else {
          setSelectedKey(key);
        }
      } else {
        // if route doesn't exist, navigate to base page
        navigate('/settings');
      }
    }
  }, [location, roles.instanceAdmin, settingsKeys]);

  return (
    <div className="settings">
      <div className="settings__menu">
        <Menu mode="inline" selectedKeys={[selectedKey]} onClick={onSideNavClick} className="h-full">
          {/* Subscription Profile */}
          <Menu.Item key={settingsKeys.subProfile}>
            <Icon component={(props) => <SubscriptionIcon {...props} />} />
            <span>{tn('SubProfile.page_title')}</span>
          </Menu.Item>

          {/* Subscriptions */}
          <Can
            capability={[CapConstants.SUPER_ADMIN, CapConstants.IS_GHOST_USER]}
            key={settingsKeys.subscription}
            restrict={[CapConstants.GHOSTED]}>
            <Menu.Item>
              <Icon type="wallet" />
              <span>{tn('Subscriptions.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Branding */}
          {brandingEnabled && (
            <Can
              capability={[
                CapConstants.SUPER_ADMIN,
                CapConstants.INSTANCE_ADMIN,
                CapConstants.ADMIN,
                CapConstants.VIEWER,
                CapConstants.IS_GHOST_USER,
              ]}
              key={settingsKeys.branding}
              restrict={[CapConstants.GHOSTED]}>
              <Menu.Item>
                <Icon
                  component={wrapIcon(() => (
                    <BrandingLogo />
                  ))}
                />
                <span>{tn('Branding.page_title')}</span>
              </Menu.Item>
            </Can>
          )}

          {/* Instances */}
          <Can permission={AllPermissions.LIST_INSTANCE} key={settingsKeys.instance}>
            <Menu.Item>
              <Icon type="database" />
              <span>{tn('Instances.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Users */}
          <Can
            permission={[AllPermissions.LIST_USER, AllPermissions.LIST_INSTANCE]}
            permissionOperator={PermissionsComparisonOperator.AND}
            key={settingsKeys.users}>
            <Menu.Item>
              <Icon type="user" />
              <span>{tn('Users.page_title')}</span>
            </Menu.Item>
          </Can>

          <Can permission={AllPermissions.LIST_ROLES}>
            <SubMenu
              title={
                <span>
                  <Icon type="lock" />
                  <span>{tn('AccessControl.title')}</span>
                </span>
              }
              key={settingsKeys.accessControl}>
              <Menu.Item key={settingsKeys.roleBasedAccessControl}>
                <span>{tn('AccessControl.role_based')}</span>
              </Menu.Item>

              {abacEnabled && (
                <Menu.Item key={settingsKeys.attributeBasedAccessControl}>
                  <span>{tn('AccessControl.attribute_based')}</span>
                </Menu.Item>
              )}
            </SubMenu>
          </Can>

          {/* Service Credentials */}
          <Can permission={AllPermissions.SERVICE_CREDENTIAL} key={settingsKeys.creds}>
            <Menu.Item>
              <Icon component={(props) => <CredentialsIcon {...props} />} />
              <span>{tn('ServiceCredentials.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Specter */}
          <Can
            capability={[CapConstants.SUPER_ADMIN, CapConstants.IS_GHOST_USER, CapConstants.GHOSTED]}
            key={settingsKeys.specter}>
            <Menu.Item>
              <Icon component={(props) => <SpecterIcon {...props} />} />
              <span>{tn('Specter.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Data Store */}
          <Can
            capability={[
              CapConstants.ADMIN,
              CapConstants.INSTANCE_ADMIN,
              CapConstants.SUPER_ADMIN,
              CapConstants.GHOSTED,
              CapConstants.IS_GHOST_USER,
            ]}
            key={settingsKeys.dataStore}>
            <SubMenu
              title={
                <span>
                  <Icon component={(props) => <DataStoreIcon {...props} />} />
                  <span>{tn('DataStore.page_title')}</span>
                </span>
              }
              key={settingsKeys.dataStore}>
              <Menu.Item key={settingsKeys.dataStoreConfigure}>
                <span>{tn('DataStore.configure')}</span>
              </Menu.Item>
            </SubMenu>
          </Can>

          {/* SSO */}
          <Can permission={AllPermissions.READ_SSO} key={settingsKeys.sso}>
            <Menu.Item>
              <Icon component={(props) => <SsoIcon {...props} />} />
              <span>{tn('SsoConfig.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Error notifications */}
          <Can
            permission={[AllPermissions.READ_ERROR_NOTIFICATION_EMAIL, AllPermissions.READ_ERROR_NOTIFICATION_WEBHOOK]}
            key={settingsKeys.errorNotifications}>
            <Menu.Item>
              <Icon component={(props) => <ErrorNotificationIcon {...props} />} />
              <span>{tn('ErrorNotifications.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Insights Sharing */}

          <Can permission={[AllPermissions.READ_ALLOWED_DOMAINS]} key={settingsKeys.insightsSharing}>
            <Menu.Item>
              <Icon component={(props) => <InsightsSharingIcon {...props} />} />
              <span>{tn('InsightsSharing.page_title')}</span>
            </Menu.Item>
          </Can>

          <Can permission={[AllPermissions.READ_STUDIO]} key={settingsKeys.realtimePipelines}>
            <Menu.Item key={settingsKeys.realtimePipelines}>
              <Icon component={(props) => <PipelineIcon {...props} />} />
              <span>{t('RealtimePipeline.realtime_pipeline_plural')}</span>
            </Menu.Item>
          </Can>

          {/* Debug Flag */}
          <Can permission={AllPermissions.READ_DEBUG_MODE} key={settingsKeys.debugFlag}>
            <Menu.Item>
              <Icon type="bug" />
              <span>{tn('DebugFlag.page_title')}</span>
            </Menu.Item>
          </Can>

          {/* Data Fix */}
          <Can
            capability={[CapConstants.SUPER_ADMIN, CapConstants.IS_GHOST_USER, CapConstants.GHOSTED]}
            key={settingsKeys.dataFix}>
            <Menu.Item>
              <Icon type="tool" />
              <span>Data Fix</span>
            </Menu.Item>
          </Can>

          {/* Ghost Access */}
          <Can capability={[CapConstants.SUPER_ADMIN, CapConstants.IS_GHOST_USER]} key={settingsKeys.ghostAccess}>
            <Menu.Item>
              <Icon type="team" />
              <span>Ghost Access</span>
            </Menu.Item>
          </Can>
        </Menu>
      </div>
      <Router className="settings__content">
        <CredentialList path="/credential" />
        <Redirect path="datastore" redirectTo="/settings/datastore/configure" replace />
        <ConfigureDataStore path="/datastore/configure" />
        <InstanceList path="/instance" />
        <DebugFlag path="/debug-flag" />
        <Specter path="/specter" />
        <RealtimePipelineSettings path="/realtime-pipelines" />
        <SsoConfig path="/sso" />
        <Branding path="/branding" />
        <SubscriptionList path="/subscription" />
        <SubscriptionProfile path="/subscription-profile" />
        <UserList path="/user" />
        <RoleBasedAccessControl path="/access-control/role-based/*" />
        <AttributeBasedAccessControl path="/access-control/attribute-based/*" />
        <ErrorNotifications path="/notifications/*" />
        <InsightsSharing path="/insights-sharing/*" />
        <DataFixQueries path="/data-fix" />
        <GhostAccess path="/ghost-access" />
      </Router>
    </div>
  );
};

export default Settings;
