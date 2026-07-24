// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ObjectID from 'bson-objectid';
import { cloneDeep, each, endsWith, find, isUndefined, uniqueId } from 'lodash';

import { BASE_NODE_CONSTANTS } from 'components/graph/Base';
import { FONT_FAMILY } from 'components/graph/constants';
import { OVERVIEW_ID } from 'pages/sync-studio/test/Test.util';
import AppConstants from 'utils/AppConstants';
import { STARTING_DEFAULT_X, STARTING_DEFAULT_Y, STARTING_INC_X, STARTING_INC_Y } from 'utils/DefaultConstants';

import { ellipsis, generateUniqueNamesCallback } from './StringUtil';

export function arrangeGraph(graph) {
  const { nodes, edges } = graph;
  let arrangedGraph = {};

  // TODO: Auto arrange the graph here
  arrangedGraph = {
    nodes,
    edges,
  };

  return arrangedGraph;
}

export function extractUserPref(nodes, edges) {
  const prefNodes = {},
    prefEdges = {};
  each(nodes, (node) => {
    const x = node.x || node.location?.x;
    const y = node.y || node.location?.y;
    prefNodes[node.id] = {
      x,
      y,
      id: node.id,
    };
  });
  each(edges, (edge) => {
    prefEdges[edge.id] = {
      sourceAnchor: edge.sourceAnchor,
      targetAnchor: edge.targetAnchor,
      id: edge.id,
    };
  });
  return {
    nodes: prefNodes,
    edges: prefEdges,
  };
}

/**
 * Extract the id, x and y coordinates of the source graph nodes
 */
export const extractNodesFromGraph = (graphItems) => {
  return Object.keys(graphItems)
    .map((key) => graphItems[key])
    .filter((item) => !Boolean(item.edgeType))
    .map((item) => ({
      x: item.x,
      y: item.y,
      id: item.id,
    }));
};

function getDefaultPosition(currentPositions) {
  let count = currentPositions.count || 0;
  let x = STARTING_DEFAULT_X + count * STARTING_INC_X;
  let y = STARTING_DEFAULT_Y + count * STARTING_INC_Y;
  currentPositions.count = count + 1;
  return { x, y };
}

/**
 * Add the provided preferences on top of the nodes and edges to set the x and y
 * position and the anchors
 */
export function applyUserPref(nodes, edges, prefs) {
  const defaultLocations = {};
  each(nodes, (node) => {
    if (prefs?.nodes?.[node.id]) {
      node.x = Number(prefs.nodes[node.id].x);
      node.y = Number(prefs.nodes[node.id].y);
    } else {
      const { x, y } = getDefaultPosition(defaultLocations);
      node.x = x;
      node.y = y;
    }
  });
  each(edges, (edge) => {
    if (prefs?.edges?.[edge.id]) {
      if (isUndefined(prefs.edges[edge.id].sourceAnchor)) {
        edge.sourceAnchor = undefined;
      } else {
        edge.sourceAnchor = Number(prefs.edges[edge.id].sourceAnchor);
      }
      if (isUndefined(prefs.edges[edge.id].targetAnchor)) {
        edge.targetAnchor = undefined;
      } else {
        edge.targetAnchor = Number(prefs.edges[edge.id].targetAnchor);
      }
    }
  });
}

export function updateKeys(items: Record<string, any>[]) {
  const [firstItem, ...restItems] = items;
  if (firstItem) {
    return [{ ...firstItem, key: uniqueId() }, ...restItems];
  }
  return items;
}

export const getNodeShadowStyles = (y: number = 0) => {
  return {
    shadowOffsetX: 0,
    shadowOffsetY: y + 30,
    shadowBlur: 10,
    shadowColor: 'rgba(0,0,0,.25)',
  };
};

export function getNodeShape(nodeType) {
  let shape = AppConstants.NODE_TYPE_SHAPE_MAP[nodeType];
  if (!shape) {
    shape = AppConstants.GRAPH_NODE_SHAPES.BASE;
  }
  return shape;
}

export function generateNewId(id, nodes) {
  let newNodes = cloneDeep(nodes);
  const node = find(newNodes, (node) => {
    return node.id === id;
  });

  if (node) {
    const newId = ObjectID.generate();
    node.id = newId;
    node.key = newId;
    return newNodes;
  }
  return nodes;
}

