import { RouteComponentProps } from '@reach/router';
import { EmbedEvent, SearchEmbed } from '@thoughtspot/visual-embed-sdk';
import { Spin } from 'antd';
import { useEffect } from 'react';

import { useLayoutContext } from 'pages/LayoutContext';
import { useShareTSObjectMutation } from 'store/insights-studio';
import AppConstants from 'utils/AppConstants';

import { useInsightsViewContext } from '../context/InsightsViewContext';
import { TAB_RESERVED_HEIGHT, hiddenActions } from './Dashboards';

const Search = (props: RouteComponentProps) => {
  const layoutContext = useLayoutContext();
  const { isThoughtSpotLoading, setIsThoughtspotLoading, isThoughtSpotInitialized } = useInsightsViewContext();
  const [shareDatacard] = useShareTSObjectMutation();
  useEffect(() => {
    if (!isThoughtSpotInitialized) {
      return;
    }
    setIsThoughtspotLoading(true);
    try {
      const searchEmbedEl = document.getElementById('searchEmbed')!;

      const searchEmbed = new SearchEmbed(searchEmbedEl, {
        frameParams: {
          width: '100%',
          height: '100%',
        },
        hiddenActions,
        additionalFlags: {
          hideWorksheetSelector: true,
          disableWorksheetChange: true,
          disableRedirectionLinksInNewTab: true,
        },
        linkOverride: true,
        enableV2Shell_experimental: true,
        dataPanelV2: false,
      });
      searchEmbed.on(EmbedEvent.Init, () => {
        setIsThoughtspotLoading(false);
      });
      searchEmbed.on(EmbedEvent.Load, () => {
        setIsThoughtspotLoading(false);
      });
      searchEmbed.render();
    } catch (err) {
      // Graceful failure
      console.log('Error embedding search', err);
      setIsThoughtspotLoading(false);
    }
    // Handle app routing based on the routing inside iframe
    const listenIFrameMessage = (event: MessageEvent<any>) => {
      if (event.origin === AppConstants.THOUGHTSPOT_URL && event.data.type === 'save' && event.data.status === 'end') {
        shareDatacard({ metadataType: 'ANSWER', metadataId: event.data.data.answerId });
      }
    };

    window.addEventListener('message', listenIFrameMessage);

    return () => {
      window.removeEventListener('message', listenIFrameMessage);
    };
  }, [isThoughtSpotInitialized, setIsThoughtspotLoading, shareDatacard]);

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
        id="searchEmbed"
      />
    </div>
  );
};

export default Search;
