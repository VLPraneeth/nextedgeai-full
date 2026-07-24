//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ConnectorSyncStatusModel } from 'store/entity-pipeline/types';
import { render, renderWithRouter, screen } from 'tests/helpers';

import { DirectionId } from '../../types';
import { ConnectorSyncStatus } from '../ConnectorSyncStatusList';
import { SyncStatus } from '../SyncStatus';

const connectorsMetadata = [
  {
    id: '5f619d485dad1b58c597204f',
    name: 'salesforce',
    type: 'Synapse',
    displayName: 'Salesforce',
    description: null,
    category: 'CRM',
    iconUri: '/assets/icons/logos/salesforce.svg',
    idFieldName: null,
    watermarkFieldName: 'SystemModstamp',
    createdAtFieldName: null,
    updatedAtFieldName: null,
    watermarkCustomizable: false,
    defaultApiLimit: 1000,
    supportedAuthTypes: [
      {
        authType: 'UserPasswordToken',
        fields: [
          { name: 'userName', dataType: 'text', label: 'User Name', helpSummary: null },
          { name: 'password', dataType: 'password', label: 'Password', helpSummary: null },
          { name: 'token', dataType: 'password', label: 'Token', helpSummary: null },
        ],
        label: 'User Password Token',
        helpSummary: '',
      },
    ],
    configureFields: [
      { name: 'endpoint', dataType: 'text', label: 'Endpoint URL', helpSummary: null },
      { name: 'authType', dataType: 'picklist', label: 'Authentication', helpSummary: null },
    ],
    watermarkFieldOverrides: {},
    disabledMessage: null,
    oauthUri: null,
  },
  {
    id: '5f619d485dad1b58c5972050',
    name: 'hubspot',
    type: 'Synapse',
    displayName: 'Hubspot',
    description: null,
    category: 'Marketing',
    iconUri: '/assets/icons/logos/hubspot.svg',
    idFieldName: null,
    watermarkFieldName: 'hs_lastmodifieddate',
    createdAtFieldName: null,
    updatedAtFieldName: 'hs_lastmodifieddate',
    watermarkCustomizable: false,
    defaultApiLimit: 1000,
    supportedAuthTypes: [
      {
        authType: 'Oauth',
        fields: [
          {
            name: 'clientId',
            dataType: 'password',
            label: 'Client ID',
            helpSummary: 'Public identifier of your application.',
          },
          {
            name: 'clientSecret',
            dataType: 'password',
            label: 'Client Secret',
            helpSummary: 'It is a secret known only to the application and the application authorization server.',
          },
        ],
        label: 'OAuth',
        helpSummary: '',
      },
    ],
    configureFields: [{ name: 'authType', dataType: 'picklist', label: 'Authentication', helpSummary: null }],
    watermarkFieldOverrides: { contact: 'lastmodifieddate' },
    disabledMessage: null,
    oauthUri:
      '/oauth/authorize?redirect_uri={{redirect_uri}}&client_id={{client_id}}&scope=contacts oauth integration-sync',
  },
];

