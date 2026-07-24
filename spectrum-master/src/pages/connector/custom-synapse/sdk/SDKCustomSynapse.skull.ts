//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ConfigRenderer, SkullConfig } from 'components/skull';

export enum CustomSynapseRenderComponents {
  CREATE = 'create',
  AUTHENTICATE = 'authenticate',
  PAYLOAD_CONFIG = 'payload_config',
  RESPONSE = 'response',
  TEST_PAYLOAD = 'test_payload',
  SHARE = 'share',
  REVIEW = 'review',
}

export const getSDKCustomSynapseSkullConfig = (defaultFileUploadValue: any): SkullConfig => {
  return {
    id: 'staticSDKCustomSynapse',
    configuration: [
      {
        id: 'synapseFileUpload',
        renderType: 'sdkCustomSynapse',
        componentName: CustomSynapseRenderComponents.CREATE,
        name: 'synapseFileUpload',
        defaultValue: defaultFileUploadValue,
      },
      {
        id: 'customSynapseAuthenticationTest',
        renderType: 'sdkCustomSynapse',
        componentName: CustomSynapseRenderComponents.AUTHENTICATE,
        name: 'customSynapseAuthenticationTest',
        defaultValue: defaultFileUploadValue,
      },
      {
        id: 'customSynapseSharingOptions',
        renderType: 'sdkCustomSynapse',
        componentName: CustomSynapseRenderComponents.SHARE,
        name: 'customSynapseSharingOptions',
        defaultValue: defaultFileUploadValue,
      },
      {
        id: 'customSynapseReview',
        renderType: 'sdkCustomSynapse',
        componentName: CustomSynapseRenderComponents.REVIEW,
        name: 'customSynapseReview',
        defaultValue: defaultFileUploadValue,
      },
    ],
    description: 'Custom synapse wizard',
    displayName: 'Custom synapse wizard',
    helpLink: 'https://syncari.helpdocs.io/customsynapse',
    helpSummary: 'Custom synapse help',
    iconPath: null,
    name: 'custom_synapse',
    renderer: {
      renderType: ConfigRenderer.FULL_CONTENT_PANEL,
      title: 'New synapse',
      steps: [
        {
          applyStep: false,
          customFooter: true,
          stepName: defaultFileUploadValue?.id ? 'Edit synapse' : 'Create synapse',
          fields: ['synapseFileUpload'],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
        },
        {
          stepName: 'Test authentication',
          fields: ['customSynapseAuthenticationTest'],
          applyStep: false,
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-sm',
          },
        },
        {
          stepName: 'Sharing options',
          fields: ['customSynapseSharingOptions'],
          applyStep: false,
          customFooter: true,
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container',
          },
        },
        {
          stepName: 'Review and submit',
          fields: ['customSynapseReview'],
          closeStep: true,
          customFooter: true,
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-sm',
          },
        },
      ],
    },
  };
};
