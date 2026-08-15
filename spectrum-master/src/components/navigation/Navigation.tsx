//
// NextEdge AI private application.
//
import { useLocation } from '@reach/router';
import { Layout } from 'antd';
import cx from 'classnames';
import { ReactEventHandler, useEffect, useRef, useState } from 'react';
import { useDispatch } from 'react-redux';
import type { ThunkDispatch } from '@reduxjs/toolkit';
import type { AnyAction } from 'redux';

import { ReactComponent as CollapseIconActive } from 'assets/syncari-icons/color/collapse.svg';
import { ReactComponent as DataQualityStudioIconActive } from 'assets/syncari-icons/color/data-quality-studio.svg';
import { ReactComponent as DataStudioIconActive } from 'assets/syncari-icons/color/data-studio.svg';
import { ReactComponent as ImportedFilesIconActive } from 'assets/syncari-icons/color/imported-files.svg';
import { ReactComponent as DashboardIconActive } from 'assets/syncari-icons/color/insights-studio.svg';
import { ReactComponent as TransactionsIconActive } from 'assets/syncari-icons/color/logs.svg';
import { ReactComponent as SchemaStudioIconActive } from 'assets/syncari-icons/color/schema-studio.svg';
import { ReactComponent as SettingsIconActive } from 'assets/syncari-icons/color/settings.svg';
import { ReactComponent as SynapseIconActive } from 'assets/syncari-icons/color/synapse-studio.svg';
import { ReactComponent as SyncStudioIconActive } from 'assets/syncari-icons/color/sync-studio.svg';
import { ReactComponent as FullLogo } from 'assets/syncari-icons/full-logo.svg';
import { ReactComponent as CollapseIconInactive } from 'assets/syncari-icons/grayscale/collapse.svg';
import { ReactComponent as DataQualityStudioIconInactive } from 'assets/syncari-icons/grayscale/data-quality-studio.svg';
import { ReactComponent as DataStudioIconInactive } from 'assets/syncari-icons/grayscale/data-studio.svg';
import { ReactComponent as ImportedFilesIconInactive } from 'assets/syncari-icons/grayscale/imported-files.svg';
import { ReactComponent as DashboardIconInactive } from 'assets/syncari-icons/grayscale/insights-studio.svg';
import { ReactComponent as TransactionsIconInactive } from 'assets/syncari-icons/grayscale/logs.svg';
import { ReactComponent as SchemaStudioIconInactive } from 'assets/syncari-icons/grayscale/schema-studio.svg';
import { ReactComponent as SettingsIconInactive } from 'assets/syncari-icons/grayscale/settings.svg';
import { ReactComponent as SynapseIconInactive } from 'assets/syncari-icons/grayscale/synapse-studio.svg';
import { ReactComponent as SyncStudioIconInactive } from 'assets/syncari-icons/grayscale/sync-studio.svg';
import SideMenuItem from 'components/navigation/SideMenuItem';
import useDimensions from 'hooks/useDimensions';
import usePersistedState from 'hooks/usePersistedState';
import { useLayoutContext } from 'pages/LayoutContext';
import { navigateTo } from 'utils/AppUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import { UserflowTags } from 'utils/UserflowTags';
import { checkCustomRuleAssignmentExists } from 'store/data-quality/thunks';
import { selectUserEmail } from 'store/user/selectors';
import { useEnhancedSelector } from 'hooks/redux';
import { isGuidedDemoAccount } from 'utils/GuidedDemo';

// import 'antd/dist/antd.css';
import './Navigation.scss';
import { useBrandingEnabled } from 'pages/settings/utils/useBrandingEnabled';

type SyncariLogoProps = {
  isCollapsed: boolean;
  onClick: ReactEventHandler;
  isBrandingEnabled?: boolean;
};

type MenuItemType = {
  className?: string;
  inactiveIcon: React.FC;
  activeIcon: React.FC;
  path: string;
  title: string;
  userflowTag?: string;
  permission?: AllPermissions | AllPermissions[];
  tourTarget?: string;
};

export const EXPANDED_WIDTH = 220;
export const COLLAPSED_WIDTH = 60;
const COLLAPSED = 'collapsed';
const EXPANDED = 'expanded';
const ENABLED = 'enabled';
const DISABLED = 'disabled';
export const PERSISTED_NAVIGATION_COLLAPSED = 'PERSISTED_NAVIGATION_COLLAPSED';

const { Sider } = Layout;

const tn = tNamespaced('SideNavigationMenu');

