export const getCurrentGraphFixture = (nodeId: string = '5e83fc29393afd0001f397b4') => ({
  id: nodeId,
  targetId: '5e613d6598a68d0001ab85b7',
  parentId: null,
  scope: 'ENTITY',
  name: 'Account',
  createdBy: null,
  updatedBy: '5e613d6b98a68d0001ab876a',
  createdAt: null,
  updatedAt: '2020-06-09T18:57:59.830+00:00',
  lastSyncedTime: '2020-07-17T18:26:51.162Z',
  syncStatus: 'PAUSED',
  ready: false,
  draftStatus: 'APPROVED',
  readOnly: true,
  readOnlyReason: 'Last sync: 2020-07-17 13:26:51 CDT',
  draft: {
    nodes: [
      {
        id: '5f982a67b840be6c0d71e420',
        name: 'Account',
        apiName: 'account',
        label: 'Account',
        subLabel: 'Syncari',
        inputPorts: [
          {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 2147483647,
          },
        ],
        outputPorts: [
          {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 2147483647,
          },
        ],
        configuration: {
          entityDefinition: '5e613d6598a68d0001ab85b7',
          enableDeduplicate: false,
          dataAuthorityStrategy: 'NONE',
          configId: '5e613d6598a68d0001ab85b5',
        },
        nodeType: 'CORE_ENTITY',
        location: {
          y: '400',
          x: '600',
        },
        key: '3',
      },
      {
        id: '5f982a67b840be6c0d71e421',
        name: 'Sync To Account',
        apiName: '1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG',
        label: 'Sync To Account',
        subLabel: 'Syncari Test Repo',
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
          entityDefinition: '5edfd97ffee0d800011e2591',
          connectorId: '5edfd92cfee0d800011e255d',
          configId: '5edfd92cfee0d800011e255d',
        },
        nodeType: 'ENTITY_SINK',
        location: {
          y: '159',
          x: '1010',
        },
      },
      {
        id: '5f982a67b840be6c0d71e422',
        name: 'Sync From Account',
        apiName: '1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG',
        label: 'Sync From Account',
        subLabel: 'Syncari Test Repo',
        inputPorts: [],
        outputPorts: [
          {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 2147483647,
          },
        ],
        configuration: {
          schedule: '0 30 12 */6 * *',
          entityDefinition: '5edfd97ffee0d800011e2591',
          connectorId: '5edfd92cfee0d800011e255d',
          configId: '5edfd92cfee0d800011e255d',
        },
        nodeType: 'ENTITY_SOURCE',
        location: {
          y: '612',
          x: '285',
        },
      },
      {
        id: '60424c142f6c2b07b4246cdd',
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
          y: '566.9003516998828',
          x: '692.4032825322391',
        },
      },
      {
        id: '609a92e58490afdee2e09140',
        name: 'Send Email',
        apiName: 'sendEmail',
        label: 'Send Email',
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
          configId: '5e613d6598a68d0001ab873c',
          recipients: '',
          subject: '{{record.values.Name}}',
          name: 'sendEmail',
          description: '{{record.values.Id}}--asdfasdfasdf--asdfasdfasdf',
          definition: '5e613d6598a68d0001ab873c',
          body: 'PGgyPmpoZ2poZ2pqZ2hhc2Zhc2Y8L2gyPg==',
        },
        nodeType: 'ACTION',
        location: {
          y: '211.1365767878077',
          x: '712.0633059788979',
        },
      },
      {
        id: '60d21ef830c307253b077d4b',
        name: 'Filter',
        apiName: 'filter',
        label: 'Filter',
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
          predicate: {
            predicates: [
              {
                left: {
                  datatype: 'datetime',
                  picklistGroup: 'Account (syncari)',
                  label: 'Created Date',
                  type: 'variable',
                  value: '5e613d6598a68d0001ab85d7',
                },
                operator: 'lt',
                predicateId: '60d21f3730c307253b077eb9',
                name: 'predicate',
                right: {
                  value: '2020-01-01',
                  type: 'literal',
                },
              },
            ],
            groupPredicateId: '60d21f0330c307253b077ea4',
            operator: 'AND',
          },
          definition: '5e613d6598a68d0001ab872d',
          value: '',
          configId: '5e613d6598a68d0001ab872d',
        },
        nodeType: 'FUNCTION',
        location: {
          y: '302.2907385697538',
          x: '877.9343493552167',
        },
      },
    ],
    edges: [
      {
        id: '0ee49076',
        source: {
          nodeId: '5f982a67b840be6c0d71e422',
          port: {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '1',
        },
        destination: {
          nodeId: '60424c142f6c2b07b4246cdd',
          port: {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '3',
        },
        key: '4',
      },
      {
        id: '042afb74',
        source: {
          nodeId: '60424c142f6c2b07b4246cdd',
          port: {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '0',
        },
        destination: {
          nodeId: '5f982a67b840be6c0d71e420',
          port: {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '2',
        },
      },
      {
        id: '8ea9dc8f',
        source: {
          nodeId: '609a92e58490afdee2e09140',
          port: {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '1',
        },
        destination: {
          nodeId: '5f982a67b840be6c0d71e421',
          port: {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '3',
        },
      },
      {
        id: 'e5df26e0',
        source: {
          nodeId: '5f982a67b840be6c0d71e420',
          port: {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '0',
        },
        destination: {
          nodeId: '60d21ef830c307253b077d4b',
          port: {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '2',
        },
      },
      {
        id: '8bb01664',
        source: {
          nodeId: '60d21ef830c307253b077d4b',
          port: {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '0',
        },
        destination: {
          nodeId: '609a92e58490afdee2e09140',
          port: {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '2',
        },
      },
    ],
    id: '5f982a67b840be6c0d71e425',
    targetId: '5e613d6598a68d0001ab85b7',
    parentId: '5e83fc29393afd0001f397b4',
    scope: 'ENTITY',
    name: 'Account',
    createdBy: null,
    updatedBy: '5e613d6b98a68d0001ab876a',
    createdAt: null,
    updatedAt: '2021-08-09T03:40:19.770+00:00',
    lastSyncedTime: null,
    syncStatus: null,
    ready: false,
    draftStatus: 'NEW',
    readOnly: false,
    readOnlyReason: '',
    draft: null,
    resyncDetail: null,
  },
  resyncDetail: {
    entitiesToResync: {},
    startTime: '1970-01-01T00:00:00Z',
    endTime: '2020-06-09T18:57:59.800Z',
    status: 'SUCCESS',
    errorMsg: null,
    lastResyncTime: '2020-06-09T18:58:27.169Z',
    syncStatus: 'PAUSED',
  },
  nodes: [
    {
      id: '5edfdb67fee0d800011e2717',
      name: 'Account',
      apiName: 'account',
      label: 'Account',
      subLabel: 'Syncari',
      inputPorts: [
        {
          portType: 'INPUT',
          datatype: 'object',
          maxConnections: 2147483647,
        },
      ],
      outputPorts: [
        {
          portType: 'OUTPUT',
          datatype: 'object',
          maxConnections: 2147483647,
        },
      ],
      configuration: {
        entityDefinition: '5e613d6598a68d0001ab85b7',
        enableDeduplicate: false,
        dataAuthorityStrategy: 'LATEST_RECORD',
        configId: '5e613d6598a68d0001ab85b5',
      },
      nodeType: 'CORE_ENTITY',
      location: {},
      key: '5',
    },
    {
      id: '5edfdb67fee0d800011e2718',
      name: 'Sync From Account',
      apiName: '1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG',
      label: 'Sync From Account',
      subLabel: 'Syncari Test Repo',
      inputPorts: [],
      outputPorts: [
        {
          portType: 'OUTPUT',
          datatype: 'object',
          maxConnections: 2147483647,
        },
      ],
      configuration: {
        schedule: '',
        entityDefinition: '5edfd97ffee0d800011e2591',
        connectorId: '5edfd92cfee0d800011e255d',
        configId: '5edfd92cfee0d800011e255d',
      },
      nodeType: 'ENTITY_SOURCE',
      location: {
        x: 285,
        y: 612,
      },
    },
    {
      id: '5edfdb67fee0d800011e2719',
      name: 'Sync To Account',
      apiName: '1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG',
      label: 'Sync To Account',
      subLabel: 'Syncari Test Repo',
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
        entityDefinition: '5edfd97ffee0d800011e2591',
        connectorId: '5edfd92cfee0d800011e255d',
        configId: '5edfd92cfee0d800011e255d',
      },
      nodeType: 'ENTITY_SINK',
      location: {
        x: 825,
        y: 186,
      },
    },
  ],
  edges: [
    {
      id: '5edfdb67fee0d800011e271a',
      source: {
        nodeId: '5edfdb67fee0d800011e2718',
        port: {
          portType: 'OUTPUT',
          datatype: 'object',
          maxConnections: 2147483647,
        },
        anchor: '1',
      },
      destination: {
        nodeId: '5edfdb67fee0d800011e2717',
        port: {
          portType: 'INPUT',
          datatype: 'object',
          maxConnections: 1,
        },
        anchor: '2',
      },
      key: '6',
    },
    {
      id: '5edfdb67fee0d800011e271b',
      source: {
        nodeId: '5edfdb67fee0d800011e2717',
        port: {
          portType: 'OUTPUT',
          datatype: 'object',
          maxConnections: 1,
        },
        anchor: '0',
      },
      destination: {
        nodeId: '5edfdb67fee0d800011e2719',
        port: {
          portType: 'INPUT',
          datatype: 'object',
          maxConnections: 2147483647,
        },
        anchor: '3',
      },
    },
  ],
});
