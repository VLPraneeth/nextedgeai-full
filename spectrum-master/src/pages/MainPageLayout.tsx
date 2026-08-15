//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps, Router } from '@reach/router';
import Layout from 'antd/lib/layout';
import cx from 'classnames';
import { first } from 'lodash';
import { Suspense, useEffect, useMemo } from 'react';

import { getConnectorsMetadata } from 'actions/connectorActions';
import GhostUserBanner from 'components/GhostUserBanner';
import GuidedProductTour from 'components/guided-product-tour/GuidedProductTour';
import Navigation from 'components/navigation/Navigation';
import Redirect from 'components/Redirect';
import RouteSpin from 'components/RouteSpin';
import { TrialBanner } from 'components/trial-banner/TrialBanner';
import { Userflow } from 'components/userflow/Userflow';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import { getEntities } from 'store/entity/actions';
import { getEntityState } from 'store/entity/selectors';
import { selectUserEmail } from 'store/user/selectors';
import { isGuidedDemoAccount, isGuidedDemoRoute } from 'utils/GuidedDemo';
import { tNamespaced } from 'utils/i18nUtil';
import { EnhancedReactLazy } from 'utils/ModuleUtils';
import RouteConstants from 'utils/RouteConstants';
import { getPaths } from 'utils/UrlUtil';

import { BreadcrumbContextProvider } from './breadcrumbs/BreadcrumbContext';
import { InsightsViewContextProvider } from './insights-studio/context/InsightsViewContext';
import { LayoutContextProvider } from './LayoutContext';
import MainContent from './MainContent';
import MainHeader from './MainHeader';

import './MainPageLayout.less';

/* Route components */
// load InitialRoute and Dashboard concurrently since they're most likely
const Error404 = EnhancedReactLazy(() => import('pages/errors/Error404'), { loadConcurrently: true });

const V1Workspace = EnhancedReactLazy(() => import('pages/v1-workspace/V1Workspace'), { loadConcurrently: true });
const SynapseMain = EnhancedReactLazy(() => import('pages/connector/SynapseMain'));
const SchemaStudio = EnhancedReactLazy(() => import('pages/schema-studio'));
const SyncStudio = EnhancedReactLazy(() => import('pages/sync-studio/SyncStudio'));
const DataStudio = EnhancedReactLazy(() => import('pages/data-studio-new'));
const DataQualityStudio = EnhancedReactLazy(() => import('pages/data-quality-studio'));
const ImportedFiles = EnhancedReactLazy(() => import('pages/imported-files'));
const Logs = EnhancedReactLazy(() => import('pages/logs'));
const InsightsStudio = EnhancedReactLazy(() => import('pages/insights-studio/InsightsStudio'));

const Settings = EnhancedReactLazy(() => import('pages/settings/Settings'));

const Profile = EnhancedReactLazy(() => import('pages/settings/user/Profile'));
const EditProfile = EnhancedReactLazy(() => import('pages/settings/user/EditProfile'));
const NotificationList = EnhancedReactLazy(() => import('pages/notification/NotificationList'));

/* end of Route components */

/**
 * createPathClassNames
 *
 * used to create our path based classnames. This allows us to have
 * classNames for each route automatically
 *
 * @example
 * createPathClassNames("main-content", "content")
 * // location: /sync-studio/entity/asdfasdfasdf
 * // => "main-content sync-studio-content entity-content asdfasdfasdf-content"
 *
 * // location: /
 * // => "main-content"
 *
 * createPathClassNames("main-content", "content", "fallback-content")
 * // location: /
 * // => "main-content fallback-content"
 */
const createPathClassNames = (
  paths: string[],
  mainClassName: string,
  contentClassSuffix: string,
  fallbackClassName?: string
) => {
  if (paths.length <= 1 && (first(paths) === '' || first(paths) === '/')) {
    return cx(mainClassName, fallbackClassName || mainClassName);
  }

  return cx(
    mainClassName,
    paths.map((path) => `${path}-${contentClassSuffix}`)
  );
};

const getContentClassName = (paths: string[]) =>
  createPathClassNames(paths, 'main-content', 'content', 'dashboard-content');
const getInnerContentClassName = (paths: string[]) => createPathClassNames(paths, 'content-inner', 'inner-content');

const tn = tNamespaced('MainPageLayout');

const LayoutStyle = { height: '100vh', width: '100%' };

const MainPageLayout = ({ location }: RouteComponentProps) => {
  const dispatch = useEnhancedDispatch();
  const { entitiesFetching, entities } = useEnhancedSelector(getEntityState);
  const changed = useEnhancedSelector((state) => state.pipeline.changed);
  const userEmail = useEnhancedSelector(selectUserEmail);
  const isGuidedDemo = isGuidedDemoAccount(userEmail);

  useEffect(() => {
    // It needs to overwrite the onbeforeunload when the changed changes
    // otherwise the changed doesn't reflect its current value when the
    // function is called. onbeforeunload quirk ¯\_(ツ)_/¯
    window.onbeforeunload = () => {
      if (changed) {
        return tn('unsaved_changes');
      }
    };
  }, [changed]);

  useMountUnmountEffect(() => {
    dispatch(getConnectorsMetadata());
    if (!entitiesFetching && !entities?.length) {
      dispatch(getEntities());
    }
  });

  const [contentClassName, innerContentClassName] = useMemo(() => {
    if (!location) {
      return ['', ''];
    }
    const paths = getPaths(location.pathname);

    return [getContentClassName(paths), getInnerContentClassName(paths)];
  }, [location]);

  if (isGuidedDemo && location && !isGuidedDemoRoute(location.pathname)) {
    return <Redirect redirectTo={RouteConstants.V1_WORKSPACE} replace />;
  }

  return (
    <LayoutContextProvider>
      <Userflow />
      <GuidedProductTour autoStart={isGuidedDemo} />
      <Layout style={LayoutStyle}>
        <BreadcrumbContextProvider>
          <GhostUserBanner />
          <TrialBanner />
          <Layout hasSider>
            <Navigation />
            <Layout className={contentClassName}>
              <MainHeader />
              <MainContent className={innerContentClassName}>
                <InsightsViewContextProvider>
                  <Suspense fallback={<RouteSpin />}>
                    <Router className="main-page-layout-container">
                      <Redirect path="/" redirectTo={RouteConstants.HOME} />
                      <V1Workspace path={RouteConstants.V1_WORKSPACE} />
                      <SynapseMain path="/synapses/*" />

                      <SchemaStudio path="/schema-studio/*" />
                      <SyncStudio path="/sync-studio/*" />
                      <DataStudio path="/data-studio/*" />
                      <DataQualityStudio path="/data-quality-studio/*" />
                      <Logs path={`${RouteConstants.LOGS}/*`} />
                      <InsightsStudio path="/insights-studio/*" />
                      <ImportedFiles path="/imported-files/*" />
                      <Settings path="/settings/*" className="settings-section" />
                      <NotificationList path="/notifications" location={location} />
                      <Profile path="/profile">
                        <EditProfile path="/edit-profile" />
                      </Profile>
                      <Redirect path="/transaction" redirectTo={RouteConstants.LOGS} replace />
                      <Redirect path={RouteConstants.TRANSACTIONS} redirectTo={RouteConstants.LOGS} replace />
                      <Redirect path="/sync-studio" redirectTo="/sync-studio/entity" replace />
                      <Error404 default />
                    </Router>
                  </Suspense>
                </InsightsViewContextProvider>
              </MainContent>
            </Layout>
          </Layout>
        </BreadcrumbContextProvider>
      </Layout>
    </LayoutContextProvider>
  );
};

export default MainPageLayout;