export const SyncariLogo = ({ isCollapsed, onClick, isBrandingEnabled }: SyncariLogoProps) => {
  return (
    <div
      className={cx(
        'main-nav__logo-container',
        `main-nav__logo-container--${isCollapsed ? COLLAPSED : EXPANDED}`,
        `main-nav__logo-container--branding-${isBrandingEnabled ? ENABLED : DISABLED}`
      )}
    >
      <div className={isBrandingEnabled ? 'logo-custom' : 'logo'}>
        {isBrandingEnabled ? (
          <>
            {isCollapsed ? (
              <img
                className="img-custom--collapsed"
                src="/arcade/api/v1/brand/logoSquare"
                alt="Organization branding logo"
                aria-labelledby={tn('logo')}
                onClick={onClick}
              />
            ) : (
              <>
                <img
                  className="img-custom--expanded"
                  src="/arcade/api/v1/brand/logo"
                  alt="Organization branding logo"
                  aria-labelledby={tn('logo')}
                  onClick={onClick}
                />
                <span className="powered-by">— powered by NextEdge AI</span>
              </>
            )}
          </>
        ) : (
          <FullLogo aria-labelledby={tn('logo')} onClick={onClick} />
        )}
      </div>
    </div>
  );
};

const Separator = ({ isCollapsed }: { isCollapsed: boolean }) => (
  <div className={cx('main-nav__separator', `main-nav__separator--${isCollapsed ? COLLAPSED : EXPANDED}`)} />
);