describe('Sync Status Panel', () => {
  test('Show sync status and the sync status is not available yet', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af7" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Sync status not available.')).toBeInTheDocument();
  });
  test('Show sync status unpublished', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af7" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 0,
            lastSyncTime: null,
            status: 'UNPUBLISHED',
            syncariEntityId: '5f619d7b5dad1b58e7893af7',
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Never synced')).toBeInTheDocument();
  });

  test('Show sync status running', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'RUNNING',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
          },
          pipelineError: {},
        },
      },
    });
    expect(await screen.findByText('Running')).toBeInTheDocument();
    expect(await screen.findByText(/Synced.*PM/)).toBeInTheDocument();
  });

  test('Show sync status testing', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'TEST',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Testing')).toBeInTheDocument();
  });

  test('Show sync status stalled', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'STALLED',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Stalled')).toBeInTheDocument();
  });

  test('Show sync status ready', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'READY',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Queued')).toBeInTheDocument();
  });

  test('Show sync status error', async () => {
    render(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 100,
            errorDetails: 'Lorem ipsum',
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'ERROR',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
          },
          summary: {
            sources: [
              {
                entityName: 'Account',
                entityId: '5f64f6e3e4ed398bb63178e6',
                connectorName: 'sfdcone',
                connectorType: 'salesforce',
                processedUpTo: '2020-09-22T22:00:53.070Z',
                historicalSync: false,
              },
            ],
            sinks: [
              {
                entityName: 'Account',
                entityId: '5f64f6e3e4ed398bb63178e6',
                connectorName: 'sfdcone',
                connectorType: 'salesforce',
                processedUpTo: null,
                historicalSync: false,
              },
            ],
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Error')).toBeInTheDocument();
    expect(await screen.findByText('Lorem ipsum')).toBeInTheDocument();
    expect(await screen.findByText('Error Last Cycle:')).toBeInTheDocument();
    expect(await screen.findByText('100')).toBeInTheDocument();
  });

  test('Show sync status running with short list of sources and sink', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'RUNNING',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
            summary: {
              sources: [
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e6',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
              ],
              sinks: [
                {
                  entityName: 'Lead',
                  entityId: '5f64f6e3e4ed398bb63178e6',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: null,
                  historicalSync: false,
                },
              ],
            },
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Running')).toBeInTheDocument();
    expect(await screen.findByText('Sources')).toBeInTheDocument();
    expect(await screen.findByText('sfdcone / Account')).toBeInTheDocument();
    expect(await screen.findByText('Last change was at 9/22/2020 10:00:53 PM GMT')).toBeInTheDocument();
    expect(await screen.findByText('Destinations')).toBeInTheDocument();
    expect(document.querySelector('input[placeholder="Filter Sources…"]')).toBeNull();
    expect(document.querySelector('input[placeholder="Filter Destinations…"]')).toBeNull();
  });

  test('Show sync status running with long list filter for sinks and sources', async () => {
    renderWithRouter(<SyncStatus entityId="5f619d7b5dad1b58e7893af3" />, {
      testState: {
        // @ts-expect-error: type mismatch for test fixture
        connector: { connectorsMetadata },
        entityPipeline: {
          getSyncStatusStatus: 'idle',
          entitySyncStatus: {
            errorCount: 0,
            errorDetails: null,
            lagTimeInSeconds: 173,
            lastSyncTime: '2020-09-30T18:11:15.648Z',
            status: 'RUNNING',
            syncariEntityId: '5f619d7b5dad1b58e7893af3',
            summary: {
              sources: [
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e6',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e7',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e8',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e9',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178f0',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
              ],
              sinks: [
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e6',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e7',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e8',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178e9',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:53.070Z',
                  historicalSync: false,
                },
                {
                  entityName: 'Account',
                  entityId: '5f64f6e3e4ed398bb63178f0',
                  connectorName: 'sfdcone',
                  connectorType: 'salesforce',
                  processedUpTo: '2020-09-22T22:00:55.070Z',
                  historicalSync: false,
                },
              ],
            },
          },
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Running')).toBeInTheDocument();
    expect(document.querySelector('input[placeholder="Filter Sources…"]')).toBeVisible();
    expect(document.querySelector('input[placeholder="Filter Destinations…"]')).toBeVisible();
    expect(await screen.findByText('Last change was at 9/22/2020 10:00:55 PM GMT')).toBeInTheDocument();
  });
});

describe('ConnectorSyncStatus', () => {
  const syncStatus = {
    connectorName: 'connectorName',
    connectorType: 'connectorType',
    entityId: 'entityId',
    entityName: 'entityName',
    historicalSync: false,
  };

  test('should render the `Last change was at` when provided', async () => {
    render(
      <ConnectorSyncStatus
        syncDirection={DirectionId.SYNC_FROM}
        syncStatus={{ ...syncStatus, processedUpTo: '2020-01-01T00:00:00.000Z' } as ConnectorSyncStatusModel}
      />
    );

    expect(await screen.findByText('Last change was at 1/1/2020 12:00:00 AM GMT')).toBeVisible();
  });

  test('should not render the `Last change was at` when not provided', () => {
    render(
      <ConnectorSyncStatus syncDirection={DirectionId.SYNC_FROM} syncStatus={syncStatus as ConnectorSyncStatusModel} />
    );

    const element = screen.queryByText((text: string) => text.includes('Last change was at'));
    expect(element).toBeNull();
  });
});
