// @ts-nocheck
import { getDervConnectors, selectCurrentConnector, selectCurrentOauthRedirectUrl } from '../connectorSelectors';

// keyBy :: String -> List xs -> Object
const keyBy = (propName) => (xs) =>
  xs.reduce((obj, item) => {
    obj[item[propName]] = item;
    return obj;
  }, {});

describe('getDervConnectors', () => {
  test('returns [] if no connectors or no metadata exists', () => {
    const state = {
      connector: {
        connectors: [],
        connectorsMetadata: [],
      },
    };

    expect(getDervConnectors(state)).toEqual([]);
  });

  test('returns sorted connectors', () => {
    const state = {
      connector: {
        connectors: [
          {
            connectorId: '5e67c4701feec90208bbd300',
            id: '5e67c4720eec908bbd30001f',
            name: 'Salesone',
            metadataId: '5e0d2254615f5b37df51d37e',
            endpoint: 'https://test.com',
            status: 'ACTIVE',
          },
          {
            connectorId: '5e67c4701feec90208bbd301',
            id: '5e67c4720eec908bbd30001a',
            name: 'jira',
            metadataId: '5e0d2254615f5b37df51d37e',
            endpoint: 'https://test.com',
            status: 'ACTIVE',
          },
        ],
        connectorsMetadata: [
          {
            id: '5e0f5b37df51d2254615d37e',
            name: 'salesforce',
            type: 'Synapse',
            displayName: 'Salesforce',
            description: null,
            category: 'CRM',
            iconUri: '/assets/icons/logos/salesforce2.svg',
            idFieldName: null,
            watermarkFieldName: 'SystemModstamp',
            createdAtFieldName: null,
            updatedAtFieldName: null,
            watermarkCustomizable: false,
            defaultApiLimit: 1000,
          },
        ],
      },
    };

    expect(getDervConnectors(state)[0].name).toStrictEqual('jira');
    expect(getDervConnectors(state)[1].name).toStrictEqual('Salesone');
  });

  test('returns connectors with icon if no metadata exists', () => {
    const iconUri = '/assets/icons/logos/salesforce2.svg';

    const connector = {
      key: 'test_key_72',
      connectorId: '5e67c4701feec90208bbd300',
      id: '5e67c4720eec908bbd30001f',
      name: 'SF',
      metadataId: '5e0d2254615f5b37df51d37e',
      endpoint: 'https://test.com',
      status: 'ACTIVE',
      iconUri,
      errorMessage: null,
      errorDetails: null,
      createdBy: '5e613d6b98a68d0001ab876a',
      updatedBy: '5e613d6698a68d0001ab8742',
      createdAt: '2020-03-10T16:46:41.669+0000',
      updatedAt: '2020-04-01T02:29:07.443+0000',
    };

    const state = {
      connector: {
        connectors: [connector],
        connectorsMetadata: [],
      },
    };

    expect(getDervConnectors(state)).toStrictEqual([{ ...connector, icon: iconUri }]);
  });

  test('returns merged connector + type metadata and icon', () => {
    const createState = () => ({
      connector: {
        connectors: [
          {
            key: 'test_key_103',
            connectorId: '5e67c4701feec90208bbd300',
            id: '5e67c4720eec908bbd30001f',
            name: 'SF',
            metadataId: '5e0d2254615f5b37df51d37e',
            endpoint: 'https://test.com',
            status: 'ACTIVE',
            errorMessage: null,
            errorDetails: null,
            apiConfig: { dailyQuota: 1000 },
            createdBy: '5e613d6b98a68d0001ab876a',
            updatedBy: '5e613d6698a68d0001ab8742',
            createdAt: '2020-03-10T16:46:41.669+0000',
            updatedAt: '2020-04-01T02:29:07.443+0000',
          },
          {
            key: 'test_key_119',
            connectorId: '5e67eec9020c4701f8bbd300',
            id: '5e67c4720eec908bbd3c4701f',
            name: 'SF2',
            metadataId: '5e0f5b37df51d2254615d37e',
            endpoint: 'https://test.com',
            status: 'ACTIVE',
            errorMessage: null,
            errorDetails: null,
            apiConfig: { dailyQuota: 1000 },
            createdBy: '5e613d6b98a68d0001ab876a',
            updatedBy: '5e613d6698a68d0001ab8742',
            createdAt: '2020-03-10T16:46:41.669+0000',
            updatedAt: '2020-04-01T02:29:07.443+0000',
          },
        ],
        connectorsMetadata: [
          {
            id: '5e0f5b37df51d2254615d37e',
            backgroundColor: '#FFFFFF',
            name: 'salesforce',
            implementationClassName: 'com.syncari.connector.service.SalesforceService',
            type: 'Synapse',
            displayName: 'Salesforce2',
            description: null,
            category: 'CRM',
            iconUri: '/assets/icons/logos/salesforce2.svg',
            idFieldName: null,
            watermarkFieldName: 'SystemModstamp',
            createdAtFieldName: null,
            updatedAtFieldName: null,
            watermarkCustomizable: false,
            defaultApiLimit: 1000,
          },
          {
            id: '5e0d2254615f5b37df51d37e',
            backgroundColor: '#FFFFFF',
            name: 'salesforce',
            implementationClassName: 'com.syncari.connector.service.SalesforceService',
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
          },
        ],
      },
    });

    const state = createState();

    // we expect that the connectors have been augmented with
    // { typeDisplayName, typeName, icon, iconAlt } from the metadata
    let expectedResult = createState();
    const metadataById = keyBy('id')(expectedResult.connector.connectorsMetadata);

    expectedResult = expectedResult.connector.connectors.map((connector) => {
      const metadata = metadataById[connector.metadataId];

      return {
        ...connector,
        backgroundColor: '#FFFFFF',
        typeDisplayName: metadata.displayName,
        typeName: metadata.displayName,
        icon: metadata.iconUri,
        iconAlt: metadata.displayName,
      };
    });

    expect(getDervConnectors(state)).toStrictEqual(expectedResult);
  });
});

