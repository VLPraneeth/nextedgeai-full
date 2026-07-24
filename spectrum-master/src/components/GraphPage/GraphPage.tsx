//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import { first, isFunction, keyBy, map } from 'lodash';
import PropTypes from 'prop-types';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import useEffectOnValueChange from 'hooks/useEffectOnValueChange';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import usePreviousValue from 'hooks/usePreviousValue';
import { useSelectedNodes, useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { itemIsGroupOrNode } from 'pages/sync-studio/pipeline/PipelineEditor.utils';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { functionOverride } from 'utils/FunctionUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { createPage, enableNodeCheck } from './GraphPage.utils';

import './GraphPage.scss';

export const DEFAULT_ZOOM = 1.0;

export enum GRAPH_MODE {
  DEFAULT = 'default',
  UPDATE_ONLY = 'updateOnly',
  READ_ONLY = 'readOnly',
  READ_SELECT_NODE_ONLY = 'readSelectNodeOnly',
  UPDATE_ONLY_CONNECTOR = 'updateOnlyConnector',
  READ_CHECK_NODE_ONLY = 'readCheckNodeOnly',
  DRAG_SELECT = 'dragSelect',
}

/**
 * This returns true when node keys change which happens when the graph is saved
 * and reloaded from the backend, or when a fragment is dropped on the canvas.
 */
const shouldReplaceGraph = (data: any = {}, nextData: any = {}) => {
  const firstNode: any = first(data.nodes);
  const firstEdge: any = first(data.edges);
  const firstNextNode: any = first(nextData.nodes);
  const firstNextEdge: any = first(nextData.edges);

  // Rerender if the new nodes has different keys
  return (
    // Key is a top level property for Synapses/Connectors
    firstNode?.key !== firstNextNode?.key ||
    // the key is moved to the metadata field for nodes in the getNodesForGraph function for Pipelines
    firstNode?.metadata?.key !== firstNextNode?.metadata?.key ||
    firstEdge?.key !== firstNextEdge?.key
  );
};

export const GraphPage = ({
  editor,
  data = {},
  currentZoom = DEFAULT_ZOOM,
  pipelineViewportMatrix = null,
  pageEventHandlers: rawPageEventHandlers = {},
  graphEventHandlers: rawGraphEventHandlers = {},
  graphMode,
}: any) => {
  const [page, setPage] = useState<any>();
  const pageContainer = useRef();

  const previousData = usePreviousValue(data);
  const { selectedGraphNode } = useEnhancedSelector((state) => state.entityPipeline);

  const { selectedNodeIds } = useSelectedNodes();
  const updateSelectedNodeIds = useUpdateSelectedNodeIdsQueryParam();

  const [triggerReselectNodes, setTriggerReselectNodes] = useState(0);

  const pageEventHandlers = useMemo(() => {
    const onClickEdge = (evt: any) => {
      editor.executeCommand(() => {
        evt.item.update();
        page.update(evt.item, {
          zIndex: evt.item.zIndex + 20,
        });
      });
    };

    return {
      ...rawPageEventHandlers,
      afterchange: (evt: any) => {
        // The CHANGE_DATA event is fired when the graph is reloaded (which
        // happens when we save a draft). We need to reselect the previously
        // selected nodes after reloading the graph.
        if (evt.action === AppConstants.NODE_ACTION.CHANGE_DATA) {
          setTriggerReselectNodes(Math.random());
        }

        // We have to force the graph to redraw when a collapsed group is moved
        // around or expanded to get the edges to line up correctly.

        // A node within a group was moved
        if (
          evt.action === AppConstants.NODE_ACTION.UPDATE &&
          evt.item?.isNode &&
          evt.updateModel?.x &&
          evt.item.model.parent
        ) {
          window.setTimeout(() => {
            page.update(evt.item, { forceRedrawKey: Math.random() });
          });
        }

        // A group was expanded
        if (
          evt.action === AppConstants.NODE_ACTION.UPDATE &&
          evt.item?.isGroup &&
          evt.updateModel?.collapsed === false
        ) {
          const redrawItems = page.getNodes().filter((node: any) => node.model.parent === evt.item.id);
          window.setTimeout(() => {
            redrawItems.forEach((redrawItem: any) => {
              page.update(redrawItem, { forceRedrawKey: Math.random() });
            });
          });
        }

        // Skip calling the afterchange handler if the event is the forceRedrawKey
        if (!(evt.updateModel && 'forceRedrawKey' in evt.updateModel)) {
          rawPageEventHandlers.afterchange(evt);
        }
      },
      'edge:click': onClickEdge,
    };
  }, [rawPageEventHandlers, editor, page]);

  const graphEventHandlers = useMemo(() => rawGraphEventHandlers, [rawGraphEventHandlers]);

  // If a collapsed group is passed to page.read() the editor will crash. This
  // function sets all groups collapsed:false and then collapses the groups that
  // should be collapsed after the page.read().
  const readData = useCallback(
    (_page: any) => {
      const collapsedGroupIds: string[] = [];
      const nodeIds = keyBy(data.nodes, 'id');

      const cleanData = {
        ...data,
        // If an edge somehow references a node that does not exist, filter it out
        edges: data.edges?.filter((edge: any) => nodeIds[edge?.source] && nodeIds[edge?.target]),
        groups: data.groups?.map((group: any) => {
          if (group.collapsed) {
            collapsedGroupIds.push(group.id);
          }
          return { ...group, collapsed: false };
        }),
      };

      _page.read(cleanData);

      // Don't collapse groups that have a selected node within them
      const groupIdsToKeepExpanded = _page
        .getNodes()
        .filter((node: any) => selectedNodeIds.includes(node.id))
        .map((node: any) => node.model.parent);

      _page
        .getGroups()
        .filter((group: any) => collapsedGroupIds.includes(group.id) && !groupIdsToKeepExpanded.includes(group.id))
        .forEach((group: any) => {
          _page.update(group, { collapsed: true, changeShouldNotPromptSave: Math.random() });
        });
    },
    [data, selectedNodeIds]
  );

  // Create page
  useMountUnmountEffect(() => {
    if (editor && data) {
      const _page = createPage(pageContainer.current);

      readData(_page);
      editor.add(_page);
      setPage(_page);
    }
  });

  // attach event handlers
  useEffect(() => {
    if (!page) {
      return;
    }

    const graph = page.getGraph();

    page.addListeners(pageEventHandlers);
    graph.addListeners(graphEventHandlers);

    // Add a hook on remove graph item
    let remove: any;
    if (isFunction(pageEventHandlers?.onCustomBeforeDelete)) {
      remove = functionOverride(graph, 'remove', (originalFunc: any, ...params: any) => {
        if (pageEventHandlers.onCustomBeforeDelete.apply(graph, params)) {
          return originalFunc.apply(graph, params);
        }
      });
    }

    return () => {
      page.removeListeners(pageEventHandlers);
      graph.removeListeners(graphEventHandlers);
      if (remove) {
        graph.remove = remove;
      }
    };
  }, [page, pageEventHandlers, graphEventHandlers]);

  // handle zoom changes
  useEffectOnValueChange(
    (previousWatchers) => {
      const previousZoom = previousWatchers?.[0];
      // Don't update the zoom if it hasn't changed
      if (!page || previousZoom === currentZoom) {
        return;
      }

      const graph = page.getGraph();
      graph.zoom(currentZoom);
    },
    [currentZoom, page]
  );

  // handle viewport changes
  useEffect(() => {
    if (!page || !pipelineViewportMatrix) {
      return;
    }

    const graph = page.getGraph();
    graph.setMatrix(pipelineViewportMatrix);
  }, [pipelineViewportMatrix, page]);

  // handle graph mode changes
  useEffect(() => {
    if (!page) {
      return;
    }

    const graph = page.getGraph();
    if (graph.mode !== graphMode) {
      graph.changeMode(graphMode);
    }

    switch (graphMode) {
      case GRAPH_MODE.READ_SELECT_NODE_ONLY:
      case GRAPH_MODE.UPDATE_ONLY:
      case GRAPH_MODE.READ_ONLY:
        graph.disableDelete = true;
        break;
      default:
        graph.disableDelete = false;
    }
  }, [page, graphMode]);

  // Reload the graph based on updated data when needed
  useEffect(() => {
    // We reload the editor data when we create a group to render the group
    // behind the nodes. We then select the group to update our URL
    // selections.
    const groups = previousData?.groups || EMPTY_ARRAY;
    const newGroups = (data?.groups || EMPTY_ARRAY).filter((group: any) => !groups.some((g: any) => g.id === group.id));
    const hasNewGroup = newGroups.length > 0;

    if (page && (hasNewGroup || shouldReplaceGraph(previousData, data))) {
      // place on the next tick
      window.setTimeout(() => {
        readData(page);
        if (hasNewGroup) {
          const newGroupId = newGroups[0].id;
          const item = page.getGroups().filter((group: any) => group.id === newGroupId);
          page.setSelected(item, true);
        }
      });
    }
  }, [page, data, previousData, readData]);

  // Select all nodes based on nodeIds
  useEffectOnValueChange(() => {
    // We should update the selected nodes only if they have changed
    // from their previous value OR if the page has changed from its previous
    // value. This is both to prevent an infinite update loop in the case of
    // the selectedItemIds AND to properly select the items on initial page
    // load if the page comes in as undefined.

    if (page) {
      // Unselect nodes that were selected and are no longer selected...
      const selectedNodes = page.getSelected();
      const currentlySelectedNodeIds = map(selectedNodes, 'id');
      const nodeIdsToUnselect = currentlySelectedNodeIds.filter((nodeId) => !selectedNodeIds.includes(nodeId));

      nodeIdsToUnselect.forEach((nodeId) => {
        const item = page.getGraph().find(nodeId);
        if (item && item.isSelected) {
          page.setSelected(item, false);
          page.setActived(item, false);
        }
      });

      // ...then select the new nodes.
      selectedNodeIds.forEach((nodeId) => {
        const item = page.getGraph().find(nodeId);
        // Temporary code: nodeIsLastSelected is a special case when more than
        // one node is selected and the user navigates to a currently selected
        // node from the validation panel or search results. In that case we
        // need to select the node again so that it fires the event that
        // triggers an update to state/redux where we have the currently
        // selected node. This is a temporary solution until we stop relying on
        // the state and redux to track the currently selected node and just use
        // the query params.

        // ALERT: This actually introduces a new minor bug. If you have two
        // nodes selected and you're holding shift and click on one of the
        // nodes, both will be unselected (instead of just one). This is because
        // we're firing a select event when just one node is not selected and
        // the updateUrlWithSelectedNodes function in PipelineEditor unselects
        // the node. This is a smaller bug so leaving this for now.
        const nodeIsLastSelected = currentlySelectedNodeIds.length > 1 && selectedNodeIds.length === 1;
        item && (!item.isSelected || nodeIsLastSelected) && page.setSelected(item, true);
      });

      // Remove any invalid nodeIds from the query params
      const validNodeIds = page
        .getItems()
        .filter(itemIsGroupOrNode)
        .map(({ id }: any) => id);

      const newSelectedIds = selectedNodeIds.filter((nodeId) => validNodeIds.includes(nodeId));
      if (newSelectedIds.length < selectedNodeIds.length) {
        updateSelectedNodeIds(newSelectedIds);
      }
    }
  }, [!!page, selectedNodeIds, triggerReselectNodes]);

  // reset canvas focus if a new node is selected
  useEffect(() => {
    const page: any = pageContainer.current;
    const canvas = page.getElementsByTagName('canvas')?.[0];

    if (canvas && selectedGraphNode) {
      canvas.focus();
    }
  }, [selectedGraphNode]);

  useEffect(() => {
    enableNodeCheck(graphMode === GRAPH_MODE.READ_CHECK_NODE_ONLY, editor);
  }, [editor, graphMode]);

  return (
    <div className={cx('editor-page')}>
      <div className="canvas-container" data-userflow-tag={UserflowTags.SyncStudio.Canvas} ref={pageContainer as any} />
    </div>
  );
};

GraphPage.propTypes = {
  createPage: PropTypes.func,
  editor: PropTypes.object,
};
