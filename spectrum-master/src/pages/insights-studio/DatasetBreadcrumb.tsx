import { RouteComponentProps, useMatch } from '@reach/router';
import { useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { useGetDatasetsQuery } from 'store/insights-studio';
import RouteConstants from 'utils/RouteConstants';
import { makeRouteConstantToRoute, makeUrl } from 'utils/UrlUtil';

export interface DatasetBreadcrumbProps extends RouteComponentProps {
  dashboardId?: string;
  datasetId?: string;
}

export const DatasetBreadcrumb = ({ dashboardId, datasetId }: DatasetBreadcrumbProps) => {
  const { data: datasets } = useGetDatasetsQuery();
  const { setUrlName } = useBreadcrumb();
  const datasetMatch = useMatch(makeRouteConstantToRoute(RouteConstants.INSIGHTS_STUDIO_DATASET));

  const [datasetName = datasetId, url] = useMemo(() => {
    const name = datasets?.find((dataset) => dataset.id === datasetId)?.displayName;
    if (name && datasetMatch) {
      setUrlName(makeUrl(RouteConstants.INSIGHTS_STUDIO_DATASET, datasetMatch), name);
    }
    return [name, makeUrl(RouteConstants.INSIGHTS_STUDIO_DATASET, { dashboardId, datasetId })];
  }, [dashboardId, datasetId, datasetMatch, datasets, setUrlName]);

  return (
    <>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={url}>{datasetName}</BreadcrumbLink>
    </>
  );
};
