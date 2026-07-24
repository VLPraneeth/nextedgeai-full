//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMemo } from 'react';

import { CustomSynapse } from 'components/custom-synapse/types';
import { HStack, Stack } from 'components/layout';
import { SkullConfigProvider, useSkullConfig } from 'components/skull';
import ConfigFooter from 'pages/sync-studio/node-config/ConfigFooter';
import ConfigPage from 'pages/sync-studio/node-config/ConfigPage';
import ConfigSteps from 'pages/sync-studio/node-config/ConfigSteps';

import { getHTTPCustomSynapseSkullConfig } from './HTTPCustomSynapse.skull';

export interface HTTPCustomSynapseContentProps {
  customSynapse: CustomSynapse | undefined;
  close?: () => void;
}

const HTTPCustomSynapseContent = ({ customSynapse, close }: HTTPCustomSynapseContentProps) => {
  const config = useMemo(() => {
    return getHTTPCustomSynapseSkullConfig({
      ...customSynapse,
      authType: customSynapse?.authType || 'None',
    });
  }, [customSynapse]);

  const context = useSkullConfig({
    nodeConfig: config,
    configSteps: config.renderer.steps,
    configTitle: config.renderer.title,
    configInputs: config.configuration,
    configValue: customSynapse,
    close,
    groupConfiguration: {},
  });

  return (
    <SkullConfigProvider value={context}>
      <Stack spacing="z">
        <HStack spacing="z" className="synri-quick-start-wizard-container">
          <ConfigSteps direction="vertical" />
          <div className="synri-quick-start-body-wrapper">
            <div className="synri-quick-start-wrapper">
              <ConfigPage />
            </div>
            <ConfigFooter onClose={close} />
          </div>
        </HStack>
      </Stack>
    </SkullConfigProvider>
  );
};

export default HTTPCustomSynapseContent;
