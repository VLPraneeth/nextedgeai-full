// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import AppConstants from 'utils/AppConstants';

import { updateGraph, shouldHideLeftStrip, getNodeIcon } from '../PipelineUtil';

function getTestGraph() {
  return {
    nodes: [
      {
        id: '5ec5b4b114dde5d563487ace',
        name: 'Sync from Created Date',
        label: 'Sync from Created Date',
        subLabel: 'sfdcone Contact',
        inputPorts: [],
        outputPorts: [{ portType: 'OUTPUT', datatype: 'datetime', maxConnections: 1 }],
        configuration: {
          attributeDefinition: '5ec4a123f2dcfd745bc0ef88',
          configId: '5ec4a123f2dcfd745bc0ef74_source',
        },
        nodeType: 'ATTRIBUTE_SOURCE',
        location: { x: 223, y: 401 },
      },
      {
        id: '5ec5a735f2dcfd745bc11c59',
        name: 'Created Date',
        label: 'Created Date',
        subLabel: 'Syncari',
        inputPorts: [{ portType: 'INPUT', datatype: 'datetime', maxConnections: 2147483647 }],
        outputPorts: [{ portType: 'OUTPUT', datatype: 'datetime', maxConnections: 2147483647 }],
        configuration: {
          attributeDefinition: '5ec4a0bdf2dcfd743e5d20ba',
          dataAuthority: { dataAuthorityStrategy: 'LATEST_RECORD' },
          configId: '5ec4a0bdf2dcfd743e5d20ba',
        },
        nodeType: 'CORE_ATTRIBUTE',
        location: {},
      },
      {
        id: '5ec5c2bd9e64aa7843d379d1',
        name: 'Sync to Account ID',
        label: 'Sync to Account ID',
        subLabel: 'sfdcone Contact',
        inputPorts: [{ portType: 'INPUT', datatype: 'reference', maxConnections: 1 }],
        outputPorts: [],
        configuration: {
          attributeDefinition: '5ec4a123f2dcfd745bc0ef78',
          defaultValue: '',
          configId: '5ec4a123f2dcfd745bc0ef74_sink',
        },
        nodeType: 'ATTRIBUTE_SINK',
        location: { x: 1014, y: 391 },
      },
    ],
    edges: [
      {
        id: '5ec5b4bc14dde5d563487b0e',
        source: {
          nodeId: '5ec5b4b114dde5d563487ace',
          port: { portType: 'OUTPUT', datatype: 'datetime', maxConnections: 1 },
          anchor: '1',
        },
        destination: {
          nodeId: '5ec5a735f2dcfd745bc11c59',
          port: { portType: 'INPUT', datatype: 'datetime', maxConnections: 1 },
          anchor: '3',
        },
        key: '8',
      },
      {
        id: '5ec5c2ca9e64aa7843d37a20',
        source: {
          nodeId: '5ec5a735f2dcfd745bc11c59',
          port: { portType: 'OUTPUT', datatype: 'datetime', maxConnections: 1 },
          anchor: '1',
        },
        destination: {
          nodeId: '5ec5c2bd9e64aa7843d379d1',
          port: { portType: 'INPUT', datatype: 'reference', maxConnections: 1 },
          anchor: '3',
        },
      },
    ],
  };
}

test('Edge source port gets updated when the the source node gets updated to string', () => {
  const event = {
    action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
    originModel: { id: '5ec5b4b114dde5d563487ace' },
    updateModel: {
      nodeType: 'ATTRIBUTE_SOURCE',
      configuration: { attributeDefinition: '5ec4a123f2dcfd745bc0ef88', configId: '5ec4a123f2dcfd745bc0ef74_source' },
      inputPorts: [],
      outputPorts: [{ portType: 'OUTPUT', datatype: 'string', maxConnections: 1 }],
      label: 'Sync from Business Fax',
      subLabel: 'sfdcone Contact',
    },
  };
  const { nodes, edges } = getTestGraph();
  const { edges: newEdges } = updateGraph({ nodes, edges, event });

  expect(newEdges[0].source.port.datatype).toBe(event.updateModel.outputPorts[0].datatype);
  expect(newEdges[0].source.port.datatype).toBe('string');
});

test('Edge destination port gets updated when the the source node gets updated to string', () => {
  const event = {
    action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
    originModel: { id: '5ec5c2bd9e64aa7843d379d1' },
    updateModel: {
      nodeType: 'ATTRIBUTE_SINK',
      configuration: {
        attributeDefinition: '5ec4a123f2dcfd745bc0ef78',
        configId: '5ec4a123f2dcfd745bc0ef74_sink',
      },
      inputPorts: [{ portType: 'INPUT', datatype: 'string', maxConnections: 1 }],
      outputPorts: [],
      label: 'Sync to Account ID',
      subLabel: 'sfdcone Contact',
    },
  };
  const { nodes, edges } = getTestGraph();
  const { edges: newEdges } = updateGraph({ nodes, edges, event });

  expect(newEdges[1].destination.port.datatype).toBe(event.updateModel.inputPorts[0].datatype);
  expect(newEdges[1].destination.port.datatype).toBe('string');
});

