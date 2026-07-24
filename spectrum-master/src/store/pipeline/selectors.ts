//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { cloneDeep, isEmpty, keyBy } from 'lodash';
import { createSelector } from 'reselect';

import { GROUP_GRAY_ICON } from 'components/icons/Icons';
import { ConfigContext, SkullConfigMetadata } from 'components/skull';
import { RootState } from 'reducers/index';
import { selectCurrentEntityPipeline } from 'selectors/entityPipelineSelectors';
import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { getNodeConfig } from 'utils/NodeConfigUtil';
import { findGroupNodes, getNodeIconPath } from 'utils/Pipeline.utils';
import { getPipelineActions, getPipelineFunctions } from 'utils/PipelineUtil';

import { Group, Node, NodeOrGroup } from './types';

export const selectPipeline = (state: RootState) => state.pipeline;

export const selectConnectors = (state: RootState) => state.connector.connectors;
export const selectConnectorsMetadata = (state: RootState) => state.connector.connectorsMetadata;
export const selectSelectedGraphNode = (state: RootState) => state.entityPipeline.selectedGraphNode;
export const selectConnectorEntities = (state: RootState) => state.entityPipeline.connectorEntities;
export const selectAttributeNodes = (state: RootState) => state.fieldPipeline.attributeNodes;
export const selectPipelineContext = (state: RootState) => state.entityPipeline.pipelineContext;
export const selectEntityPipelineFunctions = (state: RootState) => state.pipelineFunction.entityPipelineFunctions;
export const selectFieldPipelineFunctions = (state: RootState) => state.pipelineFunction.fieldPipelineFunctions;
export const selectEntityPipelineActions = (state: RootState) => state.pipelineAction.entityPipelineActions;
export const selectFieldPipelineActions = (state: RootState) => state.pipelineAction.fieldPipelineActions;
export const selectNodeConfigContext = (state: RootState) => state.entityPipeline.nodeConfigContext;
export const selectNodeConfigName = (state: RootState) => state.entityPipeline.nodeConfigName;
export const selectDynamicConfigValues = (state: RootState) => state.entityPipeline.dynamicConfigValues;
export const selectDynamicConfigStatus = (state: RootState) => state.entityPipeline.dynamicConfigStatus;
export const selectDynamicConfigErrorMessage = (state: RootState) => state.entityPipeline.dynamicConfigErrorMessage;

export const selectDisplayedGraph = (state: RootState) => state.pipeline.displayedGraph;
export const selectCurrentGraph = (state: RootState) => state.pipeline.currentGraph;
export const selectEntityPipeline = (state: RootState) => state.entityPipeline.entityPipeline;
export const selectDeleteEntityDraft = (state: RootState) => ({
  entityId: state.entityPipeline.deleteDraftModalEntityId,
  refreshPipelineOnDelete: state.entityPipeline.deleteDraftModalRefreshOnDelete,
});
export const selectFieldPipeline = (state: RootState) => state.fieldPipeline.fieldPipeline;
export const selectSelectedNodeIds = (state: RootState) => state.pipeline.selectedNodeIds;
export const selectDeleteMutipleNodesModalVisible = (state: RootState) =>
  state.pipeline.deleteMultipleNodesModalVisible;
export const selectCreateGroupPanelVisible = (state: RootState) => state.pipeline.createGroupPanel;
export const selectConfirmUngroupModalVisible = (state: RootState) => state.pipeline.confirmUngroupModal;
export const selectCreateVersionModalVisible = (state: RootState) => state.pipeline.createVersionModal;
export const selectRestoreVersionModalVisible = (state: RootState) => state.pipeline.restoreVersionModal;
export const selectConfirmDuplicateModalVisible = (state: RootState) => state.pipeline.confirmDuplicateModal;

export const selectChanged = (state: RootState) => state.pipeline.changed;
const selectChangedId = (state: RootState) => state.pipeline.changedId;
const selectChangedScope = (state: RootState) => state.pipeline.changedScope;

