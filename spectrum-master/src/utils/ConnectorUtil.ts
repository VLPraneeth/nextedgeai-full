// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { each, filter, find, isEmpty, keyBy, map, sortBy } from 'lodash';

import SycariLogo from 'assets/images/connectors/syncari-logo.svg';
import { FILE_DATA_CONNECTOR_LABEL } from 'pages/imported-files/constants';
import { ConnectorMetadata } from 'reducers/connectorReducer';
import AppConstants from 'utils/AppConstants';
import { tc } from 'utils/i18nUtil';

export const SYNCARI_CENTER_ID = 'syncari-entity';
export const DEFAULT_SYNCARI_CENTER_X = 788;
export const DEFAULT_SYNCARI_CENTER_Y = 343;
export const SYNCARI_CIRCLE_RADIUS = 60;

const DEFAULT_CONNECTOR_X = 678;
const DEFAULT_CONNECTOR_Y = 132;

export const PANEL_ALLOWED_CONNECTOR_STATUS = [AppConstants.CONNECTOR_STATUS.ACTIVE];

/**
 * Transform connectors for the connector context panel
 * @param {Object} connectors list of connectors
 */
export function getConnectorsForPanel(connectors) {
  const panelConnectors = [];
  each(connectors, (connector) => {
    if (PANEL_ALLOWED_CONNECTOR_STATUS.indexOf(connector.status) === -1) {
      return;
    }
    panelConnectors.push({
      ...connector,
      key: connector.key,
      iconAlt: connector.name,
      title: connector.name,
      draggable: false,
      noicon: 'noicon',
      typeColor: AppConstants.FLOW_TYPE_COLOR.CONNECTOR,
    });
  });
  return panelConnectors;
}

export function addConnectorMetaIcon(connectorsMeta) {
  return map(connectorsMeta, (meta) => {
    return {
      ...meta,
      icon: meta.iconUri,
    };
  });
}

export function haveActiveConnectors(connectors) {
  const filteredConnectors = connectors?.filter((connector) => connector.typeName !== FILE_DATA_CONNECTOR_LABEL);
  return getConnectorsForPanel(filteredConnectors).length > 0;
}

export function firstConnectorActivating(connectors) {
  if (getConnectorsForPanel(connectors).length <= 0) {
    const activatingConnectors = filter(connectors, (connector) => {
      return connector.status === AppConstants.CONNECTOR_STATUS.ACTIVATING;
    });
    return activatingConnectors?.length === 1;
  }
}

function connectNodes(node1, node2) {
  return {
    source: node1.id,
    target: node2.id,
    edgeType: AppConstants.EDGE_TYPE.USER_PREFERENCE,
    color: AppConstants.EDGE_COLOR.PIPELINE,
    shape: 'line-arrow',
    id: `${node1.id}-${node2.id}-edge`,
  };
}

export function getConnectorGraph(connectors, connectorMetadata) {
  const nodes = [];
  const edges = [];
  if (isEmpty(getUserConnectors(connectors))) {
    return { nodes, edges };
  }
  if (connectors && connectorMetadata) {
    const metadataMap = keyBy(connectorMetadata, 'configId');

    const showIntro = connectors.length < 3;
    const syncariNode = getDefaultSyncariNode(showIntro);
    each(connectors, (connector) => {
      if (isSyncariConnector(connector)) {
        const syncariNodeUpdated = {
          ...syncariNode,
          icon: connector?.iconUri,
          label: connector?.displayName,
        };
        nodes.push(syncariNodeUpdated);
      } else {
        const customSynapseStatus = connectorIsCustomDraftOrPublished(metadataMap[connector.metadataId]);

        const connectorNode = {
          id: connector.id,
          shape: AppConstants.GRAPH_NODE_SHAPES.CONNECTOR,
          icon: connector.iconUri,
          typeColor: connector.backgroundColor,
          label: connector.name,
          x: DEFAULT_CONNECTOR_X,
          y: DEFAULT_CONNECTOR_Y,
          status: connector.status,
          statusErrorMessage: connector.errorMessage,
          statusErrorDetails: connector.errorDetails,
          customSynapseStatus,
        };
        nodes.push(connectorNode);
        edges.push(connectNodes(syncariNode, connectorNode));
      }
    });
  }
  return { nodes, edges };
}

export function getDefaultSyncariNode(withIntro = false) {
  return {
    id: SYNCARI_CENTER_ID,
    shape: withIntro
      ? AppConstants.GRAPH_NODE_SHAPES.SYNCARI_CIRCLE_WITH_INTRO
      : AppConstants.GRAPH_NODE_SHAPES.SYNCARI_CIRCLE,
    x: DEFAULT_SYNCARI_CENTER_X,
    y: DEFAULT_SYNCARI_CENTER_Y,
    icon: SycariLogo,
    label: tc('syncari'),
  };
}

export function isSyncariConnector(connector) {
  return connector.typeName?.toLowerCase() === 'syncari';
}

export function getUserConnectors(connectors) {
  return filter(connectors, (connector) => !isSyncariConnector(connector));
}

export function findConnectorById(connectorId, connectors) {
  return find(connectors, (conn) => conn.id === connectorId);
}

