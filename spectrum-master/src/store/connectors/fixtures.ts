import { merge } from 'lodash';

import { Connector, ConnectorMetadata, ConnectorState, _getDefaultState } from 'reducers/connectorReducer';
import AppConstants from 'utils/AppConstants';

export const getDefaultConnectorState = (connectorState?: Partial<ConnectorState>): ConnectorState =>
  merge(_getDefaultState(), connectorState);

export const getEmptyConnector = (connector: Partial<Connector>): Connector => {
  return {
    apiConfig: { dailyQuota: 1 },
    authConfig: {
      accessToken: null,
      clientId: null,
      clientSecret: null,
      consumerKey: null,
      consumerSecret: null,
      endpoint: null,
      expiresIn: null,
      lastRefreshed: null,
      password: null,
      redirectUri: null,
      refreshToken: null,
      token: null,
      tokenId: null,
      tokenSecret: null,
      userName: null,
    },
    autoSchemaSyncEntities: [],
    connectorId: 'string',
    createdAt: 'string',
    createdBy: 'string',
    draft: null,
    endpoint: 'string',
    id: 'string',
    key: 'string',
    schemaRefreshStatus: null,
    metaConfig: {},
    metadataId: 'string',
    name: 'string',
    displayName: 'string',
    iconUri: 'string',
    backgroundColor: 'string',
    setting: { syncRate: 1, apiQuota: 1, bootstrapWithSyncari: false },
    status: AppConstants.CONNECTOR_STATUS.ACTIVE,
    updatedAt: 'string',
    updatedBy: 'string',
    ...connector,
  };
};

export const getMultipleEmptyConnectorMetadata = (
  connectorMetadata: Partial<ConnectorMetadata> = {}
): ConnectorMetadata[] => {
  return [
    {
      id: '5fff7e4cd6737bd42fa5dfce',
      name: 'salesforce',
      configId: '5fff7e4cd6737bd42fa7a4v4f',
      type: 'Synapse',
      displayName: 'Salesforce',
      description: null,
      category: 'CRM',
      iconUri: '/assets/icons/logos/salesforce.svg',
      helpUrl: null,
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
            {
              name: 'userName',
              dataType: 'text',
              label: 'User Name',
              helpSummary: null,
            },
            {
              name: 'password',
              dataType: 'password',
              label: 'Password',
              helpSummary: null,
            },
            {
              name: 'token',
              dataType: 'password',
              label: 'Token',
              helpSummary: null,
            },
          ],
          label: 'User Password Token',
          helpSummary: '',
        },
      ],
      configureFields: [
        {
          name: 'endpoint',
          dataType: 'text',
          label: 'Endpoint URL',
          helpSummary: null,
        },
        {
          name: 'authType',
          dataType: 'picklist',
          label: 'Authentication',
          helpSummary: null,
        },
      ],
      watermarkFieldOverrides: {},
      disabledMessage: null,
      oauthUri: null,
      ...connectorMetadata,
    },
    {
      id: '5fff7e4cd6737bd42fa5dfcf',
      name: 'hubspot',
      type: 'Synapse',
      configId: '5fff7e4cd6737bd42fabtdac7',
      displayName: 'Hubspot',
      description: null,
      category: 'Marketing',
      iconUri: '/assets/icons/logos/hubspot.svg',
      helpUrl: null,
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
      configureFields: [
        {
          name: 'authType',
          dataType: 'picklist',
          label: 'Authentication',
          helpSummary: null,
        },
      ],
      watermarkFieldOverrides: { contact: 'lastmodifieddate' },
      disabledMessage: null,
      oauthUri:
        '/oauth/authorize?redirect_uri={{redirect_uri}}&client_id={{client_id}}&scope=contacts oauth integration-sync',
    },
  ];
};
export const getEmptyConnectorMetadata = (connectorMetadata: Partial<ConnectorMetadata> = {}): ConnectorMetadata =>
  merge(getMultipleEmptyConnectorMetadata()[0], connectorMetadata);