export const selectEntityPipelineFunctionsAsMap = createSelector([selectEntityPipelineFunctions], (epFunctions) => {
  return keyBy(epFunctions, 'id');
});

export const selectEntityPipelineActionsAsMap = createSelector([selectEntityPipelineActions], (epActions) => {
  return keyBy(epActions, 'id');
});

export const selectFieldPipelineFunctionsAsMap = createSelector([selectFieldPipelineFunctions], (fpFunctions) => {
  return keyBy(fpFunctions, 'id');
});

export const selectFieldPipelineActionsAsMap = createSelector([selectFieldPipelineActions], (fpActions) => {
  return keyBy(fpActions, 'id');
});

export const selectAllPipelineFunctionsAsMap = createSelector(
  [selectEntityPipelineFunctionsAsMap, selectFieldPipelineFunctionsAsMap],
  (epFunctions, fpFunctions) => {
    return { ...epFunctions, ...fpFunctions };
  }
);

export const selectAttributeNodesAsMap = createSelector([selectAttributeNodes], (attributeNodes) => {
  return keyBy(attributeNodes, 'id');
});

export const selectConnectorsAsMap = createSelector([selectConnectors], (connectors) => {
  return keyBy(connectors, 'id');
});

export const selectConnectorsMetadataAsMap = createSelector([selectConnectorsMetadata], (connectorsMetadata) => {
  return keyBy(connectorsMetadata, 'id');
});

export const selectCurrentGraphDraftReady = (state: RootState) => {
  if (state.pipeline.currentGraph?.draft) {
    // Ready status of a "DRAFT" pipeline
    return state.pipeline.currentGraph.draft.ready;
  } else {
    // Ready status of a "NEW" pipeline
    return state.pipeline.currentGraph?.ready;
  }
};

export const selectPipelineChange = createSelector(
  [selectChanged, selectChangedId, selectChangedScope],
  (changed, changedId, changedScope) => ({
    changed,
    changedId,
    changedScope,
  })
);

export const selectPipelineActions = createSelector(
  [selectPipelineContext, selectEntityPipelineActions, selectFieldPipelineActions],
  (pipelineContext, entityPipelineActions, fieldPipelineActions) => {
    return getPipelineActions(pipelineContext, {
      entityPipelineActions,
      fieldPipelineActions,
    });
  }
);

export const selectPipelineFunctions = createSelector(
  [selectPipelineContext, selectEntityPipelineFunctions, selectFieldPipelineFunctions],
  (pipelineContext, entityPipelineFunctions, fieldPipelineFunctions) => {
    return getPipelineFunctions(pipelineContext, {
      entityPipelineFunctions,
      fieldPipelineFunctions,
    });
  }
);

export const selectNodeConfig = createSelector(
  [
    selectSelectedGraphNode,
    selectConnectorEntities,
    selectAttributeNodes,
    selectPipelineActions,
    selectPipelineFunctions,
  ],
  (selectedNode, connectorEntities, attributeNodes, actions, functions) => {
    if (!selectedNode) {
      return;
    }

    return getNodeConfig(selectedNode, {
      connectorEntities,
      attributeNodes,
      actions,
      functions,
    });
  }
);

export const selectConfigRenderer = createSelector(
  [selectNodeConfig, selectNodeConfigContext],
  (nodeConfig, nodeConfigContext) => {
    if (nodeConfigContext === ConfigContext.QUICK_START) {
      return 'quickStartWizard';
    }
    return nodeConfig?.renderer?.renderType;
  }
);

/**
 * Return the metadata for the selected node needed for useSkullConfig
 */
