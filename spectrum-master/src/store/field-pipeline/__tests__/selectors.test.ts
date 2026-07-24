//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { RootState } from 'reducers/index';
import { DeepPartial } from 'utils/TypeUtils';

import { getAttributeNodes, getConnectors, getConnectorsMetadata } from '../fixtures';
import { selectAttributeNodesWithMeta } from '../selectors';

describe('Field pipeline selectors', () => {
  test('Select attribute nodes with metadata', () => {
    const state: DeepPartial<RootState> = {
      fieldPipeline: {
        attributeNodes: getAttributeNodes(),
      },
      connector: {
        connectors: getConnectors(),
        connectorsMetadata: getConnectorsMetadata(),
      },
    };

    // @ts-ignore: partial state for test
    expect(selectAttributeNodesWithMeta(state)).toStrictEqual([
      {
        configuration: [],
        connectorId: '606492ef02a60792769634b7',
        backgroundColor: '#F0FBFF',
        connectorName: 'sfdcone',
        entityDefinitionId: '6064930902a60792769634d0',
        iconPath: '/assets/icons/logos/salesforce.svg',
        id: '6064930902a60792769634d0_source',
        isCoreNode: false,
        label: 'Account',
        name: 'Account',
        type: 'source',
      },
      {
        configuration: [],
        connectorId: '606492ef02a60792769634b7',
        backgroundColor: '#F0FBFF',
        connectorName: 'sfdcone',
        entityDefinitionId: '6064930902a60792769634d0',
        iconPath: '/assets/icons/logos/salesforce.svg',
        id: '6064930902a60792769634d0_sink',
        isCoreNode: false,
        label: 'Account',
        name: 'Account',
        type: 'sink',
      },
      {
        configuration: [],
        entityDefinitionId: '6064914202a607927696326a',
        iconPath: '/icons/syncari.png',
        id: '6064914202a6079276963276',
        isCoreNode: true,
        label: 'Account Name',
        name: 'Name',
        type: 'core',
      },
    ]);
  });
});
