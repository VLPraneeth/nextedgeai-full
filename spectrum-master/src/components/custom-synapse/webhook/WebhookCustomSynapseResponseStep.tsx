//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button } from 'antd';
import { useEffect } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';
import { createPortal } from 'react-dom';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { webhookCustomSynapseSteps } from 'pages/connector/custom-synapse/webhook/WebhookCustomSynapse.skull';
import { useGetWebhookCustomSypapseHttpCodesQuery } from 'store/custom-synapse/webhook/api';
import AppConstants from 'utils/AppConstants';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import {
  WebhookCustomSynapseConfigurationProps,
  webhookCustomSynapseInitialState,
} from './WebhookCustomSynapseConfigureStep';

import './WebhookCustomSynapseConfigureStep.scss';

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

export const WebhookCustomSynapseResponseStep = ({
  onChange,
  defaultValue,
}: WebhookCustomSynapseConfigurationProps) => {
  const { close, next, previous } = useSkullConfigContext();
  const [webhookSynapse, setWebhookSynapse] = useSetState(() => {
    return { ...webhookCustomSynapseInitialState, ...defaultValue };
  });

  const { data: httpCodes } = useGetWebhookCustomSypapseHttpCodesQuery();

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
        label={tn('http_status')}
        value={webhookSynapse.responseCode}
        onChange={(responseCode: number) => {
          setWebhookSynapse({ responseCode });
        }}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        optionData={httpCodes?.map((code) => ({
          label: code.name,
          value: code.value,
        }))}
      />

      <InputWithLabel
        label={tn('response_template')}
        input={
          <CodeMirror
            className="code-mirror-container"
            value={webhookSynapse.responseTemplate || ''}
            options={getCodeMirrorOptions(false)}
            onBeforeChange={(editor, data, responseTemplate) => setWebhookSynapse({ responseTemplate })}
          />
        }
      />

      {footerPortal}
    </Stack>
  );
};