export const selectSkullMetadataForSelectedNode = createSelector(
  [selectNodeConfig, selectSelectedGraphNode, (state) => state.entityPipeline.groupConfiguration],
  (nodeConfig, selectedGraphNode, groupConfiguration): SkullConfigMetadata<any> => {
    const configValue = cloneDeep(selectedGraphNode?.metadata);

    return {
      nodeConfig,
      configTitle: nodeConfig?.renderer?.title,
      configSteps: nodeConfig?.renderer?.steps,
      configInputs: nodeConfig?.configuration,
      configValue,
      groupConfiguration,
    };
  }
);

export const selectDisplayedFieldPipelineGraph = createSelector(
  [selectDisplayedGraph, selectPipelineContext, selectCurrentEntityPipeline, selectFieldPipeline],
  (displayedGraph, context, entityPipeline, fieldPipeline) => {
    const pipeline = context === AppConstants.PIPELINE_CONTEXT.ENTITY ? entityPipeline : fieldPipeline;
    switch (displayedGraph?.toUpperCase()) {
      case AppConstants.GRAPH_STATUS.APPROVED:
        return pipeline;
      case AppConstants.GRAPH_STATUS.DRAFT:
        if (pipeline?.draft) {
          return pipeline.draft;
        }
        return pipeline;
      default:
        return pipeline;
    }
  }
);

export const selectFieldPipelineSinkNodes = createSelector(
  [selectDisplayedFieldPipelineGraph, selectPipelineContext],
  (graph, context) => {
    if (!graph?.nodes) {
      return EMPTY_ARRAY;
    }
    return graph?.nodes?.filter(
      (node: Node) =>
        node.nodeType ===
        (context === AppConstants.PIPELINE_CONTEXT.ENTITY
          ? AppConstants.NODE_TYPE.ENTITY_SINK
          : AppConstants.NODE_TYPE.ATTRIBUTE_SINK)
    );
  }
);

export const selectFieldPipelineSourceNodes = createSelector(
  [selectDisplayedFieldPipelineGraph, selectPipelineContext],
  (graph, context) => {
    if (!graph?.nodes) {
      return EMPTY_ARRAY;
    }

    return graph?.nodes?.filter(
      (node: Node) =>
        node.nodeType ===
        (context === AppConstants.PIPELINE_CONTEXT.ENTITY
          ? AppConstants.NODE_TYPE.ENTITY_SOURCE
          : AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE)
    );
  }
);

export const selectFieldPipelineCoreNode = createSelector(
  [selectDisplayedFieldPipelineGraph, selectPipelineContext],
  (graph, context) => {
    const nodes = graph?.nodes?.filter(
      (node: Node) =>
        node.nodeType ===
        (context === AppConstants.PIPELINE_CONTEXT.ENTITY
          ? AppConstants.NODE_TYPE.CORE_ENTITY
          : AppConstants.NODE_TYPE.CORE_ATTRIBUTE)
    );
    return nodes?.[0] || EMPTY_OBJECT;
  }
);

export const selectCurrentGraphNodes = createSelector(
  [selectCurrentGraph, selectDisplayedGraph],
  (currentGraph, displayedGraph) => {
    if (currentGraph && displayedGraph) {
      const nodes =
        displayedGraph === AppConstants.GRAPH_STATUS.DRAFT && currentGraph.draft
          ? currentGraph.draft.nodes
          : currentGraph.nodes;

      return nodes;
    }

    return EMPTY_ARRAY;
  }
);

export const selectCurrentGraphEdges = createSelector(
  [selectCurrentGraph, selectDisplayedGraph],
  (currentGraph, displayedGraph) => {
    if (currentGraph && displayedGraph) {
      const edges =
        displayedGraph === AppConstants.GRAPH_STATUS.DRAFT && currentGraph.draft
          ? currentGraph.draft.edges
          : currentGraph.edges;

      return edges;
    }

    return EMPTY_ARRAY;
  }
);

export const selectCurrentGraphGroups = createSelector(
  [selectCurrentGraph, selectDisplayedGraph],
  (currentGraph, displayedGraph): Group[] => {
    if (currentGraph && displayedGraph) {
      const groups =
        displayedGraph === AppConstants.GRAPH_STATUS.DRAFT && currentGraph.draft
          ? currentGraph.draft.groups
          : currentGraph.groups;

      return groups || EMPTY_ARRAY;
    }

    return EMPTY_ARRAY;
  }
);

