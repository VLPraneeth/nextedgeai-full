import { navigate, useLocation, useMatch } from '@reach/router';
import { message } from 'antd';
import ObjectID from 'bson-objectid';
import { flatten, get, isEqual, omit, values } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useQueryParams from 'hooks/useQueryParams';
import {
  selectEntityPipeline,
  selectFieldPipeline,
  selectPipelineContext,
  selectSelectedGroups,
  selectSelectedNodeIds,
  selectSelectedNodes,
} from 'selectors/pipelineSelectors';
import { EMPTY_ARRAY } from 'store/constants';
import { setSelectedNodeIds } from 'store/pipeline/actions';
import { Edge, EditorGroup, Group, Node } from 'store/pipeline/types';
import { useSelectCurrentInstance } from 'store/user/selector.hooks';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { NODE_STACKING_BOUNDARY } from 'utils/Pipeline.utils';

import { DuplicateNodeModel } from '../node-grouping/confirm-duplicate-modal';
import { createGroupObject } from '../node-grouping/CreateGroupPanel/CreateGroupPanel.utils';
import { CopiedGraphItems } from '../node-grouping/MultipleNodesPanel';
import { Editor } from './PipelineEditor.types';
import { isSubNode, itemIsGroup, itemIsNode } from './PipelineEditor.utils';

const tn = tNamespaced('GroupNodePanel');

export const getNodeIdsFromSearch = (search: string) => {
  const searchParams = new URLSearchParams(search);
  return searchParams.get('nodeIds')?.split(',') || EMPTY_ARRAY;
};

export const useSelectedNodeIdsReduxEffect = () => {
  const location = useLocation();
  const dispatch = useEnhancedDispatch();

  const [nodeIds, setNodeIds] = useState<string[]>(() => {
    return getNodeIdsFromSearch(location.search);
  });

  // update the search params when a nodeId is selected
  useEffect(() => {
    // Check if the selectedNodeIds have changed.
    const newlySelectedNodeIds = getNodeIdsFromSearch(location.search);

    const nodeIdsHaveChanged = !isEqual([...nodeIds].sort(), [...newlySelectedNodeIds].sort());

    if (nodeIdsHaveChanged) {
      setNodeIds(newlySelectedNodeIds);
    }
  }, [dispatch, location.search, nodeIds]);

  useEffect(() => {
    dispatch(setSelectedNodeIds(nodeIds));
  }, [dispatch, nodeIds]);
};

// Get the selected node ids from the query params on initial render. Updates
// the selectedNodeIds when the query params change.
export const useSelectedNodes = () => {
  const selectedNodeIds = useEnhancedSelector(selectSelectedNodeIds);
  const selectedNodes = useEnhancedSelector(selectSelectedNodes);
  const selectedGroups = useEnhancedSelector(selectSelectedGroups);

  return {
    selectedNodes,
    selectedNodeIds,
    selectedGroups,
  };
};

export const useUpdateSelectedNodeIdsQueryParam = () => {
  const location = useLocation();
  const [queryParams, setParams] = useQueryParams();

  return useCallback(
    (nodeIds?: string[] | null, path?: string, focusNodeId?: string) => {
      const newPath = path || location.pathname;

      if (focusNodeId) {
        const params = new URLSearchParams({ focusNodeId }).toString();
        navigate(`${newPath}?${params}`, { replace: true });
      } else if (nodeIds === null && path) {
        navigate(`${path}?${location.search.toString()}`, { replace: true });
      } else if (Boolean(nodeIds?.length)) {
        const params = new URLSearchParams({ nodeIds: nodeIds!.join(',') });

        navigate(`${newPath}?${params.toString()}`, { replace: true });
      } else {
        navigate(newPath, { replace: true });
        setParams(omit(queryParams, 'nodeIds'));
      }
    },
    [location.pathname, location.search, queryParams, setParams]
  );
};

