import { merge } from 'lodash';

import { ConnectorState } from 'reducers/connectorReducer';
import { Entity, EntityFieldDraftStatus, EntityFieldStatus } from 'store/entity/types';
import { ServerMapping } from 'store/fast-mapper/types';
import AppConstants from 'utils/AppConstants';
import { DeepPartial } from 'utils/TypeUtils';
const { SYNCARI_NODE_STATUS } = AppConstants;

export const getConnector = (): DeepPartial<ConnectorState> => {
  return {
    connectors: [
      {
        key: 'test_key_14',
        connectorId: '603d124e5db2a7fe97e4c350',
        id: '603d124e5db2a7fe97e4c350',
        name: 'sfdcone',
        metadataId: '6030084acbe04544de58362f',
        status: 'ACTIVE',
        schemaRefreshStatus: 'NEW',
      },
      {
        key: 'test_key_23',
        connectorId: '603ca4f25db2a7fe97e4b5b0',
        id: '603ca4f25db2a7fe97e4b5b0',
        name: 'syncari',
        metadataId: '6030084acbe04544de58362e',
        endpoint: 'local',
        status: 'ACTIVE',
        errorMessage: null,
        errorDetails: null,
        apiConfig: {
          dailyQuota: 0,
        },
        autoSchemaSyncEntities: [],
        metaConfig: {},
        schemaRefreshStatus: null,
        oauthRedirectUrl: null,
      },
    ],
    connectorsMetadata: [
      {
        id: '605cd50cb561c597121a3803',
        name: 'salesforce',
        type: 'Synapse',
        displayName: 'Salesforce',
        description: null,
        category: 'CRM',
        iconUri: '/assets/icons/logos/salesforce.svg',
        helpUrl: 'https://support.syncari.com/hc/en-us/articles/360052204672-Salesforce-Setup',
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
                required: true,
              },
              {
                name: 'password',
                dataType: 'password',
                label: 'Password',
                helpSummary: null,
                required: true,
              },
              {
                name: 'token',
                dataType: 'password',
                label: 'Token',
                helpSummary: null,
                required: true,
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
            required: true,
          },
          {
            name: 'authType',
            dataType: 'picklist',
            label: 'Authentication',
            helpSummary: null,
            required: true,
          },
        ],
        watermarkFieldOverrides: {},
        disabledMessage: null,
        oauthUri: null,
      },
    ],
  };
};

export const getEntities = (updatedEntities: DeepPartial<Entity>[] = []): DeepPartial<Entity>[] => {
  return merge(
    [
      {
        id: '603ca4f25db2a7fe97e4b5b4',
        apiName: 'account',
        displayName: 'Account',
        dataStoreName: 'account',
        description: null,
        tags: [],
        subLabel: 'DRAFT',
        iconPath: '/assets/icons/draft.svg',
        pipelineStatus: SYNCARI_NODE_STATUS.DRAFT,
        type: 'standard',
        fields: [
          {
            id: '603ca4f25db2a7fe97e4b5c3',
            apiName: 'Name',
            displayName: 'Account Name',
            description: null,
            dataType: 'string',
            status: EntityFieldStatus.ACTIVE,
            type: null,
            tags: [],
            values: [],
            isMapped: false,
            hasChanges: false,
            draftStatus: EntityFieldDraftStatus.APPROVED,
            readOnly: false,
            required: true,
            referenceTargetField: '',
            multiValueField: false,
            unique: false,
            watermarkField: false,
            system: false,
            idField: false,
            reference: false,
          },
          {
            id: '605cd613b561c5977cae2f9d',
            apiName: 'Description',
            displayName: 'Account Description',
            description: null,
            dataType: 'string',
            status: EntityFieldStatus.ACTIVE,
            type: null,
            tags: [],
            values: [],
            isMapped: false,
            hasChanges: false,
            draftStatus: EntityFieldDraftStatus.APPROVED,
            readOnly: false,
            required: true,
            referenceTargetField: '',
            multiValueField: false,
            unique: false,
            watermarkField: false,
            system: false,
            idField: false,
            reference: false,
          },
        ],
      },
    ],
    updatedEntities
  );
};

export const getMappings = (): Required<ServerMapping>[] => {
  return [
    {
      id: '606b776043b005f137cb5753',
      synapseId: '603d124e5db2a7fe97e4c350',
      synapseName: 'sfdcone',
      synapseEntityId: '603ca4f25db2a7fe97e4b5b4',
      synapseEntityApiName: 'Account',
      synapseEntityDisplayName: 'Account',
      synapseFieldId: '605cd613b561c5977cae2f9d',
      synapseFieldApiName: 'Description',
      synapseFieldDisplayName: 'Account Description',
      synapseFieldDatatype: 'textarea',
      syncariFieldId: '605cd557b561c59732d378ff',
      syncariFieldApiName: 'Description',
      syncariFieldDisplayName: 'Account Description',
      syncariFieldDatatype: 'textarea',
      syncariFieldIsMultiValued: false,
      syncariFieldIsRequired: false,
      createNewSyncariField: false,
      edited: false,
      directions: ['SYNC_FROM', 'SYNC_TO'],
    },
    {
      id: '606b776043b005f137cb5752',
      synapseId: '603d124e5db2a7fe97e4c350',
      synapseName: 'sfdcone',
      synapseEntityId: '603ca4f25db2a7fe97e4b5b4',
      synapseEntityApiName: 'Account',
      synapseEntityDisplayName: 'Account',
      synapseFieldId: '603ca4f25db2a7fe97e4b5c3',
      synapseFieldApiName: 'Name',
      synapseFieldDisplayName: 'Account Name',
      synapseFieldDatatype: 'string',
      syncariFieldId: '605cd557b561c59732d378ed',
      syncariFieldApiName: 'Name',
      syncariFieldDisplayName: 'Account Name',
      syncariFieldDatatype: 'string',
      createNewSyncariField: false,
      syncariFieldIsMultiValued: false,
      syncariFieldIsRequired: false,
      edited: false,
      directions: ['SYNC_FROM', 'SYNC_TO'],
    },
  ];
};