export const selectSelectedNodes = createSelector(
  [selectCurrentGraphNodes, selectSelectedNodeIds],
  (nodes: Node[], nodeIds) => {
    if (nodes) {
      const filteredNodes = nodes.filter((node: Node) => nodeIds.find((id) => node.id === id));

      return filteredNodes;
    }

    return EMPTY_ARRAY;
  }
);

export const selectSelectedGroups = createSelector(
  [selectCurrentGraphGroups, selectSelectedNodeIds],
  (groups: Group[], nodeIds) => {
    if (groups) {
      const filteredNodes = groups.filter((group) => nodeIds.find((id) => group.id === id));

      return filteredNodes;
    }

    return EMPTY_ARRAY;
  }
);

export const selectSelectedGraphItems = createSelector(
  [selectSelectedNodes, selectSelectedGroups],
  (selectedNodes: Node[], selectedGroups: Group[]) => {
    return [...selectedNodes, ...selectedGroups] as NodeOrGroup[];
  }
);

export const selectSelectedGraphItemsWithIcons = createSelector(
  [
    selectCurrentGraphNodes,
    selectSelectedGraphItems,
    selectPipelineContext,
    selectEntityPipelineFunctionsAsMap,
    selectEntityPipelineActionsAsMap,
    selectFieldPipelineFunctionsAsMap,
    selectFieldPipelineActionsAsMap,
    selectAttributeNodesAsMap,
    selectConnectorsAsMap,
    selectConnectorsMetadataAsMap,
  ],
  (
    currentGraphNodes,
    selectedGraphItems,
    context,
    epFunctionsMap,
    epActionsMap,
    fpFunctionsMap,
    fpActionsMap,
    attributeNodesMap,
    connectorsMap,
    connectorsMetadataMap
  ) => {
    if (!isEmpty(selectedGraphItems)) {
      const iconData = {
        epFunctionsMap,
        epActionsMap,
        fpFunctionsMap,
        fpActionsMap,
        attributeNodesMap,
        connectorsMap,
        connectorsMetadataMap,
      };

      let groupNodes: Node[] = EMPTY_ARRAY;

      const graphItemsWithIcons = selectedGraphItems
        // Remove the predicate nodes from being displayed in
        // DeleteMultipleNodesModal
        .filter((graphItem) => {
          if (!graphItem?.configuration?.configId) {
            return true;
          }
          const matchingFunction =
            epFunctionsMap[graphItem.configuration.configId] || fpFunctionsMap[graphItem.configuration.configId];

          return !([AppConstants.PREDICATE_FUNCTION_NAME, AppConstants.CASE_BRANCH_FUNCTION_NAME] as string[]).includes(
            matchingFunction?.name
          );
        })
        .map((graphItem) => {
          if (graphItem.nodeType === AppConstants.NODE_TYPE.CUSTOM_GROUP) {
            // Get icons for group nodes.
            const group: Group = { ...(graphItem as Group) };

            // Lookup and accumulate the nodes in a group for lookup
            groupNodes = [...groupNodes, ...findGroupNodes(currentGraphNodes, group.id)];

            group.iconPath = GROUP_GRAY_ICON;

            return group;
          } else {
            // Get icons from single nodes.
            const node: Node = { ...(graphItem as Node) };
            node.iconAssetPath = getNodeIconPath(node, context, iconData);

            return node;
          }
        });

      const groupNodesWithIcons = groupNodes.map((groupNode) => {
        const node = { ...groupNode };
        node.iconAssetPath = getNodeIconPath(node, context, iconData);

        return node;
      });

      return [...graphItemsWithIcons, ...groupNodesWithIcons];
    }
    return EMPTY_ARRAY;
  }
);
