import { installReviewItems } from 'components/quick-start-install-review/QuickStartInstall.fixtures';
import { ConfigRenderer } from 'components/skull';
import { QuickStartInstalls } from 'store/quick-start/types';

export const authorQuickStartsList = [
  {
    id: '1',
    displayName: 'My Custom Quick Start 1',
    author: 'Dan',
    description: 'A great quickstart',
    status: 'draft',
    tags: [],
    configuration: {},
    snapshotedAt: 'date',
  },
  {
    id: '3',
    displayName: 'My Custom Quick Start 3',
    author: 'Amber',
    description: 'A great quickstart',
    status: 'draft',
    tags: [],
    configuration: {},
    snapshotedAt: 'date',
  },
  {
    id: '2',
    displayName: 'My Custom Quick Start 2',
    author: 'Justin',
    description: 'A great quickstart',
    status: 'published',
    tags: [],
    configuration: {},
    snapshotedAt: 'date',
  },
];

export const quickStartLibraryItems: QuickStartInstalls = [
  {
    id: '1',
    displayName: 'Compete with Salesforce',
    requiredSynapses: ['Salesforce'],
    installStatus: null,
    config: {
      id: '1',
      configuration: [
        {
          renderType: 'displayText',
          id: 'quickStartTitle',
          name: 'quickStartTitle',
          textProps: {
            children: 'Compete with Salesforce',
            as: 'h3',
            size: 'xl',
          },
        },
        {
          renderType: 'displayText',
          id: 'requiredSynapsesText',
          name: 'requiredSynapsesText',
          textProps: {
            children: 'Published by Syncari<br />Requires Salesforce, HubSpot, Marketo',
            as: 'span',
            size: 'md',
            color: 'gray-750',
            beDangerous: true,
          },
        },
        {
          renderType: 'displayText',
          id: 'overviewDescription',
          name: 'overviewDescription',
          textProps: {
            children:
              'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur id placerat purus, et gravida elit. Morbi et magna ac nisi euismod laoreet. Morbi sed dignissim justo. Pellentesque imperdiet tincidunt ante, nec imperdiet diam maximus id. Aliquam erat volutpat. Phasellus venenatis nec purus sollicitudin dictum. Donec porttitor leo vel mauris gravida hendrerit semper a urna. Maecenas non sem massa. Aenean ut maximus quam. Mauris eget justo est. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam gravida est id interdum sodales.',
            as: 'p',
            size: 'md',
            color: 'black',
          },
        },
        {
          renderType: 'quickStartInstallErrorResolution',
          id: 'resolveSynapses',
          name: 'resolveSynapses',
          resolutionData: {
            type: 'create_synapse',
            synapseName: 'Salesforce',
          },
        },
        {
          renderType: 'quickStartInstallErrorResolution',
          id: 'matchSynapses',
          name: 'matchSynapses',
          resolutionData: {
            type: 'select_matches',
            matches: [
              {
                label: 'Select Marketo synapse',
                optionPlaceholder: 'Select synapse…',
                options: [{ label: 'Marketo-1', value: 'value' }],
              },
              {
                label: 'Select Salesforce synapse',
                optionPlaceholder: 'Select synapse…',
                options: [{ label: 'Salesforce', value: 'value' }],
              },
              {
                label: 'Select ZenDesk synapse',
                optionPlaceholder: 'Select synapse…',
                options: [{ label: 'ZenDesk', value: 'value' }],
              },
            ],
          },
        },
        {
          renderType: 'quickStartInstallErrorResolution',
          id: 'resolveServiceCredentials',
          name: 'resolveServiceCredentials',
          resolutionData: {
            type: 'service_credentials',
            serviceProvider: 'ClearBit',
          },
        },
        {
          renderType: 'quickStartInstallErrorResolution',
          id: 'resolveReferenceData',
          name: 'resolveReferenceData',
          resolutionData: {
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
          },
        },
        {
          datatype: 'infoBox',
          id: 'quickStartReview',
          name: 'quickStartReview',
          message: 'Review the changes this Quick Start will make',
          description:
            'Carefully review all of the changes that will be made to your instance below before running the Quick Start.',
          showIcon: false,
        },
        {
          renderType: 'quickStartInstallReview',
          id: 'quickStartInstallReview',
          name: 'quickStartInstallReview',
          reviewItems: installReviewItems,
        },
        {
          datatype: 'confirmationInfoBox',
          description:
            'We’ll send you a notification when this Quick Start has been fully run. You can safely close this window.',
          id: 'confirmQuickStart',
          message: 'We’ll notify you when complete',
          name: 'confirmQuickStart',
        },
      ],
      description: 'Quick start installation',
      displayName: 'Quick start installation',
      helpLink: 'https://syncari.helpdocs.io/quickstart',
      helpSummary: 'Quick start help',
      iconPath: null,
      name: 'install_quick_start',
      renderer: {
        renderType: ConfigRenderer.FULL_CONTENT_PANEL,
        steps: [
          {
            fields: ['quickStartTitle', 'publishedByText', 'requiredSynapsesText', 'overviewDescription'],
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
            stepName: 'Overview',
          },
          {
            fields: ['resolveSynapses'],
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
            stepName: 'Resolve Synapses',
          },
          {
            fields: ['matchSynapses'],
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
            stepName: 'Match Synapses',
          },
          {
            fields: ['resolveServiceCredentials'],
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
            stepName: 'Resolve Service Credentials',
          },
          {
            fields: ['resolveReferenceData'],
            stepName: 'Resolve Reference Data',
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
          },
          {
            fields: ['quickStartReview', 'quickStartInstallReview'],
            stepName: 'Review',
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
          },
          {
            closeStep: true,
            fields: ['confirmQuickStart'],
            stepName: 'Confirm',
            layout: {
              type: 'stack',
              className: 'synri-skull-stack-container-md',
            },
          },
        ],
        title: 'New Quickstart',
      },
    },
  },
  {
    id: '2',
    displayName: 'Synapse power',
    installStatus: null,
    requiredSynapses: ['Marketo', 'Netsuite', 'Salesforce'],
  },
  {
    id: '3',
    displayName: 'Salesforce for life',
    installStatus: null,
    requiredSynapses: ['Salesforce'],
  },
  {
    id: '4',
    displayName: 'Another Hubspot QS',
    installStatus: null,
    requiredSynapses: ['Hubspot'],
  },
];

export const quickStartSharedItems: QuickStartInstalls = [
  {
    id: '1',
    displayName: 'A Hubspot quickstart',
    installStatus: null,
    requiredSynapses: ['Hubspot'],
  },
  {
    id: '2',
    displayName: 'This is shared with me',
    installStatus: null,
    requiredSynapses: ['Marketo', 'Salesforce'],
  },
];
