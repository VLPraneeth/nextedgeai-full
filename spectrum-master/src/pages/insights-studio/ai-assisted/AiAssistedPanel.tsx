import { useCallback } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack } from 'components/layout';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';

import { AiAssistCreateDataCards } from './AiAssistCreateDataCards';
import { InterestingDataCards } from './InterestingDataCards';

import './AiAssistedPanel.scss';

export const AiAssistedPanel = withI18n(() => {
  const { navigateToCurrentDashboard, aiAssistedMatch } = useUnifiedDataCardNavigate();
  const { tn } = useI18nContext();

  const closeAndReset = useCallback(() => {
    navigateToCurrentDashboard();
  }, [navigateToCurrentDashboard]);

  return (
    <DrawerPanel
      destroyOnClose
      maskClosable
      noPadding
      onClose={() => closeAndReset()}
      title={tn('title')}
      visible={Boolean(aiAssistedMatch)}
      width="full">
      <HStack spacing="z" align="start" className="ai-assisted-panel">
        <InterestingDataCards />
        <AiAssistCreateDataCards />
      </HStack>
    </DrawerPanel>
  );
}, 'InsightsStudio.InsightsGPT');