export const useClearSelectedNodes = () => {
  const setSelectedNodes = useUpdateSelectedNodeIdsQueryParam();

  return useCallback(() => {
    setSelectedNodes(EMPTY_ARRAY);
  }, [setSelectedNodes]);
};

export const useCreateGroup = () => {
  const setSelectedNodes = useUpdateSelectedNodeIdsQueryParam();
  const { selectedNodes } = useSelectedNodes();

  return useCallback(
    (editor: Editor, group: Group, selectedNodesOverride?: Node[]) => {
      if (!editor) {
        return;
      }

      editor.executeCommand(AppConstants.NODE_ACTION.ADD, {
        type: AppConstants.GRAPH_ITEM_TYPE.GROUP,
        addModel: group,
      });

      // Timeout is needed to let the nodes finish getting created before trying
      // to add them to the group.
      setTimeout(() => {
        (selectedNodesOverride || selectedNodes).forEach((node) => {
          editor.executeCommand(() => {
            const page = editor.getCurrentPage();
            const item = page.find(node.id);
            if (item) {
              page.update(item, {
                parent: group.id,
                // Store this timestamp as a way to tell our event handler if a
                // group was just barely created with this node as opposed to
                // dragging the node onto a group.
                timeAddedToGroup: Date.now(),
              });
            }
          });
        });

        message.success(tn('group_created'));

        setSelectedNodes([group.id]);
      }, 50);
    },
    [selectedNodes, setSelectedNodes]
  );
};

export const useCopySelectedItems = (editor: any, scope: string) => {
  const currentInstance = useSelectCurrentInstance();
  const instanceId = currentInstance.id;

  return (showToast = true) => {
    const page = editor.getCurrentPage();
    let selectedNodes = page.getSelected().filter(itemIsNode);
    const selectedGroups = page.getSelected().filter(itemIsGroup);
    const edges = page.getEdges();

    const copyNodes: any[] = [];
    const copyEdges: Edge[] = [];
    const copyGroups: any[] = [];

    const allNodes = page.getNodes();

    selectedGroups.forEach((group: any) => {
      const groupNodes = allNodes.filter((node: any) => node.model.parent === group.id);

      const groupNodesNotSelected = groupNodes.filter(
        (node: any) => !selectedNodes.some((selectedNode: any) => selectedNode.id === node.id)
      );
      selectedNodes = selectedNodes.concat(...groupNodesNotSelected);

      copyGroups.push(group.model);
    });

    selectedNodes.forEach((item: any) => {
      const node = item.model;

      if (node.nodeType === AppConstants.NODE_TYPE.FUNCTION || node.nodeType === AppConstants.NODE_TYPE.ACTION) {
        editor.executeCommand(AppConstants.NODE_ACTION.ADD);
        copyNodes.push(node);
      }
    });

    const copiedNodeIds = copyNodes.map((node) => node.id);

    edges
      .map((item: any) => item.model)
      .filter((edge: any) => copiedNodeIds.includes(edge.source) && copiedNodeIds.includes(edge.target))
      .forEach((edge: any) => {
        copyEdges.push(edge);
      });

    const clipboardData: CopiedGraphItems = {
      instanceId,
      scope,
      copyNodes,
      copyEdges,
      copyGroups,
    };

    if (copyNodes.length > 0) {
      localStorage.setItem(AppConstants.COPIED_NODES_CLIPBOARD, JSON.stringify(clipboardData));

      const nodesText = tn('nodes_copied', { count: copyNodes.length });
      const edgesText = tn('edges_copied', { count: copyEdges.length });

      if (showToast) {
        message.success(
          tn('copy_confirmation', {
            nodesText,
            edgesText,
          })
        );
      }
    } else {
      message.info(tn('invalid_selection'));
    }
  };
};

