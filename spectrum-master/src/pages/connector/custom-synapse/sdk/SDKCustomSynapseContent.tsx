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

import { getSDKCustomSynapseSkullConfig } from './SDKCustomSynapse.skull';

export interface SDKCustomSynapseContentProps {
  customSynapse: CustomSynapse | undefined;
  close?: () => void;
}

const SDKCustomSynapseContent = ({ customSynapse, close }: SDKCustomSynapseContentProps) => {
  const config = useMemo(() => {
    return getSDKCustomSynapseSkullConfig({
      id: customSynapse?.id || '',
      name: customSynapse?.name || '',
      displayName: customSynapse?.displayName || '',
      publishToGlobal: customSynapse?.publishToGlobal || false,
      isGlobal: customSynapse?.isGlobal || false,
    });
  }, [
    customSynapse?.displayName,
    customSynapse?.id,
    customSynapse?.isGlobal,
    customSynapse?.name,
    customSynapse?.publishToGlobal,
  ]);

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
              <ConfigPage className="synri-config-page-quick-start-v2" />
            </div>
            <ConfigFooter onClose={close} />
          </div>
        </HStack>
      </Stack>
    </SkullConfigProvider>
  );
};

export default SDKCustomSynapseContent;
