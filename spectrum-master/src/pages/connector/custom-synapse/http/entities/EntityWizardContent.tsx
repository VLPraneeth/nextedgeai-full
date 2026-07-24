//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMatch } from '@reach/router';
import { useMemo } from 'react';

import { HStack, Stack } from 'components/layout';
import { SkullConfigProvider, useSkullConfig } from 'components/skull';
import ConfigFooter from 'pages/sync-studio/node-config/ConfigFooter';
import ConfigPage from 'pages/sync-studio/node-config/ConfigPage';
import ConfigSteps from 'pages/sync-studio/node-config/ConfigSteps';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';

import { entitiesItemPath } from '../../CustomSynapseBreadcrumb';
import { getEntitySkullConfig } from './Entity.skull';

export interface EntityWizardContentProps {
  entity: HTTPCustomSynapseEntity | undefined;
  close?: () => void;
}

export const EntityWizardContent = ({ entity, close }: EntityWizardContentProps) => {
  const entityMatch = useMatch(entitiesItemPath);

  const config = useMemo(() => {
    return getEntitySkullConfig(
      {
        ...entity,
        metaId: entity?.metaId || entityMatch?.synapseId,
        type: entity?.type || 'LIMIT_OFFSET',
        limitParam: entity?.limitParam || '',
        limitValue: entity?.limitValue || 100,
        offsetParam: entity?.offsetParam || '',
        offsetValue: entity?.offsetValue || 0,
        pageNumberParam: entity?.pageNumberParam || '',
        pageNumberValue: entity?.pageNumberValue || 1,
        pageSizeParam: entity?.pageSizeParam || '',
        pageSize: entity?.pageSize || 100,
        cursorType: entity?.cursorType || 'parameter',
        nextCursorSelector: entity?.nextCursorSelector || '',
        nextCursorParam: entity?.nextCursorParam || '',
        startValue: entity?.startValue || '',
      },
      entityMatch?.version
    );
  }, [entity, entityMatch?.synapseId, entityMatch?.version]);

  const context = useSkullConfig({
    nodeConfig: config,
    configSteps: config.renderer.steps,
    configTitle: config.renderer.title,
    configInputs: config.configuration,
    configValue: entity,
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