test('selectCurrentConnector returns current connector', () => {
  const state = {
    connector: {
      connectorId: '5e67c4720eec908bbd30001f',
      connectors: [
        {
          key: 'test_key_204',
          connectorId: '5e67c4701feec90208bbd300',
          id: '5e67c4720eec908bbd30001f',
          name: 'SF',
          metadataId: '5e0d2254615f5b37df51d37e',
          endpoint: 'https://test.com',
          status: 'ACTIVE',
          errorMessage: null,
          errorDetails: null,
          apiConfig: { dailyQuota: 1000 },
          createdBy: '5e613d6b98a68d0001ab876a',
          updatedBy: '5e613d6698a68d0001ab8742',
          createdAt: '2020-03-10T16:46:41.669+0000',
          updatedAt: '2020-04-01T02:29:07.443+0000',
        },
        {
          key: 'test_key_220',
          connectorId: '5e67eec9020c4701f8bbd300',
          id: '5e67c4720eec908bbd3c4701f',
          name: 'SF2',
          metadataId: '5e0f5b37df51d2254615d37e',
          endpoint: 'https://test.com',
          status: 'ACTIVE',
          errorMessage: null,
          errorDetails: null,
          apiConfig: { dailyQuota: 1000 },
          createdBy: '5e613d6b98a68d0001ab876a',
          updatedBy: '5e613d6698a68d0001ab8742',
          createdAt: '2020-03-10T16:46:41.669+0000',
          updatedAt: '2020-04-01T02:29:07.443+0000',
        },
      ],
    },
  };

  expect(selectCurrentConnector(state)).toStrictEqual(state.connector.connectors[0]);
});

test('selectCurrentOauthRedirectUrl returns the oauth redirect url', () => {
  const state = {
    connector: {
      connectorId: '5e67c4720eec908bbd30001f',
      connectors: [
        {
          key: 'test_key_248',
          connectorId: '5e67c4701feec90208bbd300',
          id: '5e67c4720eec908bbd30001f',
          oauthRedirectUrl: 'http://localhost:3000/oauth/authorize?guid=5e98a9f66515a6b66e174c09',
        },
        {
          key: 'test_key_254',
          connectorId: '5e67eec9020c4701f8bbd300',
          id: '5e67c4720eec908bbd3c4701f',
          oauthRedirectUrl: 'http://localhost:3000/oauth/authorize?guid=5e98a9f66515a6b66e174d23',
        },
      ],
    },
  };

  expect(selectCurrentOauthRedirectUrl(state)).toStrictEqual(state.connector.connectors[0].oauthRedirectUrl);
});

test('selectCurrentOauthRedirectUrl returns oauth redirect url from the newly created one', () => {
  const state = {
    connector: {
      oauthRedirectUrl: 'http://localhost:3000/oauth/authorize?guid=5e98a9f66515a6b66e174d23',
      connectors: [
        {
          key: 'test_key_272',
          connectorId: '5e67c4701feec90208bbd300',
          id: '5e67c4720eec908bbd30001f',
          oauthRedirectUrl: 'http://localhost:3000/oauth/authorize?guid=5e98a9f66515a6b66e174c09',
        },
        {
          key: 'test_key_278',
          connectorId: '5e67eec9020c4701f8bbd300',
          id: '5e67c4720eec908bbd3c4701f',
          oauthRedirectUrl: 'http://localhost:3000/oauth/authorize?guid=5e98a9f66515a6b66e174d23',
        },
      ],
    },
  };

  expect(selectCurrentOauthRedirectUrl(state)).toStrictEqual(state.connector.oauthRedirectUrl);
});
