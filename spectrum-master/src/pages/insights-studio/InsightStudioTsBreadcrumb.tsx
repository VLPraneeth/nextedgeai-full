import { RouteComponentProps, useMatch } from '@reach/router';
import { useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useGetTSLiveboardsQuery } from 'store/insights-studio';
import { t } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { humanize } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

export const httpCustomSynapseItemPath = '/synapses/custom-synapses/http/:id';
export const sdkCustomSynapseItemPath = '/synapses/custom-synapses/sdk/:id';
export const entitiesBasePath = '/synapses/custom-synapses/http/:synapseId/entities/:version/*';
export const entitiesItemPath = '/synapses/custom-synapses/http/:synapseId/entities/:version/:entityId';

export interface InsightStudioTsBreadcrumbProps extends RouteComponentProps {
  tab?: string;
}

export const InsightStudioTsBreadcrumb = ({ tab }: InsightStudioTsBreadcrumbProps) => {
  const dashboardIdTSMatch = useMatch('/insights-studio/ts/dashboards/:dashboardId/*');
  const { data: liveboards } = useGetTSLiveboardsQuery();

  const liveboardsArray = useMemo(() => {
    return Object.keys(liveboards || []).map((key) => ({
      id: liveboards?.[key] || '',
      name: key,
    }));
  }, [liveboards]);

  const tsDashboardName = useMemo(() => {
    if (dashboardIdTSMatch) {
      const dashboard = liveboardsArray.find((dash) => dash.id === dashboardIdTSMatch?.dashboardId);
      if (dashboard) {
        return dashboard.name;
      }
    }
  }, [dashboardIdTSMatch, liveboardsArray]);
  return (
    <>
      <BreadcrumbLink to={RouteConstants.INSIGHTS_STUDIO}>{t('InsightsStudio.title')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      {tab && (
        <BreadcrumbLink to={makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_TAB, { tab })}>{humanize(tab)}</BreadcrumbLink>
      )}
      {tsDashboardName && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink
            to={makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS_ID, {
              dashboardId: dashboardIdTSMatch?.dashboardId,
            })}>
            {tsDashboardName}
          </BreadcrumbLink>
        </>
      )}
    </>
  );
};
