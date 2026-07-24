import { navigate, useMatch } from '@reach/router';

import { SYNC_STUDIO_VIEW_SELECTION } from 'components/graph/constants';
import RouteConstants from 'utils/RouteConstants';
import { entityIdIsValid, routeToMatch } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

export const useCurrentSyncStudioRootTab = () => {
  const baseRouteMatch = useMatch(routeToMatch(RouteConstants.ENTITIES));
  const entityRouteMatch = useMatch(routeToMatch(RouteConstants.ENTITY));
  const quickStartRouteMatch = useMatch(routeToMatch(RouteConstants.QUICK_START));
  const quickStartInstallMatch = useMatch(routeToMatch(RouteConstants.QUICK_START_INSTALL));

  const storedTab = localStorage.getItem(SYNC_STUDIO_VIEW_SELECTION);

  // Pick the match in order of route "deepness"
  const match = quickStartInstallMatch ?? quickStartRouteMatch ?? entityRouteMatch ?? baseRouteMatch;
  const currentTab = match?.tabId ?? storedTab ?? 'details';

  return { currentTab };
};

export const useRouteRedirects = () => {
  const tabId = localStorage.getItem(SYNC_STUDIO_VIEW_SELECTION) ?? 'details';

  const OLD_baseRouteMatch = useMatch('/sync-studio/entity');
  const OLD_entityRouteMatch = useMatch('/sync-studio/entity/:entityId');
  const OLD_quickStartRouteMatch = useMatch('/sync-studio/entity/quick-start');
  const OLD_quickStartInstallMatch = useMatch('/sync-studio/entity/quick-start/:quickStartId/install');

  let url: string | undefined;

  if (OLD_quickStartInstallMatch) {
    const { quickStartId } = OLD_quickStartInstallMatch;
    url = makeUrl(RouteConstants.QUICK_START_INSTALL, { tabId, quickStartId });
  } else if (OLD_quickStartRouteMatch) {
    url = makeUrl(RouteConstants.QUICK_START, { tabId });
  } else if (OLD_entityRouteMatch) {
    const { entityId } = OLD_entityRouteMatch;

    if (entityIdIsValid(entityId)) {
      url = makeUrl(RouteConstants.ENTITY, { tabId, entityId });
    }
  } else if (OLD_baseRouteMatch) {
    url = makeUrl(RouteConstants.ENTITIES, { tabId });
  }

  if (url) {
    navigate(url);
  }
};
