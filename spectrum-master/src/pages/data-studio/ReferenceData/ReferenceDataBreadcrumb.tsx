import { RouteComponentProps } from '@reach/router';
import { useEffect, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { useReferenceDataList } from 'store/reference-data';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface ReferenceDataBreadcrumbProps extends RouteComponentProps {
  refDataId?: string;
}

export const ReferenceDataBreadcrumb = withI18n(({ refDataId }: ReferenceDataBreadcrumbProps) => {
  const { setUrlName } = useBreadcrumb();
  const { t, tn } = useI18nContext();
  const { data: referenceDataList } = useReferenceDataList();

  useEffect(() => {
    setUrlName(RouteConstants.DATA_STUDIO_ROOT, t('DataStudio.window_title'));
    setUrlName(RouteConstants.DATA_STUDIO_REFDATA_ROOT, tn('title'));
  }, [setUrlName, t, tn]);

  const [refDataName = refDataId, url] = useMemo(() => {
    const url = makeUrl(RouteConstants.DATA_STUDIO_REFDATA, { refDataId });
    if (refDataId) {
      const refData = referenceDataList?.find((refData) => refData.id === refDataId);
      if (refData) {
        setUrlName(url, refData.name);
        return [refData.name, url];
      }
    }
    return [refDataId, url];
  }, [refDataId, referenceDataList, setUrlName]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.DATA_STUDIO_ROOT}>{t('DataStudio.window_title')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={RouteConstants.DATA_STUDIO_ROOT}>{tn('title')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={url}>{refDataName}</BreadcrumbLink>
    </>
  );
}, 'ReferenceDataList');
