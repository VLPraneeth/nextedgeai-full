import { Token } from './types';
import { makeFakeToken } from './utils';

export const fakeNodeId = 'ea9a085fa0fd6f4198487bde';

export const testEntityPipelineState = {
  selectedGraphNode: {
    shape: 'logo-only-node',
    label: 'Sync to Last Modified',
    icon: '/assets/icons/logos/googlesheets.svg',
    hideLeftStrip: true,
    description: 'Google Sheets Account',
    typeColor: '#3EC675',
    nodeType: 'ATTRIBUTE_SINK',
    id: fakeNodeId,
    x: 900,
    y: 400,
    metadata: {
      id: fakeNodeId,
      name: 'Sync to Last Modified',
      apiName: 'syncariLastModified',
      label: 'Sync to Last Modified',
      subLabel: 'Google Sheets Account',
      inputPorts: [
        {
          portType: 'INPUT',
          datatype: 'datetime',
          maxConnections: 1,
        },
      ],
      outputPorts: [],
      configuration: {
        alwaysUseDefaultOnEmpty: false,
        attributeDefinition: '5faea95e80fd6f4198487b9e',
        defaultValue: '{{record.values.lastModified}}-{{record.values.syncarirecordid}}-{{record.values.name}}',
        configId: '5faea95e80fd6f4198487b9a_sink',
      },
      nodeType: 'ATTRIBUTE_SINK',
      location: {
        x: '900',
        y: '400',
      },
      displayName: 'Sync to Last Modified',
      description: 'Google Sheets Account',
      nodeId: fakeNodeId,
      nodeName: 'Sync to Last Modified',
    },
    tooltipMessage: 'Google Sheets Account',
  },
};

export const testTokens: Record<string, Record<string, Token[]>> = {
  [fakeNodeId]: {
    Syncari: [
      {
        ...makeFakeToken('{{syncari.test.token}}', 'Test Token'),
        datatype: 'double',
        group: 'Syncari',
      },
    ],
    Synapse: [
      {
        ...makeFakeToken('{{record.values.lastModified}}', 'Last Modified'),
        datatype: 'datetime',
        group: 'Synapse',
      },
      {
        ...makeFakeToken('{{record.values.name}}', 'Name'),
        group: 'Synapse',
      },
      {
        ...makeFakeToken('{{record.values.syncarirecordid}}', 'SyncariRecordId'),
        group: 'Synapse',
        datatype: 'id',
      },
    ],
  },
};
