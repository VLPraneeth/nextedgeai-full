import AppConstants from 'utils/AppConstants';

export const ACTIONS = {
  SAVE: 'SAVE',
  VALIDATE: 'VALIDATE',
};

// Nodes that cannot be selected using on create fragment mode
export const UNSELECTABLE_NODES = [
  AppConstants.NODE_TYPE.ENTITY_SINK,
  AppConstants.NODE_TYPE.ENTITY_SOURCE,
  AppConstants.NODE_TYPE.ATTRIBUTE_SINK,
  AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE,
];

// Node types that cannot be deleted from the graph
export const READONLY_NODE_TYPE = [AppConstants.NODE_TYPE.CORE_ENTITY, AppConstants.NODE_TYPE.CORE_ATTRIBUTE];

export const LOOP_SUB_NODE_API_NAMES = ['forEach', 'endLoop', 'after'];

// Node shapes that can have non-unique names/labels
export const NON_UNIQUE_NODE_SHAPES = [
  AppConstants.GRAPH_NODE_SHAPES.PREDICATE_FUNCTION,
  AppConstants.GRAPH_NODE_SHAPES.CASE_BRANCH_FUNCTION,
  AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION,
];
