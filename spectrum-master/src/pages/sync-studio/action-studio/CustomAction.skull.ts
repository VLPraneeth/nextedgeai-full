//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ConfigRenderer, SkullConfig } from 'components/skull';

import { DEFAULT_ACTION_ICON } from './ActionStudioList';

export const configuration: SkullConfig = {
  id: 'staticCustomActionConfig',
  configuration: [
    {
      id: 'customActionBasicSettingsDescription',
      datatype: 'infoBox',
      message: 'Set up your basic Action settings',
      description: 'Configure how your action will appear to users during use, including name and description.',
      name: 'customActionBasicSettingsDescription',
      showIcon: false,
    },
    {
      id: 'displayName',
      datatype: 'string',
      helpSummary: 'Name of your custom action',
      name: 'displayName',
      label: 'Display name',
    },
    {
      datatype: 'textarea',
      helpSummary: 'Description of your custom action',
      id: 'description',
      label: 'Description',
      name: 'description',
      placeholder: '',
    },
    {
      id: 'apiName',
      datatype: 'string',
      helpSummary: 'API name of your custom action',
      name: 'apiName',
      label: 'API name',
    },
    {
      datatype: 'tag',
      helpSummary: 'Tags are used to improve search results',
      id: 'tags',
      label: 'Tags',
      name: 'tags',
    },
    {
      id: 'helpLink',
      name: 'helpLink',
      datatype: 'string',
      helpSummary: 'Link to the help of the custom action',
      label: 'Help link',
    },
    {
      datatype: 'textarea',
      helpSummary: 'Basic help text users will see when they view your custom action.',
      id: 'basicHelpText',
      name: 'basicHelpText',
      label: 'Basic help text',
      placeholder: '',
    },
    {
      datatype: 'image',
      helpSummary: 'Icon to represent this custom action',
      id: 'iconPath',
      name: 'iconPath',
      defaultValue: DEFAULT_ACTION_ICON,
      label: 'Custom icon',
      accept: ['.png', '.jpg', '.jpeg', '.svg'],
    },
    {
      datatype: 'infoBox',
      message: 'Configure behavior for your Action',
      description:
        'Authenticate and test the endpoint for your Action, including any variables needed by the end user.',
      id: 'actionSetupDescription',
      name: 'actionSetupDescription',
      showIcon: false,
    },
    {
      id: 'actionConfiguration',
      name: 'actionConfiguration',
      renderType: 'actionConfiguration',
    },
    {
      datatype: 'infoBox',
      message: 'Review your Action settings',
      description: 'Confirm your expected configuration before publishing your action.',
      id: 'reviewSettingsDescription',
      name: 'reviewSettingsDescription',
      showIcon: false,
    },
    {
      renderType: 'customActionReview',
      id: 'customActionReview',
      name: 'customActionReview',
      includeFormValues: true,
    },
    {
      datatype: 'confirmationInfoBox',
      id: 'customActionConfirmation',
      message: 'Your Action has been saved',
      description: 'You can safely close this panel.',
      name: 'customActionConfirmation',
    },
  ],
  description: 'Custom action wizard',
  displayName: 'Custom action wizard',
  helpLink: 'https://syncari.helpdocs.io/customaction',
  helpSummary: 'Custom action help',
  iconPath: null,
  name: 'custom_action',
  renderer: {
    renderType: ConfigRenderer.FULL_CONTENT_PANEL,
    title: 'New action',
    steps: [
      {
        stepName: 'Basic settings',
        fields: [
          'customActionBasicSettingsDescription',
          'displayName',
          'description',
          'apiName',
          'tags',
          'basicHelpText',
          'helpLink',
          'iconPath',
        ],
        layout: {
          type: 'stack',
          className: 'synri-skull-stack-container-md',
        },
      },
      {
        stepName: 'Action setup',
        fields: ['actionSetupDescription', 'actionConfiguration'],
        layout: {
          type: 'stack',
          className: 'synri-skull-stack-container-lg',
        },
      },
      {
        stepName: 'Review',
        fields: ['reviewSettingsDescription', 'customActionReview'],
        applyStep: true,
        layout: {
          type: 'stack',
          className: 'synri-skull-stack-container-lg',
        },
      },
      {
        stepName: 'Confirmation',
        fields: ['customActionConfirmation'],
        layout: {
          type: 'stack',
          className: 'synri-skull-stack-container-md',
        },
      },
    ],
  },
};
