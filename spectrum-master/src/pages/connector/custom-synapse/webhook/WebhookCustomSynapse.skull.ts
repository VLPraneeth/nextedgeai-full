//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomSynapse } from 'components/custom-synapse/types';
import { ConfigRenderer, SkullConfig } from 'components/skull';

import { CustomSynapseRenderComponents } from '../sdk/SDKCustomSynapse.skull';

export const webhookCustomSynapseSteps = {
  configureStep: 'configureStep',
  payloadConfig: 'payloadConfig',
  responseConfig: 'responseConfig',
  testPayload: 'testPayload',
  reviewStep: 'reviewStep',
};

export const getWebhookCustomSynapseSkullConfig = (defaultValue: Partial<CustomSynapse>): SkullConfig => {
  return {
    id: 'staticWebhookCustomSynapse',
    configuration: [
      {
        id: webhookCustomSynapseSteps.configureStep,
        renderType: 'webhookCustomSynapse',
        componentName: CustomSynapseRenderComponents.CREATE,
        name: webhookCustomSynapseSteps.configureStep,
        defaultValue: defaultValue as any,
        includeFormValues: true,
      },
      {
        id: webhookCustomSynapseSteps.payloadConfig,
        renderType: 'webhookCustomSynapse',
        componentName: CustomSynapseRenderComponents.PAYLOAD_CONFIG,
        name: webhookCustomSynapseSteps.payloadConfig,
        defaultValue: defaultValue as any,
      },
      {
        id: webhookCustomSynapseSteps.responseConfig,
        renderType: 'webhookCustomSynapse',
        componentName: CustomSynapseRenderComponents.RESPONSE,
        name: webhookCustomSynapseSteps.responseConfig,
        defaultValue: defaultValue as any,
      },
      {
        id: webhookCustomSynapseSteps.testPayload,
        renderType: 'webhookCustomSynapse',
        componentName: CustomSynapseRenderComponents.TEST_PAYLOAD,
        name: webhookCustomSynapseSteps.testPayload,
        defaultValue: defaultValue as any,
      },
      {
        id: webhookCustomSynapseSteps.reviewStep,
        renderType: 'webhookCustomSynapse',
        componentName: CustomSynapseRenderComponents.REVIEW,
        name: webhookCustomSynapseSteps.reviewStep,
        defaultValue: defaultValue as any,
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
      title: 'New Webhook synapse',
      steps: [
        {
          applyStep: false,
          stepName: 'Configure',
          fields: [webhookCustomSynapseSteps.configureStep],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
          customFooter: true,
        },
        {
          applyStep: false,
          stepName: 'Payload Configuration',
          fields: [webhookCustomSynapseSteps.payloadConfig],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
          customFooter: true,
        },
        {
          applyStep: false,
          stepName: 'Response',
          fields: [webhookCustomSynapseSteps.responseConfig],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
          customFooter: true,
        },
        {
          stepName: 'Test Payload',
          fields: [webhookCustomSynapseSteps.testPayload],
          closeStep: true,
          customFooter: true,
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
        },
        {
          stepName: 'Review & submit',
          fields: [webhookCustomSynapseSteps.reviewStep],
          closeStep: true,
          customFooter: true,
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
        },
      ],
    },
  };
};
