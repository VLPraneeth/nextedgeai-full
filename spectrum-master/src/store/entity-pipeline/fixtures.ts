import { merge } from 'lodash';

import { _getDefaultState } from 'reducers/entityPipelineReducer';

import { EntityPipelineState } from './types';

export const getDefaultEntityPipeline = (pipeline?: Partial<EntityPipelineState>): EntityPipelineState =>
  merge(_getDefaultState(), pipeline);

export const getSelectedGraphNode = (nodeId = '60424c142f6c2b07b4246cdd') => ({
  shape: 'function-node',
  label: 'Lookup Syncari Record',
  icon: '/assets/icons/functions/look-up-syncari-record.svg',
  description: '',
  typeColor: '#4FC5C2',
  nodeType: 'FUNCTION',
  id: nodeId,
  x: 692.4032825322391,
  y: 566.9003516998828,
  metadata: {
    id: nodeId,
    name: 'Lookup Syncari Record',
    apiName: 'advancedLookUpSyncariRecord',
    label: 'Lookup Syncari Record',
    subLabel: '',
    inputPorts: [
      {
        portType: 'INPUT',
        datatype: 'object',
        maxConnections: 1,
      },
    ],
    outputPorts: [
      {
        portType: 'OUTPUT',
        datatype: 'object',
        maxConnections: 1,
      },
    ],
    configuration: {
      predicate: '',
      configId: '5f21b9a47df51d2973edca8c',
      count: '',
      description:
        '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.description}}, {{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.syncariid}}',
      definition: '5f21b9a47df51d2973edca8c',
      sortFields: '',
      value: '',
    },
    nodeType: 'FUNCTION',
    location: {
      x: '692.4032825322391',
      y: '566.9003516998828',
    },
    displayName: 'Lookup Syncari Record',
    description: '',
    nodeId,
    nodeName: 'Lookup Syncari Record',
  },
});
