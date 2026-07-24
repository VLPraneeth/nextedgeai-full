//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMatch } from '@reach/router';
import { Button, message } from 'antd';
import { ChangeEvent, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { entitiesItemPath } from 'pages/connector/custom-synapse/CustomSynapseBreadcrumb';
import { httpCustomSynapseEntitySteps } from 'pages/connector/custom-synapse/http/entities/Entity.skull';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { createApiName } from 'utils/StringUtil';
import useSetState from 'utils/useSetState';

export interface EntityBasicStepProps extends SkullRenderTypeBaseProps {
  defaultValue: HTTPCustomSynapseEntity;
}

export const entitiesInitialState: Partial<HTTPCustomSynapseEntity> = {
  id: '',
  apiName: '',
  displayName: '',
  metaId: '',
};

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

export const EntityBasicStep = ({ onChange, defaultValue }: EntityBasicStepProps) => {
  const [entity, setEntity] = useSetState<Partial<HTTPCustomSynapseEntity>>(() => {
    return { ...entitiesInitialState, ...defaultValue };
  });
  const { close, next } = useSkullConfigContext();

  const entityMatch = useMatch(entitiesItemPath);

  useEffect(() => {
    Object.keys(httpCustomSynapseEntitySteps).forEach((name) => {
      onChange({ name, value: entity });
    });
  }, [entity, onChange]);

  const handleNext = useCallback(() => {
    if (!entity.displayName?.trim().length) {
      message.error(tn('empty_input_validation', { label: tc('display_name') }));
      return;
    }
    if (!entity.apiName?.trim().length) {
      message.error(tn('empty_input_validation', { label: tc('api_name') }));
      return;
    }
    next();
  }, [entity, next]);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tc('cancel')}</Button>

      <Button onClick={handleNext} type="primary">
        {tc('next')}
      </Button>
    </>,
    footerRootNode
  );

  const isPublished = entityMatch?.version === 'published';

  return (
    <Stack className="http_custom_synapse_config_step">
      <InputWithLabel
        label={tc('display_name')}
        required
        readOnly={isPublished}
        value={entity.displayName}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setEntity({ displayName: newName.target.value });
        }}
        onBlur={() => {
          if (entity.displayName && !entity.id && !entity.apiName) {
            setEntity({ apiName: createApiName(entity.displayName) });
          }
        }}
      />

      <InputWithLabel
        label={tc('api_name')}
        // The name is not editable except when creating a new custom synapse
        disabled={!!entity.id}
        required
        readOnly={isPublished}
        value={entity.apiName}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setEntity({ apiName: createApiName(newName.target.value) });
        }}
      />

      <InputWithLabel
        label={tc('description')}
        value={entity.description}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setEntity({ description: newName.target.value });
        }}
        datatype="textarea"
        readOnly={isPublished}
      />

      <InputWithLabel
        label={tc('tags')}
        value={entity.tags}
        onChange={(tags: string[]) => {
          setEntity({ tags });
        }}
        disabled={isPublished}
        datatype="tag"
      />

      {footerPortal}
    </Stack>
  );
};