test('Node label should change', () => {
  const event = {
    action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
    originModel: { id: '5ec5b4b114dde5d563487ace' },
    updateModel: {
      nodeType: 'ATTRIBUTE_SOURCE',
      label: 'Test Label',
    },
  };
  const { nodes, edges } = getTestGraph();
  const { nodes: newNodes } = updateGraph({ nodes, edges, event });

  expect(newNodes[0].label).toBe('Test Label');
});

test('Node type should be updated', () => {
  const event = {
    action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
    originModel: { id: '5ec5b4b114dde5d563487ace' },
    updateModel: {
      nodeType: AppConstants.NODE_TYPE.ATTRIBUTE_SINK,
      label: 'Sync from Business Fax',
    },
  };
  const { nodes, edges } = getTestGraph();
  const { nodes: newNodes } = updateGraph({ nodes, edges, event });

  expect(newNodes[0].nodeType).toBe(AppConstants.NODE_TYPE.ATTRIBUTE_SINK);
});

test('Node configuration should be updated', () => {
  const event = {
    action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
    originModel: { id: '5ec5b4b114dde5d563487ace' },
    updateModel: {
      nodeType: 'ATTRIBUTE_SOURCE',
      configuration: { attributeDefinition: '5ec4a123f2dcfd745bc0ef88', configId: '5ec4a123f2dcfd745bc0ef74_sink' },
      inputPorts: [],
      outputPorts: [{ portType: 'OUTPUT', datatype: 'string', maxConnections: 1 }],
      label: 'Sync from Business Fax',
      subLabel: 'sfdcone Contact',
    },
  };
  const { nodes, edges } = getTestGraph();
  const { nodes: newNodes } = updateGraph({ nodes, edges, event });

  expect(newNodes[0].configuration.configId).toBe('5ec4a123f2dcfd745bc0ef74_sink');
});

test('create node should carry over the name and configuration for unknown node types', () => {
  const { nodes, edges } = getTestGraph();
  const { nodes: newAttributeNodes } = updateGraph({
    nodes,
    edges,
    event: {
      action: AppConstants.NODE_ACTION.ADD,
      item: {
        type: 'node',
      },
      model: {
        id: '60369c2d97197cd0d9257429',
        nodeType: AppConstants.NODE_TYPE.CONNECTOR_ATTRIBUTE,
        type: 'node',
        label: 'Lead',
        configuration: { configId: '60300ffdcbe0454537296010_source' },
      },
    },
  });
  expect(newAttributeNodes[3].name).toBe('Lead');
  expect(newAttributeNodes[3].configuration.configId).toBe('60300ffdcbe0454537296010_source');

  const { nodes: newEntityNodes } = updateGraph({
    nodes: newAttributeNodes,
    edges,
    event: {
      action: AppConstants.NODE_ACTION.ADD,
      item: {
        type: 'node',
      },
      model: {
        id: '60369c2d97197cd0d9257430',
        nodeType: AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
        type: 'node',
        label: 'Lead',
        configuration: { configId: '60300ffdcbe0454537296011_source' },
      },
    },
  });
  expect(newEntityNodes[4].name).toBe('Lead 2');
  expect(newEntityNodes[4].configuration.configId).toBe('60300ffdcbe0454537296011_source');
});

const { NODE_TYPE: nodeType } = AppConstants;
test.each`
  nodeType                        | expectedResult
  ${nodeType.ENTITY_SINK}         | ${true}
  ${nodeType.ENTITY_SOURCE}       | ${true}
  ${nodeType.ATTRIBUTE_SINK}      | ${true}
  ${nodeType.ATTRIBUTE_SOURCE}    | ${true}
  ${nodeType.CONNECTOR_ENTITY}    | ${true}
  ${nodeType.CONNECTOR_ATTRIBUTE} | ${true}
  ${nodeType.FUNCTION}            | ${false}
  ${nodeType.ACTION}              | ${false}
`('shouldHideLeftStrip returns $expectedResult when nodeType is $nodeType', ({ nodeType, expectedResult }) => {
  expect(
    shouldHideLeftStrip({
      nodeType,
    })
  ).toBe(expectedResult);
});

test('icon should be available for temporary node types', () => {
  expect(
    getNodeIcon(
      {
        configuration: {
          configId: '123',
        },
        nodeType: AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
      },
      {
        connectorEntities: [
          {
            id: '123',
            iconPath: 'myentityicon.svg',
          },
        ],
      }
    )
  ).toBe('myentityicon.svg');

  expect(
    getNodeIcon(
      {
        configuration: {
          configId: '123',
        },
        nodeType: AppConstants.NODE_TYPE.CONNECTOR_ATTRIBUTE,
      },
      {
        attributeNodes: [
          {
            id: '123',
            iconPath: 'myattributeicon.svg',
          },
        ],
      }
    )
  ).toBe('myattributeicon.svg');
});