export const usePasteNodes = (editor: any, scope: string) => {
  const currentInstance = useSelectCurrentInstance();
  const instanceId = currentInstance.id;

  const createGroup = useCreateGroup();
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  return () => {
    const storedClipboardData = localStorage.getItem(AppConstants.COPIED_NODES_CLIPBOARD);

    if (!storedClipboardData) {
      message.info(tn('no_clipboard_data_found'));
      return;
    }

    let data: CopiedGraphItems;

    try {
      data = JSON.parse(storedClipboardData);
    } catch (error) {
      message.info(tn('no_clipboard_data_found'));
      return;
    }

    if (data?.instanceId !== instanceId) {
      message.info(tn('invalid_paste_scope_instance'));
      return;
    }
    if (data.scope !== scope) {
      message.info(tn(`invalid_paste_scope_pipeline_${scope}`));
      return;
    }

    // Map of old id to new id
    const newNodeIdMap: Record<string, string> = {};
    const nodeGroupMap: Record<string, string[]> = {};
    const newGroupIds: string[] = [];

    const autoGeneratedNodeNames: string[] = [AppConstants.FOR_EACH_FUNCTION_NAME, AppConstants.END_LOOP_FUNCTION_NAME];

    data.copyNodes
      .filter((node: any) => !autoGeneratedNodeNames.includes(node.metadata?.apiName))
      .forEach((item) => {
        const newId = ObjectID.generate();
        const nodeToPaste = { ...(item as DuplicateNodeModel) };
        newNodeIdMap[item.id] = newId;

        const addModel = {
          ...nodeToPaste,
          x: nodeToPaste.x + NODE_STACKING_BOUNDARY,
          y: nodeToPaste.y + NODE_STACKING_BOUNDARY,
          id: newId,
          configuration: nodeToPaste.configuration || nodeToPaste.metadata?.configuration,
          parent: undefined, // Remove from group
          shouldSkipNotification: true,
        };

        const currentGroup = (nodeToPaste as any).parent;

        // Only add nodeIds to nodeGroupMap if the parent group has been copied
        if (currentGroup && data.copyGroups.some((group) => group.id === currentGroup)) {
          if (nodeGroupMap[currentGroup]) {
            nodeGroupMap[currentGroup].push(newId);
          } else {
            nodeGroupMap[currentGroup] = [newId];
          }
        }

        editor.executeCommand(AppConstants.NODE_ACTION.ADD, {
          type: AppConstants.GRAPH_ITEM_TYPE.NODE,
          addModel,
        });
      });

    data.copyEdges.forEach((edge) => {
      if (newNodeIdMap[edge.source] && newNodeIdMap[edge.target]) {
        const addNewEdgeModel = {
          id: ObjectID.generate(),
          source: newNodeIdMap[edge.source],
          sourceAnchor: edge.sourceAnchor,
          target: newNodeIdMap[edge.target],
          targetAnchor: edge.targetAnchor,
          edgeCreatedFromCopyPaste: true,
        };

        editor.executeCommand(AppConstants.NODE_ACTION.ADD, {
          type: AppConstants.GRAPH_ITEM_TYPE.EDGE,
          addModel: addNewEdgeModel,
        });
      }
    });

    data.copyGroups.forEach((group) => {
      const nodeIdsInGroup = nodeGroupMap[group.id];

      if (!nodeIdsInGroup) {
        // We should always have nodes in the group but just a safety check.
        return;
      }
      const groupNodes = editor
        .getCurrentPage()
        .getNodes()
        .filter((node: any) => nodeIdsInGroup.includes(node.id));

      const groups = editor.getCurrentPage().getGroups();

      const newGroup = createGroupObject({
        formData: group,
        group: omit(group, 'id') as any,
        groups: groups.map((group: EditorGroup) => group.model),
      });
      newGroupIds.push(newGroup.id);

      createGroup(editor, newGroup, groupNodes);
    });

    setTimeout(() => {
      const newNodeIds = values(newNodeIdMap);
      const nodesInGroup = flatten(values(nodeGroupMap));
      const selectNodeIds = newNodeIds.filter((nodeId) => !nodesInGroup.includes(nodeId));
      updateSelectedNodeIdsQueryParam([...selectNodeIds, ...newGroupIds]);
    }, 200);
  };
};