export function findConnectorMetadataByConnectorId(connectorId, connectors, connectorsMetadata) {
  const connector = findConnectorById(connectorId, connectors);
  if (connector) {
    return find(connectorsMetadata, (meta) => connector.metadataId === meta.id);
  }
}

// Start creating the layer if the number of nodes is more than this size
const LAYER_BREAK_POINT = 12;

/**
 * Split the nodes to multiple layers when the number of nodes is more than the LAYER_BREAK_POINT.
 * Nodes in the subsequent layers are chunk size * 2.
 * @param {ArrayGraphNodes} nodes
 * @returns segmented nodes. First index is the inner layer and followed by the outer layer.
 */
export function segmentNodes(nodes) {
  if (nodes?.length >= LAYER_BREAK_POINT) {
    const segments = [];
    nodes = sortBy(nodes, (node) => node.label?.toLowerCase());
    // Make an even-ish size layers of circles
    let chunkSize = LAYER_BREAK_POINT;
    for (let i = 0; i < nodes.length; i += chunkSize, chunkSize *= 2) {
      segments.push(nodes.slice(i, i + chunkSize));
    }
    return segments;
  } else {
    return [nodes];
  }
}

/**
 * Arrange connector graph nodes.
 * - Syncari will be at the center node.
 * - The center will be determined by the width and height of the editor.
 * - Nodes are sorted alphabetically, clockwise starting at 12:00 and outwards
 * - The following layer will start at the middle of first and second node of the previous layer
 * - LAYER_BREAK_POINT breakpoint for another layer and chunkSize * 2 for the following layer.
 *
 * @param {Array} nodes needs to be arrange. Also includes the syncari node
 * @param {Editor} editor graph editor object
 * @returns {ArrayGraphNodes} nodes with new locations
 */
export function arrangeNodes(nodes, editor) {
  if (!nodes) {
    return nodes;
  }

  let radius = 250;
  let staticStartDeg = 270;
  if (nodes.length === 2) {
    // Special case for single node + syncari circle to start at the bottom to
    // allow the intro text above the syncari circle
    staticStartDeg = 90;
  } else if (nodes.length === 3) {
    // Special case 2 nodes + 1 syncari circle to start from the left
    staticStartDeg = 180;
  }

  let newNodes = [];

  const graph = editor?.getCurrentPage()?.getGraph();
  const centerX = graph ? graph.getWidth() / 2 : DEFAULT_SYNCARI_CENTER_X;
  const centerY = graph ? graph.getHeight() / 2 : DEFAULT_SYNCARI_CENTER_Y;

  const segmentedNodes = segmentNodes(nodes);
  // Keep track of previous deg increment so we can show a aesthetically
  // pleasing starting point for the next layer.
  let prevDegInc = 0;
  segmentedNodes.forEach((nodes, idx) => {
    const syncariCircle = find(nodes, { id: SYNCARI_CENTER_ID });
    // Exclude the Syncari circle node.
    const nodeCount = nodes.length - (syncariCircle ? 1 : 0);
    const nodePoints = [];
    const degInc = 360 / nodeCount;
    // Start at 270 deg like the analog clock
    // Following layer will start a bit to right of the previous layer
    const startDeg = staticStartDeg + (prevDegInc / 2) * idx;
    for (let degrees = startDeg; degrees < startDeg + 360; degrees += degInc) {
      nodePoints.push(getPointInCircle(degrees, radius));
    }

    let nodeIndex = 0;
    const movedNodes = nodes.map((node) => {
      if (node.id === SYNCARI_CENTER_ID) {
        return { ...node, x: centerX, y: centerY };
      }
      if (nodePoints[nodeIndex]) {
        const [x, y] = nodePoints[nodeIndex];
        nodeIndex++;
        return {
          ...node,
          // move to the position of the Syncari middle node
          y: makeCenterPoint(y, centerY),
          x: makeCenterPoint(x, centerX),
        };
      } else {
        return node;
      }
    });
    newNodes = [...newNodes, ...movedNodes];
    // Increase the pie size for each layer
    radius += 250;
    prevDegInc = degInc;
  });
  return newNodes;
}

// Make a center point with respect to the syncari center node.
const makeCenterPoint = (point, centerPoint) => Math.floor(point) + centerPoint + 60 / 2;

/**
 * Get the point in the pie
 * @param {Number} degree angle in the pie
 * @param {Number} radius of the pie
 * @returns {Array} of x and y.
 */
export const getPointInCircle = (degrees, radius) => {
  const radians = degrees * (Math.PI / 180);
  return [Math.round(Math.cos(radians) * radius), Math.round(Math.sin(radians) * radius)];
};

export const connectorIsCustomDraft = (connector?: ConnectorMetadata) => {
  return Boolean(connector?.custom && connector?.draftStatus !== 'APPROVED');
};

export const connectorIsCustomDraftOrPublished = (connector?: ConnectorMetadata) => {
  if ((!connector?.custom && !connector?.httpSource && !connector?.webhook) || !connector?.draftStatus) {
    return null;
  }
  return connector?.draftStatus === 'APPROVED' ? 'PUBLISHED' : 'DRAFT';
};
