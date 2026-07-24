import { RouteComponentProps, navigate, useMatch } from '@reach/router';
import { AppEmbed, EmbedEvent, Page } from '@thoughtspot/visual-embed-sdk';
import { Spin } from 'antd';
import { useEffect } from 'react';

import { useLayoutContext } from 'pages/LayoutContext';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useInsightsViewContext } from '../context/InsightsViewContext';
import { TAB_RESERVED_HEIGHT, hiddenActions } from './Dashboards';

export const Datacards = (props: RouteComponentProps) => {
  const layoutContext = useLayoutContext();
  const datacardIdTSMatch = useMatch('/insights-studio/ts/datacards/:datacardId/*');
  const { isThoughtSpotLoading, setIsThoughtspotLoading, isThoughtSpotInitialized } = useInsightsViewContext();
  useEffect(() => {
    if (!isThoughtSpotInitialized) {
      return;
    }
    setIsThoughtspotLoading(true);
    try {
      const datacardsEmbedEl = document.getElementById('datacardsEmbed')!;

      const datacardsEmbed = new AppEmbed(datacardsEmbedEl, {
        pageId: Page.Answers,
        path: datacardIdTSMatch?.datacardId ? `saved-answer/${datacardIdTSMatch?.datacardId}` : undefined,
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
      });
      datacardsEmbed.on(EmbedEvent.Init, () => {
        setIsThoughtspotLoading(false);
      });
      datacardsEmbed.on(EmbedEvent.Load, () => {
        setIsThoughtspotLoading(false);
      });

      datacardsEmbed.render();
    } catch (err) {
      // Graceful failure
      console.log('Error embedding datacards', err);
      setIsThoughtspotLoading(false);
    }

    // Handle app routing based on the routing inside iframe
    const listenIFrameMessage = (event: MessageEvent<any>) => {
      if (event.origin === AppConstants.THOUGHTSPOT_URL && event.data.type === 'ROUTE_CHANGE') {
        const path = event.data.data.currentPath;
        if (path.startsWith('/insights/saved-answer/') || path.startsWith('/saved-answer/')) {
          const datacardId = path.split('/')[path.split('/').length - 1];

          navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATACARDS_ID, { datacardId }));
        } else if (path.startsWith('/insights/pinboard/')) {
          const dashboardId = path.split('/')[path.split('/').length - 1];

          navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS_ID, { dashboardId }));
        } else if (path === '/insights/home/liveboards') {
          navigate(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS);
        }
      }
    };

    window.addEventListener('message', listenIFrameMessage);

    return () => {
      window.removeEventListener('message', listenIFrameMessage);
    };
  }, [datacardIdTSMatch?.datacardId, setIsThoughtspotLoading, isThoughtSpotInitialized]);

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
        id="datacardsEmbed"
      />
    </div>
  );
};
