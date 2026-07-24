//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ConfigRenderer, SkullConfig } from 'components/skull';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';

export enum EntitiesRenderComponents {
  BASIC = 'basic',
  CONFIG = 'config',
  REVIEW = 'review',
}

export const httpCustomSynapseEntitySteps = {
  basicStep: 'basicStep',
  configStep: 'configStep',
  reviewStep: 'reviewStep',
};

export const getEntitySkullConfig = (
  defaultValue: Partial<HTTPCustomSynapseEntity>,
  statusView: string | undefined
): SkullConfig => {
  return {
    id: 'staticEntities',
    configuration: [
      {
        id: httpCustomSynapseEntitySteps.basicStep,
        renderType: 'httpCustomSynapseEntity',
        componentName: EntitiesRenderComponents.BASIC,
        name: httpCustomSynapseEntitySteps.basicStep,
        defaultValue: defaultValue as any,
        includeFormValues: true,
      },
      {
        id: httpCustomSynapseEntitySteps.configStep,
        renderType: 'httpCustomSynapseEntity',
        componentName: EntitiesRenderComponents.CONFIG,
        name: httpCustomSynapseEntitySteps.configStep,
        defaultValue: defaultValue as any,
      },
      ...(statusView === 'draft'
        ? [
            {
              id: httpCustomSynapseEntitySteps.reviewStep,
              renderType: 'httpCustomSynapseEntity',
              componentName: EntitiesRenderComponents.REVIEW,
              name: httpCustomSynapseEntitySteps.reviewStep,
              defaultValue: defaultValue as any,
            },
          ]
        : ([] as any)),
    ],

    description: 'Entity wizard',
    displayName: 'Entity  wizard',
    helpLink: 'https://syncari.helpdocs.io/customsynapse',
    helpSummary: 'Custom synapse help',
    iconPath: null,
    name: 'httpCustomSynapseEntities',
    renderer: {
      renderType: ConfigRenderer.FULL_CONTENT_PANEL,
      title: 'New Entity',
      steps: [
        {
          applyStep: false,
          stepName: 'Basic settings',
          fields: [httpCustomSynapseEntitySteps.basicStep],
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
          customFooter: true,
        },
        {
          stepName: 'Configuration',
          fields: [httpCustomSynapseEntitySteps.configStep],
          customFooter: true,
          layout: {
            type: 'stack',
            className: 'synri-skull-stack-container-md',
          },
        },
        ...(statusView === 'draft'
          ? [
              {
                stepName: 'Review & Finish',
                fields: [httpCustomSynapseEntitySteps.reviewStep],
                closeStep: true,
                customFooter: true,
                layout: {
                  type: 'stack',
                  className: 'synri-skull-stack-container-md',
                },
              },
            ]
          : ([] as any)),
      ],
    },
  };
};
