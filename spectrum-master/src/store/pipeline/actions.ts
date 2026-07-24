//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { createAction } from '@reduxjs/toolkit';

import { GraphModel } from 'store/pipeline/types';

import * as ActionTypes from './types';
import { TooltipCoordinates } from './types';

export const setDisplayedGraph = createAction(
  'pipeline/setDisplayedGraph',
  (displayedGraph: ActionTypes.GraphStatus) => {
    return {
      payload: { displayedGraph },
    };
  }
);

export interface GraphChangedParams {
  changed: boolean | null;
  changedScope?: ActionTypes.Scope | null;
  changedId?: string | null;
}

export const graphChanged = createAction(
  'pipeline/graphChanged',
  ({ changed, changedScope, changedId }: GraphChangedParams) => {
    return {
      payload: {
        changed,
        changedScope,
        changedId,
      },
    };
  }
);

export interface GroupNodeUpdateParams {
  groupId: string;
  action: 'update' | 'ungroup';
  data?: ActionTypes.Group;
}

export const groupNodeUpdateAction = createAction(
  'pipeline/groupNodeUpdate',
  (payload: GroupNodeUpdateParams | null) => {
    return { payload };
  }
);

export interface NodeKebabActionParams {
  nodeId: string;
  action: 'configure' | 'duplicate' | 'delete' | 'remove_from_group';
  node?: ActionTypes.Node;
}

export const nodeKebabAction = createAction('pipeline/nodeKebabAction', (payload: NodeKebabActionParams | null) => {
  return { payload };
});

export const showDeleteMultipleNodesModal = createAction(
  'pipeline/showDeleteMultipleNodesModal',
  (visible: boolean) => {
    return {
      payload: {
        visible,
      },
    };
  }
);

export const showCreateGroupPanel = createAction(
  'pipeline/showCreateGroupPanel',
  (createGroupPanelParams: ActionTypes.CreateGroupPanelParams) => {
    return {
      payload: {
        createGroupPanelParams,
      },
    };
  }
);

export const showConfirmUngroupModal = createAction(
  'pipeline/showConfirmUngroupModal',
  (confirmUngroupModalParams: ActionTypes.ConfirmUngroupModalParams) => {
    return {
      payload: {
        confirmUngroupModalParams,
      },
    };
  }
);

export const showConfirmDuplicateModal = createAction(
  'pipeline/showConfirmDuplicateModal',
  (confirmDuplicateModalParams: ActionTypes.ConfirmDuplicateModalParams) => {
    return {
      payload: {
        confirmDuplicateModalParams,
      },
    };
  }
);

export const showUnsavedConfirmModal = createAction('pipeline/showUnsavedConfirmModal', (visible: boolean) => {
  return {
    payload: {
      visible,
    },
  };
});

export const setPipelineId = createAction('pipeline/setPipelineId', (pipelineId: string) => {
  return {
    payload: {
      pipelineId,
    },
  };
});

export const setCurrentGraph = createAction('pipeline/setCurrentGraph', (graphJson: GraphModel) => {
  return {
    payload: {
      graphJson,
    },
  };
});

export const moveGraphTooltip = createAction('pipeline/moveGraphTooltip', (coordinates: TooltipCoordinates) => {
  return {
    payload: {
      coordinates,
    },
  };
});

export const setSelectedNodeIds = createAction('pipeline/setSelectedNodeIds', (selectedNodeIds: string[]) => {
  return {
    payload: {
      selectedNodeIds,
    },
  };
});

export const setDragSelectMode = createAction('pipeline/setDragSelectMode', (flag: boolean) => {
  return {
    payload: {
      flag,
    },
  };
});
