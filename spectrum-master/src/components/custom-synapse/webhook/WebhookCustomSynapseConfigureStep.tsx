//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button, message } from 'antd';
import { RcFile } from 'antd/lib/upload';
import { ChangeEvent, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';

import { SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS } from 'components/imageUpload/ImageUpload';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { webhookCustomSynapseSteps } from 'pages/connector/custom-synapse/webhook/WebhookCustomSynapse.skull';
import { AuthTypes } from 'store/credential/types';
import { useGetWebhookCustomSypapseAuthtypesQuery } from 'store/custom-synapse/webhook/api';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { createApiName } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';
import useSetState from 'utils/useSetState';

import { DEFAULT_CUSTOM_SYNAPSE_ICON } from '../sdk/SDKCustomSynapseFileUpload';
import { CustomSynapse } from '../types';

import './WebhookCustomSynapseConfigureStep.scss';

export interface WebhookCustomSynapseConfigurationProps extends SkullRenderTypeBaseProps {
  defaultValue: CustomSynapse;
}

export const webhookCustomSynapseInitialState: Partial<CustomSynapse> & { iconFile?: RcFile } = {
  id: '',
  name: '',
  displayName: '',
  authType: 'None',
};

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');
const tnCustomSynapse = tNamespaced('CustomSynapse');

export const WebhookCustomSynapseConfigureStep = ({
  onChange,
  defaultValue,
}: WebhookCustomSynapseConfigurationProps) => {
  const { data: authtypes } = useGetWebhookCustomSypapseAuthtypesQuery();
  const { close, next } = useSkullConfigContext();
  const [webhookSynapse, setWebhookSynapse] = useSetState(() => {
    return { ...webhookCustomSynapseInitialState, ...defaultValue };
  });

  useEffect(() => {
    Object.keys(webhookCustomSynapseSteps).forEach((name) => {
      onChange({ name, value: webhookSynapse });
    });
  }, [webhookSynapse, onChange]);

  const defaultCustomIconUrl = webhookSynapse.id
    ? makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_ICON, {
        connectorMetaDefinitionId: webhookSynapse.id,
      })
    : DEFAULT_CUSTOM_SYNAPSE_ICON;

  const handleNext = useCallback(() => {
    if (!webhookSynapse.displayName.trim().length) {
      message.error(tn('empty_input_validation', { label: tc('display_name') }));
      return;
    }
    if (!webhookSynapse.name.trim().length) {
      message.error(tn('empty_input_validation', { label: tc('api_name') }));
      return;
    }
    next();
  }, [webhookSynapse, next]);

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

  return (
    <Stack className="webhook_custom_synapse_config_step">
      <InputWithLabel
        label={tc('display_name')}
        required
        value={webhookSynapse.displayName}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setWebhookSynapse({ displayName: newName.target.value });
        }}
        onBlur={() => {
          if (webhookSynapse.displayName && !webhookSynapse.id && !webhookSynapse.name) {
            setWebhookSynapse({ name: createApiName(webhookSynapse.displayName) });
          }
        }}
      />

      <InputWithLabel
        label={tc('api_name')}
        // The name is not editable except when creating a new custom synapse
        disabled={!!webhookSynapse.id}
        required
        value={webhookSynapse.name}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setWebhookSynapse({ name: createApiName(newName.target.value) });
        }}
      />

      <InputWithLabel
        className="logo-upload"
        tooltip={tnCustomSynapse('custom_synapse_icon_tooltip')}
        defaultValue={defaultCustomIconUrl}
        datatype="image"
        id="iconFile"
        name="iconFile"
        label={tn('custom_icon')}
        value={webhookSynapse.iconFile}
        accept={SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS}
        onChange={(iconFile: RcFile) => {
          setWebhookSynapse({ iconFile });
        }}
      />

      <InputWithLabel
        label={tn('authentication_type')}
        value={webhookSynapse.authType}
        onChange={(authType: AuthTypes) => {
          setWebhookSynapse({ authType });
        }}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        optionData={authtypes?.map((type) => ({
          label: type.label,
          value: type.authType,
        }))}
      />
      {footerPortal}
    </Stack>
  );
};