function SideNavigationMenu() {
  const [isCollapsed, setCollapsed] = usePersistedState(PERSISTED_NAVIGATION_COLLAPSED, false);
  const [showDataQuality, setShowDataQuality] = useState(true);
  const dispatch = useDispatch<ThunkDispatch<any, any, AnyAction>>();
  const userEmail = useEnhancedSelector(selectUserEmail);
  const isGuidedDemo = isGuidedDemoAccount(userEmail);

  const isBrandingEnabled = useBrandingEnabled();

  const navigationStatus = isCollapsed ? COLLAPSED : EXPANDED;

  const { updateDimensions } = useLayoutContext();
  const location = useLocation();
  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });

  const isForbiddenToastVisibleRef = useRef<null | boolean>(null);

  useEffect(() => {
    updateDimensions('sider', dimensions);
  }, [dimensions, updateDimensions]);

  useEffect(() => {
    const checkDataQuality = async () => {
      try {
        const action = await dispatch(checkCustomRuleAssignmentExists());
        if (checkCustomRuleAssignmentExists.fulfilled.match(action)) {
          setShowDataQuality(action.payload);
        } else {
          setShowDataQuality(false);
        }
      } catch {
        setShowDataQuality(false);
      }
    };

    checkDataQuality();
  }, [dispatch]);

  const navigateHome = () => navigateTo(RouteConstants.HOME);

  const toggleSidebar = () => setCollapsed(!isCollapsed);

  const MenuItemData: MenuItemType[] = [
    {
      path: RouteConstants.V1_WORKSPACE,
      title: 'V1 Workspace',
      inactiveIcon: DashboardIconInactive,
      activeIcon: DashboardIconActive,
      tourTarget: 'workspace',
    },
    {
      path: RouteConstants.INSIGHTS_STUDIO,
      title: tn('insights_studio'),
      inactiveIcon: DashboardIconInactive,
      activeIcon: DashboardIconActive,
      userflowTag: UserflowTags.SideNav.Insights,
      permission: [AllPermissions.READ_INSIGHTS],
    },
    {
      path: RouteConstants.SYNAPSES,
      title: tn('synapses'),
      inactiveIcon: SynapseIconInactive,
      activeIcon: SynapseIconActive,
      userflowTag: UserflowTags.SideNav.Synapse,
      permission: AllPermissions.READ_CONNECTOR,
      tourTarget: 'synapses',
    },
    {
      path: RouteConstants.SCHEMA_STUDIO_ROOT,
      title: tn('schema_studio'),
      inactiveIcon: SchemaStudioIconInactive,
      activeIcon: SchemaStudioIconActive,
      userflowTag: UserflowTags.SideNav.Schema,
      permission: [AllPermissions.READ_STUDIO, AllPermissions.READ_CONNECTOR],
      tourTarget: 'schema',
    },
    {
      path: makeUrl(RouteConstants.SYNC_STUDIO),
      title: tn('sync_studio'),
      activeIcon: SyncStudioIconActive,
      inactiveIcon: SyncStudioIconInactive,
      userflowTag: UserflowTags.SideNav.Sync,
      permission: AllPermissions.READ_STUDIO,
      tourTarget: 'sync',
    },
    {
      path: RouteConstants.DATA_STUDIO_ROOT,
      title: tn('data_studio'),
      inactiveIcon: DataStudioIconInactive,
      activeIcon: DataStudioIconActive,
      userflowTag: UserflowTags.SideNav.Data,
      permission: [AllPermissions.READ_DATA_STUDIO],
      tourTarget: 'data',
    },
    ...(showDataQuality
      ? [
          {
            path: RouteConstants.DATA_QUALITY_STUDIO_ROOT,
            title: tn('data_quality_studio'),
            inactiveIcon: DataQualityStudioIconInactive,
            activeIcon: DataQualityStudioIconActive,
            userflowTag: UserflowTags.SideNav.DataQuality,
            permission: AllPermissions.ANALYTICS,
            tourTarget: 'data-quality',
          },
        ]
      : []),
    {
      path: RouteConstants.IMPORTED_FILES,
      title: tn('imported_files'),
      inactiveIcon: ImportedFilesIconInactive,
      activeIcon: ImportedFilesIconActive,
      userflowTag: UserflowTags.SideNav.ImportedFiles,
      permission: AllPermissions.READ_FILE_DATA,
      tourTarget: 'imported-files',
    },
    {
      path: RouteConstants.LOGS,
      title: tn('logs'),
      inactiveIcon: TransactionsIconInactive,
      activeIcon: TransactionsIconActive,
      userflowTag: UserflowTags.SideNav.Logs,
      permission: AllPermissions.VIEW_TRANSACTIONS,
      tourTarget: 'logs',
    },
  ];

  return (
    <div
      className={cx('main-nav__container', `main-nav__container--${navigationStatus}`)}
      data-userflow-tag={UserflowTags.SideNav.Container}
      ref={measurementRef}
    >
      <Sider
        className={cx('main-nav__sidebar')}
        data-testid="main-nav-menu-container"
        width={EXPANDED_WIDTH}
        collapsedWidth={COLLAPSED_WIDTH}
        collapsible
        collapsed={isCollapsed}
        onCollapse={setCollapsed}
        trigger={null}
      >
        <SyncariLogo isCollapsed={isCollapsed} onClick={navigateHome} isBrandingEnabled={isBrandingEnabled} />
        {!isBrandingEnabled && <Separator isCollapsed={isCollapsed} />}
        <div className={cx('main-nav__menu-items')} data-testid="main-nav-items-container">
          {MenuItemData.filter((item) => !isGuidedDemo || item.path !== RouteConstants.INSIGHTS_STUDIO).map((item) => (
            <SideMenuItem
              key={item.title}
              isCollapsed={isCollapsed}
              isForbiddenToastVisibleRef={isForbiddenToastVisibleRef}
              selected={location.pathname.includes(item.path)}
              path={item.path}
              navigationStatus={navigationStatus}
              title={item.title}
              inactiveIcon={item.inactiveIcon}
              activeIcon={item.activeIcon}
              permission={item.permission}
              tourTarget={item.tourTarget}
            />
          ))}
        </div>
        <Separator isCollapsed={isCollapsed} />
        <div className="main-nav__trigger-icons-container">
          {!isGuidedDemo && (
            <div aria-label={tn('settings')} data-userflow-tag={UserflowTags.SideNav.Settings}>
              <SideMenuItem
                selected={location.pathname.includes(RouteConstants.SETTINGS)}
                isCollapsed={isCollapsed}
                path={RouteConstants.SETTINGS}
                navigationStatus={navigationStatus}
                inactiveIcon={SettingsIconInactive}
                activeIcon={SettingsIconActive}
                title={tn('settings')}
              />
            </div>
          )}
          <div data-testid="main-nav-expand" data-userflow-tag={UserflowTags.SideNav.Expand} onClick={toggleSidebar}>
            <SideMenuItem
              className={isCollapsed ? 'flip' : 'flip--flipped'}
              selected={false}
              isCollapsed={isCollapsed}
              navigationStatus={navigationStatus}
              title={isCollapsed ? tn('expand') : tn('collapse')}
              inactiveIcon={CollapseIconInactive}
              activeIcon={CollapseIconActive}
            />
          </div>
        </div>
      </Sider>
    </div>
  );
}

export default SideNavigationMenu;
