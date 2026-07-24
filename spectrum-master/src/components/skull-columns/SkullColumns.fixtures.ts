import { pipelineSchemas } from 'components/pipeline-picker/PipelinePicker.fixtures';
import AppConstants from 'utils/AppConstants';

import { SkullColumnsType } from './SkullColumns';

export const skullColumnsFixture: SkullColumnsType = [
  {
    span: 14,
    items: [
      {
        id: 'settingsHeader',
        name: 'settingsHeader',
        renderType: 'jumpToStepLabel',
        text: 'Basic settings',
        buttonText: 'Edit',
        stepNumber: 0,
      },
      {
        id: 'displayNamePreview',
        name: 'displayNamePreview',
        datatype: 'string',
        label: 'Display name',
        value: 'Quick Start Title Here',
        tooltip: 'Name visible for installer',
        displayMode: AppConstants.INPUT_DISPLAY_MODE.READONLY,
      },
      {
        id: 'descriptionPreview',
        name: 'descriptionPreview',
        datatype: 'string',
        label: 'Description',
        value:
          'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur id placerat purus, et gravida elit. Morbi et magna ac nisi euismod laoreet. Morbi sed dignissim justo. Pellentesque imperdiet tincidunt ante, nec imperdiet diam maximus id. Aliquam erat volutpat. Phasellus venenatis nec purus sollicitudin dictum. Donec porttitor leo vel mauris gravida hendrerit semper a urna. Maecenas non sem massa.',
        tooltip: 'Name visible for installer',
        displayMode: AppConstants.INPUT_DISPLAY_MODE.READONLY,
      },
      {
        id: 'tagPreview',
        name: 'tagPreview',
        datatype: 'tag',
        label: 'Tags',
        tooltip: 'Tags are used to improve search results',
        displayMode: AppConstants.INPUT_DISPLAY_MODE.READONLY,
        value: ['one', 'two'],
      },
      {
        id: 'postInstallMessagePreview',
        name: 'postInstallMessagePreview',
        datatype: 'string',
        label: 'Post-installation message',
        value:
          'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur id placerat purus, et gravida elit. Morbi et magna ac nisi euismod laoreet. Morbi sed dignissim justo. Pellentesque imperdiet tincidunt ante, nec imperdiet diam maximus id. Aliquam erat volutpat. Phasellus venenatis nec purus sollicitudin dictum. Donec porttitor leo vel mauris gravida hendrerit semper a urna. Maecenas non sem massa.',
        tooltip: 'Name visible for installer',
        displayMode: AppConstants.INPUT_DISPLAY_MODE.READONLY,
      },
    ],
  },
  {
    span: 10,
    items: [
      {
        id: 'pipelineSettingsHeader',
        name: 'pipelineSettingsHeader',
        renderType: 'jumpToStepLabel',
        text: 'Pipeline settings',
        buttonText: 'Edit',
        stepNumber: 1,
      },
      {
        id: 'pipelinePickerPreview',
        name: 'pipelinePickerPreview',
        renderType: 'pipelinePickerPreview',
        entities: pipelineSchemas,
      },
    ],
  },
];
