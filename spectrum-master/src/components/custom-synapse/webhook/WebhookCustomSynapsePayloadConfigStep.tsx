//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button } from 'antd';
import { ChangeEvent, useEffect } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';
import { createPortal } from 'react-dom';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { webhookCustomSynapseSteps } from 'pages/connector/custom-synapse/webhook/WebhookCustomSynapse.skull';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import {
  WebhookCustomSynapseConfigurationProps,
  webhookCustomSynapseInitialState,
} from './WebhookCustomSynapseConfigureStep';

import './WebhookCustomSynapseConfigureStep.scss';

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

export const WebhookCustomSynapsePayloadConfigStep = ({
  onChange,
  defaultValue,
}: WebhookCustomSynapseConfigurationProps) => {
  const { close, next, previous } = useSkullConfigContext();
  const [webhookSynapse, setWebhookSynapse] = useSetState(() => {
    return { ...webhookCustomSynapseInitialState, ...defaultValue };
  });

  useEffect(() => {
    Object.keys(webhookCustomSynapseSteps).forEach((name) => {
      onChange({ name, value: webhookSynapse });
    });
  }, [webhookSynapse, onChange]);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tc('cancel')}</Button>

      <Button onClick={previous}>{tc('previous')}</Button>

      <Button onClick={next} type="primary">
        {tc('next')}
      </Button>
    </>,
    footerRootNode
  );

  return (
    <Stack className="webhook_custom_synapse_config_step">
      <InputWithLabel
        label={tn('record_selector')}
        tooltip={tn('record_selector_tooltip')}
        value={webhookSynapse.recordSelector}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setWebhookSynapse({ recordSelector: newName.target.value });
        }}
      />

      <InputWithLabel
        label={tn('id_selector')}
        tooltip={tn('id_selector_tooltip')}
        value={webhookSynapse.idSelector}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setWebhookSynapse({ idSelector: newName.target.value });
        }}
      />

      <InputWithLabel
        label={tn('json_schema')}
        tooltip={tn('json_schema_tooltip')}
        input={
          <CodeMirror
            className="code-mirror-container"
            value={webhookSynapse.schema || ''}
            options={getCodeMirrorOptions(false)}
            onBeforeChange={(editor, data, schema) => setWebhookSynapse({ schema })}
          />
        }
      />

      {footerPortal}
    </Stack>
  );
};
