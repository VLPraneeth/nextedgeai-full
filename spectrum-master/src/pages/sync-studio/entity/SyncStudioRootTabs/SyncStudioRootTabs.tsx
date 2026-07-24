import { navigate, useMatch } from '@reach/router';
import { Icon } from 'antd';

import { SYNC_STUDIO_VIEW_SELECTION } from 'components/graph/constants';
import { InlineTabs, InlineTab } from 'components/InlineTabs';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useCurrentSyncStudioRootTab, useRouteRedirects } from './SyncStudioRootTabs.hooks';

import './SyncStudioRootTabs.scss';

const tn = tNamespaced('SyncStudioRootTabs');

export const SyncStudioRootTabs = () => {
  useRouteRedirects();

  const match = useMatch('/sync-studio/:tabId/:entityId');
  const { currentTab } = useCurrentSyncStudioRootTab();
  const entityId = match?.entityId;

  const handleTabChange = (tabId: string) => {
    const baseRoute = entityId ? RouteConstants.ENTITY : RouteConstants.ENTITIES;
    const route = makeUrl(baseRoute, { tabId, entityId });

    navigate(route);
    localStorage.setItem(SYNC_STUDIO_VIEW_SELECTION, tabId);
  };

  return (
    <div className="sync-studio-root-tabs">
      <InlineTabs selectedTab={currentTab ?? 'details'} onChange={handleTabChange}>
        <InlineTab className="sync-studio-root-tabs__tab" id="details">
          <Icon type="unordered-list" />
          {tn('details')}
        </InlineTab>
        <InlineTab className="sync-studio-root-tabs__tab" id="relationships">
          <Icon type="fork" />
          {tn('relationships')}
        </InlineTab>
      </InlineTabs>
    </div>
  );
};