export function generateNodeIds(nodes, additionalMeta = {}) {
  const newNodes = cloneDeep(nodes);
  each(newNodes, (node) => {
    node.configId = node.id;
    // Note that this id will be used
    // when this node is dropped to the graph
    node.id = ObjectID.generate();
    each(additionalMeta, (val, key) => {
      node[key] = val;
    });
  });
  return newNodes;
}

export function findTestNode(nodeId, nodes) {
  // Always return true for the test overview id
  if (nodeId === OVERVIEW_ID) {
    return true;
  }
  return nodes?.find((node) => node.id === nodeId);
}

// Check if the graph event is an internal update
// and should not tell the user that there are unsaved changes
export const isInternalNodeUpdate = (evt) => {
  if (evt?.updateModel) {
    const updateKeyCount = Object.keys(evt.updateModel)?.length;
    // Change node selection
    if (updateKeyCount === 1 && evt.updateModel.selected) {
      return true;
    } else if (
      updateKeyCount === 2 &&
      Number.isInteger(evt.updateModel.errorCount) &&
      Number.isInteger(evt.updateModel.warningCount)
    ) {
      // Validation tag update
      return true;
    }
  }
  return false;
};

/**
 * Update the name of the new node if a duplicate name is found in the graph
 * @param {GraphEditor} editor graph editor instance
 * @param {GraphNodeModel} newNode new graph node model
 * @returns void
 */
export const updateGraphDuplicateNames = (editor, newNode) => {
  if (!editor) {
    return;
  }
  const page = editor.getCurrentPage();
  const nodes = page
    ?.getGraph()
    ?.getNodes()
    .filter((node) => node.id !== newNode.id);

  if (nodes && newNode.shape !== AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION) {
    const generateUniqueName = generateUniqueNamesCallback(nodes.map((node) => node.model?.label));
    const name = generateUniqueName(newNode.label);

    if (newNode.label !== name) {
      const item = page.find(newNode.id);
      if (item) {
        page.update(item, {
          ...newNode,
          label: name,
          metadata: {
            ...newNode,
            label: name,
          },
        });
      }
    }
  }
};

// re-use canvas object for better performance
const textWidthCanvas = document.createElement('canvas');

export const getTextWidth = (text: string, font: string = FONT_FAMILY): number => {
  const context = textWidthCanvas.getContext('2d');

  if (context) {
    context.font = font;
    const metrics = context.measureText(text);

    return Math.ceil(metrics.width);
  }
};

interface TruncateNodeTextOptions {
  nodeText: string;
  leftOffset?: number;
  rightOffset?: number;
  nodeWidth?: number;
  fontFamily?: string;
}

export const truncateNodeText = ({
  nodeText,
  leftOffset = 0,
  rightOffset = 0,
  nodeWidth = BASE_NODE_CONSTANTS.WIDTH,
  fontFamily = FONT_FAMILY,
}: TruncateNodeTextOptions) => {
  const textWidth = getTextWidth(nodeText, fontFamily);
  const maxWidth = nodeWidth - leftOffset - rightOffset;

  if (textWidth <= maxWidth) {
    return nodeText;
  }

  let charsToTruncate = 1;
  let truncatedText = ellipsis(nodeText, nodeText.length - charsToTruncate);

  while (getTextWidth(truncatedText) > maxWidth) {
    charsToTruncate++;
    truncatedText = ellipsis(nodeText, nodeText.length - charsToTruncate);
  }

  return truncatedText;
};

// Will split the nodeText into two lines to wrap
export const splitNodeText = (options: TruncateNodeTextOptions) => {
  const firstLine = truncateNodeText(options);

  if (endsWith(firstLine, '…')) {
    const firstLineWithoutEllipse = firstLine.slice(0, -1);
    const firstLineCount = firstLineWithoutEllipse.length;
    const secondLine = truncateNodeText({
      ...options,
      nodeText: options.nodeText.slice(firstLineCount),
    });

    return [firstLineWithoutEllipse.trim(), secondLine.trim()];
  }
  return [firstLine, ''];
};
