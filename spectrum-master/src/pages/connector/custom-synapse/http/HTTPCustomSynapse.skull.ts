//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomSynapse } from 'components/custom-synapse/types';
import { ConfigRenderer, SkullConfig } from 'components/skull';

import { CustomSynapseRenderComponents } from '../sdk/SDKCustomSynapse.skull';

export const httpCustomSynapseSteps = {
  configureStep: 'configureStep',
  authStep: 'authStep',
  reviewStep: 'reviewStep',
};

export const getHTTPCustomSynapseSkullConfig = (defaultValue: Partial<CustomSynapse>): SkullConfig => {
  return {
    id: 'staticHTTPCustomSynapse',
    configuration: [
      {
        id: httpCustomSynapseSteps.configureStep,
        renderType: 'httpCustomSynapse',
        componentName: CustomSynapseRenderComponents.CREATE,
        name: httpCustomSynapseSteps.configureStep,
        defaultValue: defaultValue as any,
        includeFormValues: true,
      },
      {
        id: httpCustomSynapseSteps.authStep,
        renderType: 'httpCustomSynapse',
        componentName: CustomSynapseRenderComponents.AUTHENTICATE,
        name: httpCustomSynapseSteps.authStep,
        defaultValue: defaultValue as any,
      },
      {
        id: httpCustomSynapseSteps.reviewStep,
        renderType: 'httpCustomSynapse',
        componentName: CustomSynapseRenderComponents.REVIEW,
        name: httpCustomSynapseSteps.reviewStep,
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
      title: 'New synapse',
      steps: [
        {
          applyStep: false,
          stepName: 'Configure',
          fields: [httpCustomSynapseSteps.configureStep],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
          customFooter: true,
        },
        {
          applyStep: false,
          stepName: 'Test & Authenticate',
          fields: [httpCustomSynapseSteps.authStep],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
          customFooter: true,
        },
        {
          stepName: 'Review & submit',
          fields: [httpCustomSynapseSteps.reviewStep],
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
