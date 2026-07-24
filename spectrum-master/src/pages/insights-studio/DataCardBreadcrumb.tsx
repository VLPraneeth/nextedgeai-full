import { RouteComponentProps } from '@reach/router';
import { useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { useGetAllDataCardsQuery } from 'store/insights-studio';
import RouteContants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface DataCardBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  dashboardId?: string;
  dataCardId?: string;
}

export const DataCardBreadcrumb = ({ children, dashboardId, dataCardId }: DataCardBreadcrumbProps) => {
  const { data: dataCards } = useGetAllDataCardsQuery();
  const { setUrlName } = useBreadcrumb();

  const [dataCardName = dataCardId, url] = useMemo(() => {
    const name = dataCards?.find((dataCard) => dataCard.id === dataCardId)?.displayName;
    const url = makeUrl(RouteContants.INSIGHTS_STUDIO_DATA_CARD, { dashboardId, dataCardId });
    if (name && url) {
      setUrlName(url, name);
    }

    return [name, url];
  }, [dashboardId, dataCardId, dataCards, setUrlName]);

  return (
    <>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={url}>{dataCardName}</BreadcrumbLink>
    </>
  );
};
