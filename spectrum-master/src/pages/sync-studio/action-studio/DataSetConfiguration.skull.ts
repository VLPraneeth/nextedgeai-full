//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ConfigRenderer, SkullConfig } from 'components/skull';
import { removeNullOrUndefined } from 'utils/ArrayUtil';
import { t, tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Dataset');

export const getConfiguration = (): SkullConfig => ({
  id: 'staticDatasetConfig',
  configuration: [
    {
      id: 'id',
      name: 'id',
      label: tn('id_label'),
      helpSummary: tn('id_tooltip'),
      datatype: 'string',
    },
    {
      id: 'displayName',
      name: 'displayName',
      label: tn('display_name_label'),
      helpSummary: tn('display_name_tooltip'),
      datatype: 'string',
      required: true,
      validation: {
        required: true,
      },
    },
    {
      datatype: 'textarea',
      helpSummary: tn('description_tooltip'),
      id: 'description',
      label: tn('description_label'),
      name: 'description',
      placeholder: '',
    },
    {
      id: 'name',
      name: 'name',
      label: tn('api_name_label'),
      helpSummary: tn('api_name_tooltip'),
      datatype: 'string',
      required: true,
      validation: {
        required: true,
      },
    },
    {
      id: 'tags',
      name: 'tags',
      label: tn('tag_label'),
      helpSummary: tn('tag_tooltip'),
      datatype: 'tag',
    },
    {
      id: 'helpLink',
      name: 'helpLink',
      label: tn('help_link_label'),
      helpSummary: tn('help_link_tooltip'),
      datatype: 'string',
    },
    {
      datatype: 'textarea',
      helpSummary: 'Basic help text users will see when they view your data set.',
      id: 'basicHelpText',
      name: 'basicHelpText',
      label: 'Basic help text',
      placeholder: '',
    },
    {
      id: 'iconPath',
      name: 'iconPath',
      label: tn('icon_label'),
      helpSummary: tn('icon_tooltip'),
      datatype: 'image',
    },
    {
      id: 'datasetConfig',
      name: 'datasetConfig',
      renderType: 'datasetConfiguration',
      includeFormValues: true,
    },
    {
      id: 'variablesConfig',
      name: 'variablesConfig',
      renderType: 'variablesConfiguration',
      includeFormValues: true,
    },
  ],
  displayName: tn('wizard_default_title'),
  description: tn('wizard_default_description'),
  helpLink: 'https://syncari.helpdocs.io/dataset',
  helpSummary: tn('wizard_default_tooltip'),
  name: 'dataset',
  iconPath: null,
  renderer: {
    renderType: ConfigRenderer.FULL_CONTENT_PANEL,
    title: t('InsightsStudio.create_dataset'),
    steps: removeNullOrUndefined([
      {
        stepName: tn('basic_info'),
        fields: ['displayName', 'name', 'description', 'tags'],
        applyStep: true,
        cancel: {
          buttonText: tc('close'),
        },
        next: {
          buttonText: tc('save_and_next'),
        },
        layout: {
          type: 'stack',
          className: 'synri-skull-stack-container-md basic-info',
        },
      },
      {
        stepName: tn('configuration'),
        fields: ['datasetConfig'],
        applyStep: true,
        cancel: {
          buttonText: tc('close'),
        },
        next: {
          buttonText: 'Save & next',
        },
        layout: {
          type: 'stack',
        },
      },

      {
        stepName: 'Variables',
        fields: ['variablesConfig'],
        cancel: {
          buttonText: tc('close'),
        },
        applyStep: true,
        finish: {
          buttonText: tc('save_and_finish'),
        },
        layout: {
          type: 'stack',
          className: 'synri-skull-stack-container-lg',
        },
      },

      // {
      //   stepName: 'Review',
      //   fields: [],
      //   layout: {
      //     type: 'stack',
      //     className: 'synri-skull-stack-container-lg',
      //   },
      // },
      // {
      //   stepName: 'Confirmation',
      //   fields: [],
      //   layout: {
      //     type: 'stack',
      //     className: 'synri-skull-stack-container-md',
      //   },
      // },
    ]),
  },
});
