import { RouteComponentProps, Router, useMatch } from '@reach/router';
import { Suspense, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import RouteSpin from 'components/RouteSpin';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { useDashboards } from 'pages/insights-studio/utils';
import { t } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeRouteConstantToRoute, makeUrl } from 'utils/UrlUtil';

import { DataCardBreadcrumb } from './DataCardBreadcrumb';
import { DatasetBreadcrumb } from './DatasetBreadcrumb';

export interface InsightStudioBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  dashboardId?: string;
  version?: string;
}

export const InsightStudioBreadcrumb = withI18n(({ dashboardId, version }: InsightStudioBreadcrumbProps) => {
  const insightsDashboards = useDashboards();
  const { setUrlName } = useBreadcrumb();
  const { tn } = useI18nContext();
  const dashboardMatch = useMatch(makeRouteConstantToRoute(RouteConstants.INSIGHTS_STUDIO_DASHBOARD));

  const [dashboardName = dashboardId, dashboardUrl] = useMemo(() => {
    const url = makeUrl(
      version ? RouteConstants.INSIGHTS_STUDIO_DASHBOARD_DRAFT : RouteConstants.INSIGHTS_STUDIO_DASHBOARD,
      { dashboardId }
    );

    setUrlName(RouteConstants.INSIGHTS_STUDIO, tn('title'));

    const displayName = insightsDashboards?.find((dash) => dash.id === dashboardId)?.displayName;
    if (displayName && dashboardMatch) {
      setUrlName(makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD, { dashboardId }), displayName);
    }

    return [displayName, url];
  }, [dashboardId, dashboardMatch, insightsDashboards, setUrlName, tn, version]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.INSIGHTS_STUDIO}>{t('InsightsStudio.title')}</BreadcrumbLink>
      {dashboardId && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink to={dashboardUrl}>{dashboardName}</BreadcrumbLink>
        </>
      )}
      <Suspense fallback={<RouteSpin />}>
        <Router className="insight-studio-breadcrum breadcrumb">
          <DataCardBreadcrumb path="/datacard/:dataCardId/*" dashboardId={dashboardId} />
          <DatasetBreadcrumb path="/dataset/:datasetId" dashboardId={dashboardId} />
        </Router>
      </Suspense>
    </>
  );
}, 'InsightsStudio');