export const useMultipleNodeCapability = (editor: any) => {
  const page = editor?.getCurrentPage();

  const fieldMatch = useMatch('/sync-studio/entity/:entityId/field/:fieldId/pipeline/*');

  const entityFunctions = useEnhancedSelector((state) => state.pipelineFunction.entityPipelineFunctions);
  const fieldFunctions = useEnhancedSelector((state) => state.pipelineFunction.fieldPipelineFunctions);

  const functions = useMemo(() => {
    if (fieldMatch?.fieldId) {
      return fieldFunctions;
    }
    return entityFunctions;
  }, [entityFunctions, fieldFunctions, fieldMatch?.fieldId]);

  const selectedNodes = page?.getSelected();
  const edges = page?.getEdges();

  let canClone = true;
  let cloneDisableTooltip = '';
  let canCopy = true;
  let copyDisableTooltip = '';
  if (page && selectedNodes?.length && edges?.length) {
    selectedNodes?.forEach((node: any) => {
      if (isSubNode(node?.model, { functions }) && isDanglingSubNode(node.model, selectedNodes, edges)) {
        canClone = false;
        canCopy = false;
        copyDisableTooltip = tn('action_dangling_not_allowed', { action: tc('copy') });
        cloneDisableTooltip = tn('action_dangling_not_allowed', { action: tc('clone') });
      }
    });
  }

  return {
    canCopy,
    copyDisableTooltip,
    canClone,
    cloneDisableTooltip,
  };
};

const isDanglingSubNode = (node: any, selectedNodes: any[], edges: any) => {
  let hasSource = false;
  let hasTarget = false;

  const selectedNodeIds = selectedNodes?.map((node) => node.id);

  edges?.forEach((edge: any) => {
    if (edge.target?.id === node.id && selectedNodeIds.includes(edge.source.id)) {
      hasTarget = true;
    } else if (edge.source?.id === node.id && selectedNodeIds.includes(edge.target.id)) {
      hasSource = true;
    }
  });

  return !(hasTarget && hasSource);
};

export const useNodeMetadataDisabled = () => {
  const pipelineContext = useEnhancedSelector(selectPipelineContext);
  const fieldPipeline = useEnhancedSelector(selectFieldPipeline);
  const entityPipeline = useEnhancedSelector(selectEntityPipeline);

  const isEntityPipeline = useMemo(() => {
    return Boolean(pipelineContext === 'entity');
  }, [pipelineContext]);

  const getPipeline = useCallback(() => {
    return isEntityPipeline ? entityPipeline : fieldPipeline;
  }, [entityPipeline, fieldPipeline, isEntityPipeline]);

  const getNodeDisabledMetadata = useCallback(
    (nodeMetadata: any) => {
      let disabled, disabledMessage;
      const pipeline = getPipeline();
      if (pipeline && nodeMetadata?.disabledCriteria) {
        const nodeTypes = nodeMetadata?.disabledCriteria?.map((crit: any) => {
          return crit.nodeType;
        });
        const qualifiedNodes = pipeline.nodes.filter((node: any) => {
          return nodeTypes?.includes(node.nodeType);
        });
        console.log(qualifiedNodes);
        nodeMetadata?.disabledCriteria.find((crit: any) => {
          return qualifiedNodes.find((node: any) => {
            const nodeValue = get(node, crit.nodeKey);
            if (nodeValue === crit.nodeValue) {
              disabled = true;
              disabledMessage = crit.disabledMessage;
              return true;
            }
            return false;
          });
        });
      }

      return { disabled, disabledMessage };
    },
    [getPipeline]
  );

  return { getNodeDisabledMetadata };
};
