//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import { omit, throttle } from 'lodash';
import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import G6Editor from 'sg6-editor';

import { EDGE_COLOR, EDGE_SELECTED_COLOR } from 'components/graph/constants';
import { isKebabSection } from 'components/graph/registerNodeKebab';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import useQueryParams from 'hooks/useQueryParams';
import { useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { Connector } from 'reducers/connectorReducer';
import { Edge } from 'store/pipeline/types';

import FlowEditorContext from '../../contexts/FlowEditorContext';
import GraphItemPanel from '../GraphItemPanel';
import GraphMinimap from '../GraphMinimap';
import { DEFAULT_ZOOM, GRAPH_MODE, GraphPage } from '../GraphPage';
import { registerGraphComponents } from './GraphEditor.utils';

import './GraphEditor.scss';

export const zoomOptions = [25, 50, 75, 100, 125, 150, 175, 200];

const throttledFunction = throttle((fn: () => void) => fn(), 50, { leading: false });

export type EditorProps = Record<string, any>;

export const GraphEditor = (props: EditorProps) => {
  const {
    bottomGroup,
    currentZoom: parentCurrentZoom,
    data = {},
    dragEdgeBeforeShowAnchor,
    emptyGraphPage,
    graphMode,
    hasToolbar,
    hoverNodeBeforeShowAnchor,
    minimapSettings,
    onAfterItemSelected: parentAfterItemSelected,
    onAfterItemUnSelected: parentAfterItemUnSelected,
    onCustomBeforeDelete,
    onGraphChange,
    onGraphDoubleClick,
    pipelineViewportMatrix,
    renderGraph,
    saveViewportMatrix: parentSaveViewportMatrix,
    selectNodeEdges,
    setEditor,
    setSelectedNode,
    toolbar,
  } = props;

  const dispatch = useEnhancedDispatch();
  const connectorEntities = useEnhancedSelector((state) => state.entityPipeline.connectorEntities);

  const userPermissions = useEnhancedSelector((state) => state.user.privileges);

  const syncariConnectorEntity = connectorEntities.find(
    (connector: Connector | any) => connector?.coreNode || connector.name.toLocaleLowerCase() === 'syncari'
  );

  const updateSelectedNodeIds = useUpdateSelectedNodeIdsQueryParam();

  const [editorReady, setEditorReady] = useState(false);
  const [selectedModel, setSelectedModel] = useState<Record<string, any>>({});
  const [currentZoom, setCurrentZoom] = useState<number>(pipelineViewportMatrix?.[0] || DEFAULT_ZOOM);

  const minZoom = zoomOptions[0] / 100; // Minimum zoom ratio
  const maxZoom = zoomOptions[zoomOptions.length - 1] / 100; // Maximum zoom ratio

  const editorRef = useRef<any>(null);
  const nodeUnselectionTimeoutIdRef = useRef<any>(null);

  const editor = editorRef.current;

  const changeZoom = (zoom: number) => {
    setCurrentZoom(zoom);
  };

  const [params, setQueryParams] = useQueryParams<{ focusNodeId: string }>();
  const focusNodeId = params.focusNodeId;

  // Select and center the focusNodeId, then remove it from the query params
  useEffect(() => {
    if (editor && focusNodeId) {
      updateSelectedNodeIds([focusNodeId]);
      setQueryParams(omit(params, 'focusNodeId'));
      // Interval here to give the graph time to render before selecting the node
      const intervalId = setInterval(() => {
        try {
          editor.getCurrentPage().focus(focusNodeId);
          clearInterval(intervalId);
        } catch (error) {
          console.error(`Unable to focus the node ${focusNodeId}`);
        }
      }, 30);
    }
  }, [editor, focusNodeId, params, setQueryParams, updateSelectedNodeIds]);

  // Fit the flow to screen
  const fitToScreen = () => {
    const editor = editorRef.current;
    const page = editor.getCurrentPage();
    page.getGraph().setFitView('cc');
  };

  const createEditor = useCallback(async () => {
    const newEditor = new G6Editor();

    await registerGraphComponents({
      dispatch,
      graphMode,
      userPermissions,
      syncariConnectorEntity,
    });

    editorRef.current = newEditor;
    setEditor(editorRef.current);

    setEditorReady(true);
  }, [dispatch, graphMode, setEditor, syncariConnectorEntity, userPermissions]);

  const onAfterItemUnSelected = useCallback(
    (evt: any) => {
      if (parentAfterItemUnSelected) {
        // delay sending the node Unselect event up to parents,
        // node unselect is fired for each node that gets unselected,
        // which means even when we're selecting _another_ node, we'll
        // still get the event that the original node is being unselected.
        // We dont' want to fire an unselect on the parent level if we're
        // switching to anohter node because we only want to set the
        // currently selected node, we don't want to go from
        // selected -> unselected -> selected states with the extra
        // renders and URL flashes
        //
        // this.afterItemSelected will clear this timeout and if
        // we aren't seleting a node, this event will just be
        // delayed by a short interval
        nodeUnselectionTimeoutIdRef.current = setTimeout(() => {
          parentAfterItemUnSelected(evt);
        }, 50);
      }

      if (!selectNodeEdges) {
        return;
      }

      // TODO: it looks like this is just resetting EDGE colors
      // after unselect. This could be put onto the existing GraphPage
      // effect for item selection.
      const { item } = evt;
      const page = editor.getCurrentPage();

      if (item.isNode) {
        const edges = item.getEdges();
        edges.forEach((edge: Edge) => {
          editor.executeCommand(() => {
            page.update(edge, { color: EDGE_COLOR });
          });
        });
      }
    },
    [editor, parentAfterItemUnSelected, selectNodeEdges]
  );

  // This is hacky solution to prevent calling the throtlled afterItemSelected
  // callback after the component has unmounted.
  const isComponentUnmountedRef = useRef(false);
  useEffect(() => {
    return () => {
      isComponentUnmountedRef.current = true;
    };
  }, []);

  const afterItemSelected = useCallback(
    (evt: any) => {
      // clear any node unselect event that we queued up. This will
      // prevent unneccessary rerenders and URL flashes as we navgate
      // from node to node
      clearTimeout(nodeUnselectionTimeoutIdRef.current);
      nodeUnselectionTimeoutIdRef.current = null;

      // Clearing the timout has to be done outside the throttled function.
      throttledFunction(() => {
        if (isComponentUnmountedRef.current) {
          return;
        }

        parentAfterItemSelected?.(evt);

        const { item } = evt;

        const model = item.getModel();

        setSelectedModel(model);
        setSelectedNode?.(model);

        // TODO: it looks like this is just changing edges
        // for the selected item, this can be put onto the existing GraphPage
        // effect for handling item selection along with the above
        if (selectNodeEdges) {
          const page = editor.getCurrentPage();
          if (item.isNode) {
            const edges = item.getEdges();
            edges.forEach((edge: Edge) => {
              editor.executeCommand(() => {
                evt.item.update();
                page.update(edge, {
                  color: EDGE_SELECTED_COLOR,
                });
              });
            });
          }
        }
      });
    },
    [editor, parentAfterItemSelected, selectNodeEdges, setSelectedNode]
  );

  const saveViewportMatrix = throttle((ev) => parentSaveViewportMatrix?.(ev.updateMatrix), 1000, {
    leading: false,
  });

  const graphMouseLeave = (event: any) => {
    if (isKebabSection(event?.shape?._cfg?.attrs?.section)) {
      const page = editor.getCurrentPage();
      if (graphMode === GRAPH_MODE.DRAG_SELECT) {
        page.css({ cursor: 'crosshair' });
      } else {
        page.css({ cursor: 'default' });
      }
    }
  };

  const pageMouseEnter = () => {
    const page = editor.getCurrentPage();
    if (graphMode === GRAPH_MODE.DRAG_SELECT) {
      page.css({ cursor: 'crosshair' });
    } else {
      page.css({ cursor: 'grab' });
    }
  };

  // I don't love this, but we're memoizing this down at the GraphPageLevel for now
  const graphPage = (() => {
    if (editorReady) {
      const pageEventHandlers = {
        afteritemselected: afterItemSelected,
        afteritemunselected: onAfterItemUnSelected,
        afterchange: onGraphChange,
        'dragedge:beforeshowanchor': dragEdgeBeforeShowAnchor,
        'hovernode:beforeshowanchor': hoverNodeBeforeShowAnchor,
        afterzoom: (ev: any) => {
          const zoom = ev?.updateMatrix?.[0];
          if (zoom) {
            setCurrentZoom(zoom);
          }
        },
        'hoveranchor:beforeaddedge': (ev: any) => {
          if (ev.anchor.type === 'input') {
            ev.cancel = true;
          }
        },
        mouseenter: pageMouseEnter,
        onCustomBeforeDelete,
      };

      const graphEventHandlers = {
        dblclick: onGraphDoubleClick,
        afterviewportchange: saveViewportMatrix,
        mouseleave: graphMouseLeave,
      };

      return (
        <Fragment>
          <GraphPage
            editor={editor}
            // @ts-ignore
            data={data}
            currentZoom={currentZoom}
            pipelineViewportMatrix={pipelineViewportMatrix}
            pageEventHandlers={pageEventHandlers}
            graphEventHandlers={graphEventHandlers}
            graphMode={graphMode}
          />
          {emptyGraphPage}
        </Fragment>
      );
    }
  })();

  useMountUnmountEffect(() => {
    if (renderGraph !== false) {
      createEditor();
    }
  });

  useEffect(() => {
    if (!!parentCurrentZoom) {
      setCurrentZoom(parentCurrentZoom);
    }
  }, [parentCurrentZoom]);

  useEffect(() => {
    const page = editor?.getCurrentPage();
    if (page) {
      if (graphMode === GRAPH_MODE.DRAG_SELECT) {
        page.css({ cursor: 'crosshair' });
      } else {
        page.css({ cursor: 'grab' });
      }
    }
  }, [editor, graphMode]);

  if (renderGraph !== false && !editorReady) {
    return null;
  }

  const { contextPanel, className, graphContent } = props;

  return (
    <FlowEditorContext.Provider
      value={{
        editorReady,
        selectedModel,
        currentZoom,
        editor,
      }}>
      <div className={cx('editor-container', className)}>
        {graphContent ? graphContent : graphPage}
        {toolbar}

        {Boolean(contextPanel) && (
          <GraphItemPanel editor={editor} renderGraph={renderGraph}>
            {contextPanel}
          </GraphItemPanel>
        )}
        {renderGraph !== false && !graphContent && (
          <GraphMinimap
            editor={editor}
            currentZoom={currentZoom}
            settings={minimapSettings}
            changeZoom={changeZoom}
            graphMode={graphMode}
            minZoom={minZoom}
            maxZoom={maxZoom}
            fitToScreen={fitToScreen}
            hasToolbar={hasToolbar}
          />
        )}
        <div className="synri-bottom-group-container">{bottomGroup}</div>
      </div>
    </FlowEditorContext.Provider>
  );
};
