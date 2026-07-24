import { RouteComponentProps } from '@reach/router';
import { Router } from '@reach/router';
import { Suspense, useEffect } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import RouteSpin from 'components/RouteSpin';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { showFastMapper } from 'store/fast-mapper/slice';
import { t } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { EntityPipelineBreadcrumb } from './entity-pipeline/EntityPipelineBreadcrumb';
import { useCurrentSyncStudioRootTab } from './entity/SyncStudioRootTabs';

export interface SyncStudioBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
}

export const SyncStudioBreadcrumb = withI18n(({ children }: SyncStudioBreadcrumbProps) => {
  const { setUrlName } = useBreadcrumb();
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const { currentTab } = useCurrentSyncStudioRootTab();

  const fastMapperVisible = useEnhancedSelector((state) => state.fastMapper.fastMapperVisible);

  useEffect(() => {
    setUrlName(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }), tn('title'));
  }, [currentTab, setUrlName, tn]);

  const handleSyncStudioClick = () => {
    if (fastMapperVisible) {
      dispatch(showFastMapper({ visible: false, entityId: '' }));
    }
  };

  return (
    <>
      <BreadcrumbLink to={makeUrl(RouteConstants.ENTITIES, { tabId: currentTab })} onClick={handleSyncStudioClick}>
        {t('SyncStudio.title')}
      </BreadcrumbLink>
      <Suspense fallback={<RouteSpin />}>
        <Router className="sync-studio-breadcrum breadcrumb">
          <EntityPipelineBreadcrumb path="/entity/:entityId/*" />
          <EntityPipelineBreadcrumb path="/entity/:entityId/pipeline/:version" />
        </Router>
      </Suspense>
    </>
  );
}, 'SyncStudio');
