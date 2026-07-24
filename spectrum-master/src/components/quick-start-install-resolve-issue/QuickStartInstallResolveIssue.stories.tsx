/* eslint-disable @typescript-eslint/no-unused-vars */
//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Meta, StoryFn } from '@storybook/react';

import QuickStartInstallResolveIssue, { QuickStartInstallResolveIssueProps } from './QuickStartInstallResolveIssue';
import './QuickStartInstallResolveIssue.less';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://www.figma.com/file/OHD0gVxm76XBdKN6aKbALE/Button-Patterns?node-id=0%3A1',
  },
};

const Template: StoryFn<QuickStartInstallResolveIssueProps> = (args) => <QuickStartInstallResolveIssue {...args} />;

export const BaseText = Template.bind({});

const matchSynapses: Partial<QuickStartInstallResolveIssueProps> = {
  type: 'select_matches',
  title: 'Select synapses',
  description: 'Select which synapses will be used during this process below.',
  onChange: () => {},
  defaultValue: {
    '1': null,
    '2': 'salesforce',
    '3': 'zendesk',
  },
  matches: [
    {
      id: '1',
      label: 'Select Marketo synapse',
      optionPlaceholder: 'Select synapse…',
      options: [{ label: 'Marketo-1', value: 'marketo' }],
    },
    {
      id: '2',
      label: 'Select Salesforce synapse',
      optionPlaceholder: 'Select synapse…',
      options: [{ label: 'Salesforce', value: 'salesforce' }],
    },
    {
      id: '3',
      label: 'Select ZenDesk synapse',
      optionPlaceholder: 'Select synapse…',
      options: [{ label: 'ZenDesk', value: 'zendesk' }],
    },
  ],
};

const serviceCredentials: Partial<QuickStartInstallResolveIssueProps> = {
  type: 'service_credentials',
  serviceProvider: 'ClearBit',
};

const referenceData: Partial<QuickStartInstallResolveIssueProps> = {
  type: 'reference_data',
  datasetTitle: 'Airport Codes',
  columns: [
    'Ident',
    'Type',
    'Name',
    'Elevation ft',
    'Continent',
    'Iso country',
    'Iso region',
    'Municipality',
    'Gps code',
    'lata code',
    'Local code',
    'Coordinates',
  ],
};

const createSynapse: Partial<QuickStartInstallResolveIssueProps> = {
  type: 'create_synapse',
  synapseName: 'Salesforce',
};

const issueResolved: Partial<QuickStartInstallResolveIssueProps> = {
  type: 'issue_resolved',
  successTitle: 'Success!',
  successMessage: 'Success message',
};

BaseText.args = createSynapse;
BaseText.storyName = 'QuickStartInstallResolveIssue';
BaseText.parameters = BaseDesignParameters;

export default {
  title: 'input/QuickStartInstallResolveIssue',
  component: Template,
} as Meta;
