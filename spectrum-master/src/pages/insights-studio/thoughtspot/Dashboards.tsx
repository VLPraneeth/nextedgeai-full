import { RouteComponentProps, navigate, useMatch } from '@reach/router';
import { Action, AppEmbed, EmbedEvent, Page } from '@thoughtspot/visual-embed-sdk';
import { Spin } from 'antd';
import { useEffect, useRef } from 'react';

import { useLayoutContext } from 'pages/LayoutContext';
import { useGetTSLiveboardsQuery, useShareTSObjectMutation } from 'store/insights-studio';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useInsightsViewContext } from '../context/InsightsViewContext';

export const hiddenActions = [
  Action.UpdateTML,
  Action.RequestVerification,
  Action.TML,
  Action.Share,
  Action.AddToFavorites,
  Action.CopyLink,
  Action.SyncToSheets,
  Action.ManagePipelines,
  Action.SyncToOtherApps,
  Action.SpotIQAnalyze,
  Action.SaveAsView,
  Action.SyncToSheets,
  Action.SyncToTeams,
  Action.SyncToSlack,
  Action.SyncToOtherApps,
];

export const TAB_RESERVED_HEIGHT = 49;
export const Dashboards = (props: RouteComponentProps) => {
  const layoutContext = useLayoutContext();
  const dashboardIdTSMatch = useMatch('/insights-studio/ts/dashboards/:dashboardId/*');
  const { refetch } = useGetTSLiveboardsQuery();
  const { isThoughtSpotLoading, setIsThoughtspotLoading, isThoughtSpotInitialized } = useInsightsViewContext();
  const [shareDatacard] = useShareTSObjectMutation();
  const ref = useRef(null);

  useEffect(() => {
    if (!isThoughtSpotInitialized) {
      return;
    }
    setIsThoughtspotLoading(true);
    try {
      const dashboardsEmbedEl = document.getElementById('dashboardsEmbed')!;

      const dashboardsEmbed = new AppEmbed(dashboardsEmbedEl, {
        pageId: Page.Liveboards,
        path: dashboardIdTSMatch?.dashboardId ? `pinboard/${dashboardIdTSMatch?.dashboardId}` : undefined,
        showPrimaryNavbar: false,
        frameParams: {
          height: '100%',
          width: '100%',
        },
        hiddenActions,
        additionalFlags: {
          disableRedirectionLinksInNewTab: true,
        },
        enableV2Shell_experimental: true,
        showLiveboardTitle: true,
        isLiveboardHeaderSticky: false,
      });
      dashboardsEmbed.on(EmbedEvent.Init, () => {
        setIsThoughtspotLoading(false);
      });
      dashboardsEmbed.on(EmbedEvent.Load, () => {
        setIsThoughtspotLoading(false);
      });
      dashboardsEmbed.render();
    } catch (err) {
      // Graceful failure
      console.log('Error embedding dashboard', err);
      setIsThoughtspotLoading(false);
    }

    // Handle app routing based on the routing inside iframe
    const listenIFrameMessage = (event: MessageEvent<any>) => {
      if (event.origin === AppConstants.THOUGHTSPOT_URL && event.data.type === 'ROUTE_CHANGE') {
        const path = event.data.data.currentPath;
        if (path.startsWith('/insights/pinboard/') || path.startsWith('/pinboard/')) {
          const dashboardId = path.split('/')[path.split('/').length - 1];
          if (ref.current === 'dialog-close') {
            shareDatacard({ metadataType: 'LIVEBOARD', metadataId: dashboardId });
            ref.current = null;
          }

          navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS_ID, { dashboardId }));
        } else if (path === '/insights/home/liveboards') {
          navigate(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS);
        }
      } else if (event.origin === AppConstants.THOUGHTSPOT_URL && event.data.type !== 'ROUTE_CHANGE') {
        ref.current = event.data.type;
      }
    };

    window.addEventListener('message', listenIFrameMessage);

    return () => {
      window.removeEventListener('message', listenIFrameMessage);
    };
  }, [dashboardIdTSMatch?.dashboardId, refetch, setIsThoughtspotLoading, isThoughtSpotInitialized, shareDatacard]);

  return (
    <div>
      {isThoughtSpotLoading && (
        <div className="no_dashboard">
          <Spin />
        </div>
      )}
      <div
        style={{
          width: layoutContext.dimensions.content.width,
          height: layoutContext.dimensions.content.height - TAB_RESERVED_HEIGHT,
          top: `${TAB_RESERVED_HEIGHT}px`,
          position: 'relative',
        }}
        id="dashboardsEmbed"
      />
    </div>
  );
};
