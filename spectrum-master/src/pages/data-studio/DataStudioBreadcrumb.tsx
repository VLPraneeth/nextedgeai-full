import { RouteComponentProps } from '@reach/router';
import { startCase } from 'lodash';
import { useEffect, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { getEntityName } from 'utils/EntityUtil';
import { t, tCommon } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface DataStudioBreadcrumbProps extends RouteComponentProps {
  entityId?: string;
  recordId?: string;
  recordDisplayType?: string;
}

export const DataStudioBreadcrumb = withI18n(({ entityId, recordId, recordDisplayType }: DataStudioBreadcrumbProps) => {
  const entities = useEnhancedSelector((state) => state.entity.entities);
  const { setUrlName } = useBreadcrumb();
  const { tn } = useI18nContext();

  useEffect(() => {
    setUrlName(RouteConstants.DATA_STUDIO_ROOT, tn('window_title'));
  }, [setUrlName, tn]);

  const [entityName, url] = useMemo(() => {
    const entityName = entities ? getEntityName(entityId, entities) : entityId;
    const url = makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId });
    if (entities) {
      setUrlName(url, entityName ?? entityId);
    }

    // Fallback on entityId if the user navigates to an entity that does not exist.
    return [entityName ?? entityId, url];
  }, [entities, entityId, setUrlName]);

  const [recordIdDisplayName, recordIdUrl] = useMemo(() => {
    const recordIdDisplayName = recordId
      ? tCommon('record') + ' ' + recordId.slice(0, 4) + '…' + recordId.slice(recordId.length - 4)
      : '';
    const url = makeUrl(RouteConstants.DATA_STUDIO_RECORD, { entityId, recordId });
    if (recordIdDisplayName) {
      setUrlName(url, recordIdDisplayName);
    }
    return [recordIdDisplayName, url];
  }, [entityId, recordId, setUrlName]);

  const recordDisplayTypeUrl = useMemo(() => {
    return makeUrl(
      recordDisplayType?.toLocaleLowerCase() === 'fields'
        ? RouteConstants.DATA_STUDIO_RECORD_FIELDS
        : RouteConstants.DATA_STUDIO_RECORD_TRANSACTIONS,
      { entityId, recordId }
    );
  }, [entityId, recordDisplayType, recordId]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.DATA_STUDIO_ROOT}>{t('DataStudio.window_title')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={url}>{entityName}</BreadcrumbLink>

      {recordId && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink tooltipClipboard={recordId} to={recordIdUrl}>
            {recordIdDisplayName}
          </BreadcrumbLink>
        </>
      )}

      {recordDisplayType && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink to={recordDisplayTypeUrl}>{startCase(recordDisplayType)}</BreadcrumbLink>
        </>
      )}
    </>
  );
}, 'DataStudio');
