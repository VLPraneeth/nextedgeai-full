import { each, first, flatten, isObject, set, uniq } from 'lodash';

import AppConstants from 'utils/AppConstants';
import { getNodeConfig } from 'utils/NodeConfigUtil';

import { EditorPage, PipelineEditorState } from './PipelineEditor.types';

export const getDefaultState = (): PipelineEditorState => {
  return {
    // Current Displayed
    edges: null,
    nodes: null,
    groups: null,
    metadata: null,
    draftGraphJson: null,
    newGraphJson: null,
    pipelineActions: [],
    pipelineFunctions: [],
    attributeNodes: null,
    selectedItemId: null,
    testNodeNotFoundVisible: false,
    editable: true,
    entityName: null,
    connectorEntities: [],
    haveUnsavedChanges: false,
    selectedNode: null,
    lastAction: null,

    // Approved json can only be modified from a sideeffect
    approvedGraphJson: null,
    shiftKeyActive: false,
  };
};

export const addImplicitValues = (configs: any, values: any, configId: string) => {
  each(configs, (config) => {
    if (config.implicit) {
      // If mapping is an array, we are expecting one value
      // since its an implicit input
      if (config.mapping?.length > 0) {
        set(values, (first(config.mapping) as any).graphKey, config.value);
      } else if (isObject(config.mapping)) {
        set(values, config.mapping.graphKey, config.value);
      } else {
        values[config.name] = config.value;
      }
    }
  });
  set(values, AppConstants.GRAPH_CONFIG_ID_OBJECT_PATH, configId);
  return values;
};

export const itemIsGroupOrNode = (item: any) => item.isGroup || item.isNode;
export const itemIsNode = (item: any) => item.isNode;
export const itemIsGroup = (item: any) => item.isGroup;

export const edgeIsInvalid = (edges: any[], item: any, evt?: any): boolean => {
  // Edge is missing source or target
  if (!(item.source?.id && item.target?.id)) {
    return true;
  }

  // Edge connects a node to itself
  if (item.source.id === item.target.id) {
    return true;
  }

  // Edge is a duplicate source for a predicate node
  if (
    evt?.action === AppConstants.NODE_ACTION.ADD &&
    item.target?.model?.shape === AppConstants.GRAPH_NODE_SHAPES.PREDICATE_FUNCTION &&
    !evt?.model?.edgeCreatedFromCopyPaste
  ) {
    return true;
  }

  const duplicateEdge = edges.some((iterationEdge: any) => {
    return (
      // Returns true when a match is found for the same source and target
      iterationEdge.id !== item.id &&
      iterationEdge.source.id === item.source?.id &&
      iterationEdge?.target?.id === item.target?.id
    );
  });

  return duplicateEdge;
};

/**
 * This function iterates over the selected items and moves them to the front
 * (along with any associated edges). This makes it so selected nodes and groups
 * always appear on top. Groups have a slightly transparent background to show
 * content behind the groups.
 */
export const putActiveItemsOnTop = (page: EditorPage) => {
  const selectedItems = page.getSelected();

  if (!selectedItems.length) {
    return;
  }

  const selectedGroupIds: string[] = [];
  const selectedNodeIds: string[] = [];

  selectedItems.forEach((item) => {
    if (item.isGroup) {
      selectedGroupIds.push(item.id);
    } else if (item.isNode) {
      if (item.model.parent) {
        selectedGroupIds.push(item.model.parent);
      } else {
        selectedNodeIds.push(item.id);
      }
    }
  });

  const selectedGroupNodes = page
    .getItems()
    .filter((item) => item.isNode && item.model.parent && selectedGroupIds.includes(item.model.parent))
    .map((item) => item.id);

  const sendToFrontItems = uniq(flatten([selectedGroupIds, selectedNodeIds, selectedGroupNodes]));

  if (!sendToFrontItems.length) {
    return;
  }

  sendToFrontItems.forEach((itemId) => {
    const item = page.find(itemId);
    item?.toFront();
  });

  const edges = page.getEdges();
  edges.forEach((edge) => {
    if (sendToFrontItems.includes(edge.source.id) || sendToFrontItems.includes(edge.target.id)) {
      edge.toFront();
    }
  });

  // Redraw one item to cause a rerender of the graph
  const redrawItem = first(sendToFrontItems);
  redrawItem && page.update(redrawItem, { forceRedrawKey: Math.random() });
};

const SUB_NODES = [
  AppConstants.PREDICATE_FUNCTION_NAME,
  AppConstants.CASE_BRANCH_FUNCTION_NAME,
  AppConstants.FOR_EACH_FUNCTION_NAME,
  AppConstants.END_LOOP_FUNCTION_NAME,
];

export const isSubNode = (node: any, configurations: Record<string, any[]>) => {
  const nodeConfig = getNodeConfig(node, configurations);
  return Boolean(SUB_NODES?.includes(nodeConfig?.name));
};

export const hasLeafSubNode = () => {};
