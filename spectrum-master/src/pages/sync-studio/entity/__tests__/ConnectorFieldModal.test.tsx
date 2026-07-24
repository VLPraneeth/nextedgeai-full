// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import * as EActions from 'store/entity/thunks';
import { render, screen } from 'tests/helpers';

import ConnectorFieldModal from '../ConnectorFieldModal';

describe('Connector field modal', () => {
  const testState = {
    entity: {
      manageConnectorField: {
        syncariEntityId: '5ee96fb2b580a5eab3cbdcf6',
        synapseEntityId: '5ee97714b580a5eac69d898f',
        synapseNodes: [
          {
            id: '5ee9772d74a710cefb5df73c',
            name: 'Sync From Account',
            label: 'Sync From Account',
            subLabel: 'sfdcone',
            inputPorts: [],
            outputPorts: [{ portType: 'OUTPUT', datatype: 'object', maxConnections: 2147483647 }],
            configuration: {
              entityDefinition: '5ee97714b580a5eac69d898f',
              connectorId: '5ee97700b580a5eac69d8978',
              configId: '5ee97700b580a5eac69d8978',
            },
            nodeType: 'ENTITY_SOURCE',
            location: { x: 218, y: 402 },
          },
        ],
        connectors: [],
        graphDraftId: '5ee9771cb580a5eac69dabb9',
        connectorName: 'sfdcone',
        entityName: 'Sync From Account',
      },
      connectorFields: [
        {
          syncariEntityId: '5ee96fb2b580a5eab3cbdcf6',
          synapseEntityId: '5ee97714b580a5eac69d898f',
          synapseFieldId: '5ee97714b580a5eac69d89ab',
          synapseFieldName: 'Account Description',
          dataType: 'string',
          selectedConnectorIds: [],
          graphDraftId: null,
        },
      ],
      connectorFieldsFetching: false,
      entities: [
        {
          id: '5ee96fb2b580a5eab3cbdcf6',
          apiName: 'account',
          displayName: 'Account',
          description: null,
          subLabel: 'DRAFT',
          iconPath: '/assets/icons/draft.svg',
          pipelineStatus: 'DRAFT',
          type: 'standard',
          connectedTo: ['5ee96fb2b580a5eab3cbdcfc'],
          tags: [],
          fields: [
            {
              id: '5ee96fb2b580a5eab3cbdcff',
              apiName: 'AboutUs',
              displayName: 'About Us',
              dataType: 'string',
              status: 'ACTIVE',
              tags: [],
              values: [],
              isMapped: false,
              hasChanges: false,
              multiValueField: false,
              idField: false,
            },
            {
              id: '5ee96fb2b580a5eab3cbdd14',
              apiName: 'Description',
              displayName: 'Account Description',
              dataType: 'textarea',
              status: 'ACTIVE',
              tags: [],
              values: [],
              isMapped: false,
              hasChanges: false,
              multiValueField: false,
              idField: false,
            },
          ],
          location: null,
          status: null,
          createdBy: null,
          updatedBy: null,
          createdAt: null,
          updatedAt: null,
          activeFields: [
            {
              id: '5ee96fb2b580a5eab3cbdcff',
              apiName: 'AboutUs',
              displayName: 'About Us',
              dataType: 'string',
              status: 'ACTIVE',
              tags: [],
              values: [],
              isMapped: false,
              hasChanges: false,
              multiValueField: false,
              idField: false,
            },
            {
              id: '5ee96fb2b580a5eab3cbdd14',
              apiName: 'Description',
              displayName: 'Account Description',
              dataType: 'textarea',
              status: 'ACTIVE',
              tags: [],
              values: [],
              isMapped: false,
              hasChanges: false,
              multiValueField: false,
              idField: false,
            },
          ],
        },
      ],
    },
    connector: {
      connectors: [
        {
          key: '5ee97700b580a5eac69d8978',
          connectorId: '5ee97700b580a5eac69d8978',
          id: '5ee97700b580a5eac69d8978',
          name: 'sfdcone',
          metadataId: '5ee96f46b580a5ea3f593a8e',
          endpoint: 'https://example.com',
          status: 'ACTIVE',
          errorMessage: null,
          errorDetails: null,

          setting: { syncRate: 0, apiQuota: 0, bootstrapWithSyncari: false },
          createdBy: '5ee96f47b580a5ea3f593aae',
          updatedBy: '5ee96fb3b580a5eab3cbde87',
          createdAt: '2020-06-17T01:50:56.167+0000',
          updatedAt: '2020-06-17T01:51:17.374+0000',
          autoSchemaSyncEntities: [],
          oauthRedirectUrl: 'http://example.com/oauth/authorize?guid=7f70761c-c2ad-4583-9792-da4ea3d79907',
        },
      ],
    },
  };
  test('render with values', async () => {
    const getFieldMappingSpy = jest.spyOn(EActions, 'getFieldMapping');
    const getEntitiesSpy = jest.spyOn(EActions, 'getEntities');
    render(<ConnectorFieldModal />, {
      testState,
    });

    expect(getFieldMappingSpy).toHaveBeenCalled();
    expect(getEntitiesSpy).toHaveBeenCalled();
    expect(await screen.findByText('Sync From Account')).toBeInTheDocument();
    expect(await screen.findByText('Name')).toBeInTheDocument();
    expect(await screen.findByText('Create Field In')).toBeInTheDocument();
    expect(await screen.findByText('Reference To')).toBeInTheDocument();
    expect(await screen.findByText('Account Description')).toBeInTheDocument();
    expect(await screen.findByText('Choose synapse')).toBeInTheDocument();
    expect(await screen.findByText('Choose an entity')).toBeInTheDocument();
  });

  test('render column name with field badge and the filters', async () => {
    render(<ConnectorFieldModal />, {
      testState,
    });
    const badge = document.querySelector('.ant-table-tbody .synri-connector-field-badge svg[aria-label="string"]');
    expect(badge).toBeInTheDocument();
    const filter = document.querySelector('.ant-table-thead .ant-table-column-has-filters .anticon-search');
    expect(filter).toBeInTheDocument();
  });
});
