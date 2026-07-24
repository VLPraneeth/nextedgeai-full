//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { isUndefined } from 'lodash';
import { useEffect, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { SkullConfig } from 'components/skull';
import TabPanelSpin from 'components/TabPanelSpin';
import { useRunQuickStartMutation } from 'store/quick-start/api';
import { QuickStart, QuickStartMode } from 'store/quick-start/types';

import QuickStartContent from './QuickStartContent';

import '../../node-config/ConfigWizard.less';
import './QuickStartWizard.less';

export interface QuickStartWizardProps {
  visible?: boolean;
  close?: () => void;
  config: SkullConfig | null;
  mode: QuickStartMode;
  quickStart: QuickStart | null;
}

const QuickStartWizard = ({ visible, close, config, quickStart, mode }: QuickStartWizardProps) => {
  // Use the isloading of the lazy query instead of this
  const [spinning, setSpinning] = useState(mode === QuickStartMode.INSTALL && !config);
  const { tn } = useI18nContext();

  const [quickStartConfig, setQuickStartConfig] = useState<SkullConfig | null>(
    mode === QuickStartMode.AUTHOR ? config : null
  );
  const [runQuickStart] = useRunQuickStartMutation();

  useEffect(() => {
    async function runSelectedQuickStart() {
      if (quickStart) {
        const result = await runQuickStart({ quickStartId: quickStart.id }).unwrap();
        setSpinning(false);
        if (result && !isUndefined(result.config)) {
          setQuickStartConfig(result.config);
        }
      }
    }
    if (mode === QuickStartMode.INSTALL && quickStart) {
      runSelectedQuickStart();
    }
  }, [mode, quickStart, runQuickStart]);

  const panelTitle = quickStart
    ? mode === QuickStartMode.AUTHOR
      ? tn('edit_title', { name: quickStart?.displayName, interpolation: { escapeValue: false } })
      : tn('install_title', { name: quickStart?.displayName, interpolation: { escapeValue: false } })
    : tn('install_title_default');

  return (
    <DrawerPanel
      className="synri-config-full-content"
      keyboard={false}
      maskClosable={false}
      noPadding
      onClose={close}
      title={panelTitle}
      visible={visible}
      width="full">
      <TabPanelSpin spinning={spinning} size="default">
        {quickStartConfig && (
          <QuickStartContent close={close} mode={mode} quickStart={quickStart} config={quickStartConfig} />
        )}
      </TabPanelSpin>
    </DrawerPanel>
  );
};

export default withI18n(QuickStartWizard, 'QuickStart');
