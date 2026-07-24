import { RouteComponentProps } from '@reach/router';
import { useEffect, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface DataQualityBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  dashboardId?: string;
}

export const DataQualityBreadcrumb = withI18n(({ children, dashboardId }: DataQualityBreadcrumbProps) => {
  const dashboards = useEnhancedSelector((state) => state.newDashboard.dashboards);
  const { setUrlName } = useBreadcrumb();
  const { tn } = useI18nContext();

  useEffect(() => {
    setUrlName(RouteConstants.DATA_QUALITY_STUDIO_ROOT, tn('Root.title'));
  }, [setUrlName, tn]);

  const [dashboardName = dashboardId, url] = useMemo(() => {
    const name = dashboardId ? dashboards?.[dashboardId]?.title : dashboardId;
    const url = makeUrl(RouteConstants.DATA_QUALITY_STUDIO_DASHBOARD, { dashboardId });
    if (dashboardId && dashboards?.[dashboardId]?.title) {
      setUrlName(url, dashboards[dashboardId].title);
    }
    return [name, url];
  }, [dashboardId, dashboards, setUrlName]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.DATA_QUALITY_STUDIO_ROOT}>{tn('Root.title')}</BreadcrumbLink>
      {dashboardName && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink to={url}>{dashboardName}</BreadcrumbLink>
        </>
      )}
    </>
  );
}, 'DataQualityStudio');
