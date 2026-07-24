//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Container for the entity pipeline editor
//

// TODO: Refactor items
// 1. validation needs to be refactored

import { navigate } from '@reach/router';
import { Dropdown, Icon, Menu, message, Modal, Spin, Tooltip } from 'antd';
import ObjectID from 'bson-objectid';
import {
  cloneDeep,
  delay,
  each,
  find,
  first,
  isEmpty,
  isNumber,
  isUndefined,
  keyBy,
  map,
  omit,
  some,
  toLower,
  uniq,
  uniqueId,
} from 'lodash';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { connect } from 'react-redux';

import { invalidatePicklistValues } from 'actions/picklistActions';
import PipelineIcon from 'assets/icons/pipeline.svg';
import NoConfigurationIcon from 'assets/images/no-options.svg';
import RefreshIcon from 'assets/images/refresh-icon.svg';
import EmptyGraphContent from 'components/EmptyGraphContent';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { EdgeType } from 'components/graph/useEdgeOptionsMenu';
import { GraphEditor } from 'components/GraphEditor';
import { GRAPH_MODE } from 'components/GraphPage';
import { default as SIcon } from 'components/icons/Icon';
import { getIconFromPath } from 'components/icons/Icons';
import InlineSVG from 'components/icons/InlineSvg';
import PipelineToolbar, { AvailableVersionsModel, GraphStatus, GraphToolbarProps } from 'components/PipelineToolbar';
import { SWITCH_CASE_BUILT_IN_CASE } from 'components/switch-case/SwitchCase.contants';
import { SyncariThunkDispatch, useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useEffectOnValueChange from 'hooks/useEffectOnValueChange';
import useEventListener from 'hooks/useEventListener';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import usePreviousValue from 'hooks/usePreviousValue';
import EntityEditorEntityPanel from 'pages/sync-studio/entity/EntityEditorEntityPanel';
import FragmentModal from 'pages/sync-studio/fragment/FragmentModal';
import NodeConfigModal from 'pages/sync-studio/node-config/Config';
import { usePipelineError } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import TestNodeNotFound from 'pages/sync-studio/test/test-components/TestNodeNotFound';
import TestAddUpdateSimulatedPanel from 'pages/sync-studio/test/test-panels/TestAddUpdateSimulatedPanel';
import TestResultDetails from 'pages/sync-studio/test/test-panels/TestResultDetails';
import TestResultPanel from 'pages/sync-studio/test/test-panels/TestResultPanel';
import TestRunLivePanel from 'pages/sync-studio/test/test-panels/TestRunLivePanel';
import TestRunSimulatedPanel from 'pages/sync-studio/test/test-panels/TestRunSimulatedPanel';
import { RootState } from 'reducers/index';
import { clearNodeForKebabMenu } from 'store/app/actions';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { EMPTY_ARRAY } from 'store/constants';
import { FragmentModel } from 'store/fragment/types';
import { PipelineFunction } from 'store/pipeline-functions';
import {
  groupNodeUpdateAction,
  setDragSelectMode,
  nodeKebabAction as setNodeKebabAction,
  showConfirmDuplicateModal,
} from 'store/pipeline/actions';
import { Edge, EditorGroup, EditorNode, Group } from 'store/pipeline/types';
import { TestPanelView } from 'store/test/types';
import { updateSyncStudioPipelineViewports } from 'store/user/thunks';
import { ValidationMode } from 'store/validation/types';
import {
  getEntity,
  getValidationResultCountsByGroupId,
  getValidationResultCountsByNodeId,
} from 'store/validation/utils';
import AppConstants from 'utils/AppConstants';
import { getNavigateParams, navigateTo } from 'utils/AppUtil';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { getEntityName, isValidEntity } from 'utils/EntityUtil';
import { isCmdOrCtrlPressed } from 'utils/EventHandlerUtil';
import {
  findTestNode,
  generateNewId,
  generateNodeIds,
  getNodeShape,
  isInternalNodeUpdate,
  updateGraphDuplicateNames,
  updateKeys,
} from 'utils/GraphUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { doesNeedConfiguration, getNodeConfig, normalizeConfigId } from 'utils/NodeConfigUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { eventJustBarelyOccured, getChildNodeSummaryForGroup, getUnstackedNodeCoordinates } from 'utils/Pipeline.utils';
import {
  areValidEdges,
  areValidNodes,
  createDraftGraph,
  findOrphanedLoopSubNodes,
  generateGraphEdgeAnchorIds,
  getDefaultGraphVersionFromPipeline,
  getEdgesForGraph,
  getEdgesForPipelineGraph,
  getGraphVersionUrl,
  getNewFragmentNodeLocations,
  getNodesForGraph,
  getNodesForPipelineGraph,
  getPipelineDraftStatus,
  hasDanglingEdge,
  isConnectingLoopStartAndLoopSide,
  isGraphEditable,
  navigateToGraphVersion,
  nodeIsSubnodeOfLoop,
  transformAttributeNodes,
  updateGraph,
} from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { getUrlListItemName, makeUrl, replaceToken } from 'utils/UrlUtil';
import useSetState from 'utils/useSetState';

import CreateVersionModal from '../entity-pipeline/CreateVersionModal';
import { EntityPipelineError } from '../entity-pipeline/entity-pipeline-error';
import {
  mapDispatchToPropsEntityPipeline,
  mapStateToPropsEntityPipeline,
} from '../entity-pipeline/EntityPipelineEditorProps';
import EntityPipelinePanel from '../entity-pipeline/EntityPipelinePanel';
import { usePipelineStateToasts } from '../entity/PipelineDetails';
import { useCurrentSyncStudioRootTab } from '../entity/SyncStudioRootTabs';
import {
  mapDispatchToPropsFieldPipeline,
  mapStateToPropsFieldPipeline,
} from '../field-pipeline/FieldPipelineEditorProps';
import FieldPipelinePanel from '../field-pipeline/FieldPipelinePanel';
import GroupNodePanel from '../GroupNodePanel';
import { ConfirmDuplicateModal } from '../node-grouping/confirm-duplicate-modal';
import { ConfirmUngroupModal } from '../node-grouping/confirm-ungroup-modal';
import { CreateGroupPanel } from '../node-grouping/CreateGroupPanel';
import { DeleteMultipleNodesModal } from '../node-grouping/DeleteMultipleNodesModal';
import { MultipleNodesPanel } from '../node-grouping/MultipleNodesPanel';
import NodePanel from '../NodePanel';
import { PipelineErrorResultPanel } from '../pipeline-error/PipelineErrorResultPanel';
import { ValidationResultsPanel } from '../validation/ValidationResultsPanel';
import { ACTIONS, LOOP_SUB_NODE_API_NAMES, READONLY_NODE_TYPE, UNSELECTABLE_NODES } from './PipelineEditor.constants';
import {
  useCopySelectedItems,
  useNodeMetadataDisabled,
  usePasteNodes,
  useSelectedNodeIdsReduxEffect,
  useSelectedNodes,
  useUpdateSelectedNodeIdsQueryParam,
} from './PipelineEditor.hooks';
import { PipelineEditorProps, PipelineEditorState } from './PipelineEditor.types';
import {
  addImplicitValues,
  edgeIsInvalid,
  getDefaultState,
  isSubNode,
  itemIsGroupOrNode,
  putActiveItemsOnTop,
} from './PipelineEditor.utils';
import PipelineEditorMoreActions from './PipelineEditorMoreActions';
import DisableRealtimePipelineModal from './realtime-pipeline/DisableRealtimePipelineModal';
import { RealtimePipelineContextProvider } from './realtime-pipeline/RealtimePipeline.context';
import RealtimePipelineModal from './realtime-pipeline/RealtimePipelineModal';
import { Settings } from './settings/Settings';
import { PipelineSettings, usePipelineSettings } from './settings/Settings.hooks';

import './PipelineEditor.less';

const tn = tNamespaced('PipelineEditor');

// HACK: This is a temporary hack for dev mode when setState callback gets
// called multiple times (see https://github.com/facebook/react/issues/12856).
// We need to refactor the updateEditorAfterChanges to not be called in the
// setState callback.
const eventsCalled: any[] = [];

const { GRAPH_STATUS, PIPELINE_CONTEXT } = AppConstants;

const PipelineEditor = (props: PipelineEditorProps) => {
  const { isEntityPipeline, getSyncStatuses } = props;
  useSelectedNodeIdsReduxEffect();
  usePipelineStateToasts();

  const { isSettingsEnabled, settings } = usePipelineSettings();

  const connectorIdToMetadataMap = useConnectorIdToMetadataMap();

  useEffect(() => {
    if (isEntityPipeline) {
      getSyncStatuses();
    }
    // The getSyncStatuses prop is not stable and will take some serious work to
    // stabilize the connnect function that provides it. I got it stabilized
    // before but it had some unintended side effects so didn't go forward with
    // it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEntityPipeline]);

  const dispatch = useEnhancedDispatch();

  const { selectedNodes, selectedNodeIds, selectedGroups } = useSelectedNodes();
  const { currentTab } = useCurrentSyncStudioRootTab();

  const selectedDeletableNodes = selectedNodes.filter((node) => !LOOP_SUB_NODE_API_NAMES.includes(node.apiName));
  const confirmDeleteNeeded = selectedDeletableNodes.length > 1 || selectedGroups.length > 0;

  const groupNodeUpdate = useEnhancedSelector((state) => state.pipeline.groupNodeUpdate);
  const nodeKebabAction = useEnhancedSelector((state) => state.pipeline.nodeKebabAction);
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const [state, setState] = useSetState<PipelineEditorState>(() => {
    return {
      ...getDefaultState(),
      haveUnsavedChanges: false,
      selectedNode: null,
      lastAction: null,
    };
  });

  const currentGraphRef = useRef({
    readOnly: false,
    readOnlyMsg: '',
  });

  const initialize = () => {
    props.setDisplayedGraph((props.graphVersion?.toUpperCase() as GraphStatus) || GRAPH_STATUS.NEW);

    props.clearError();
    props.clearPipeline();

    props.getPipeline();
    if (isEmpty(props.connectors)) {
      // Connectors are needed for the Synapse Entities panel and for test results
      props.getConnectors();
    }

    const pipelineId = props.isEntityPipeline ? props.entityId : (props.fieldId as string);
    if (!props.pipelineFunctions?.length) {
      props.getPipelineFunctions(pipelineId);
    }
    if (!props.pipelineActions?.length) {
      props.getPipelineActions(pipelineId);
    }
    if (props.isEntityPipeline) {
      props.getConnectorEntities(props.entityId);
    } else {
      props.getAttributeNodes(props.fieldId as string);
    }

    if (!props.entities) {
      props.getEntities();
    }

    props.setPipelineContext(props.isEntityPipeline ? PIPELINE_CONTEXT.ENTITY : PIPELINE_CONTEXT.FIELD);
    props.setPipelineId(props.entityId);

    props.getUserPreference();

    // TODO: I think this needs to be removed to avoid unselected the selected node on mount
    // props.setSelectedGraphNode();
  };

  const closeAllTest = () => {
    props.showCreateTest(false);
    props.setTestPanelView(TestPanelView.CLOSED);
  };

  const cleanup = () => {
    props.showNodeConfigModal(false);
    props.clearError();
    props.setDisplayedGraph();
    props.clearPipeline();
    props.clearConnectorEntities?.();
    props.clearAttributeNodes?.();
    if (props.nodeCheckMode) {
      props.showCreateFragmentModal(false);
      props.resetFragmentModal();
      props.enableNodeCheck(false);
    }
    props.showFastMapper?.({ visible: false, entityId: props.entityId });

    props.setSelectedGraphNode();
    closeAllTest();
    setState(getDefaultState());
  };

  useMountUnmountEffect(() => {
    initialize();
    return cleanup;
  });

  const editorRef = useRef<any>(null);
  const spaceUpRef = useRef<boolean>(false);
  const mouseDownRef = useRef<boolean>(false);

  const setEditor = (editor: any) => {
    editorRef.current = editor;
  };

  useEffect(() => {
    const page = editorRef.current?.getCurrentPage();
    if (page) {
      putActiveItemsOnTop(page);
    }
  }, [selectedNodeIds]);

  const graphMode: GRAPH_MODE = useMemo(() => {
    if (toLower(props.graphVersion) === toLower(AppConstants.GRAPH_STATUS.APPROVED)) {
      // Published Pipeline
      return GRAPH_MODE.READ_SELECT_NODE_ONLY;
    } else {
      // Draft Pipeline
      if (props.testResultVisible) {
        return GRAPH_MODE.READ_SELECT_NODE_ONLY;
      }

      if (props.nodeCheckMode) {
        return GRAPH_MODE.READ_CHECK_NODE_ONLY;
      }

      if (props.dragSelectMode) {
        return GRAPH_MODE.DRAG_SELECT;
      }

      return GRAPH_MODE.DEFAULT;
    }
  }, [props.dragSelectMode, props.graphVersion, props.nodeCheckMode, props.testResultVisible]);

  const updateGraphNode = (model: any) => {
    const editor = editorRef.current;
    editorRef.current.executeCommand(() => {
      const page = editor.getCurrentPage();
      const selectedItems = page.getSelected();
      selectedItems.forEach((item: any) => {
        const updateModel: Record<string, any> = {};
        // Change the label
        updateModel['label'] = model.label;
        updateModel['description'] = model.description || model.subLabel || '';
        updateModel['metadata'] = model;
        // Change it to different shape
        // updateModel['shape'] = 'custom-entity';
        page.update(item, updateModel);
      });
    });
  };

  // FP only function
  const getNewDraftMetadata = useCallback(() => {
    if (!state?.metadata) {
      return;
    }
    const id = ObjectID.generate();
    const { draftStatus, draft, ...metadataSpread } = state.metadata;
    return {
      ...metadataSpread,
      id,
      parentId: state.metadata.id,
    };
  }, [state.metadata]);

  const isApproveWithDraftGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft !== null;
  }, [props]);

  // Get the draft if its in state. This means that the user
  // might have created a draft and have not saved yet
  const getDraftGraph = useCallback(() => {
    let graph = {};
    if (state.draftGraphJson) {
      graph = {
        nodes: state.draftGraphJson.nodes,
        edges: state.draftGraphJson.edges,
        groups: state.draftGraphJson.groups,
      };
    } else if (isApproveWithDraftGraph()) {
      const { pipeline = {} } = props;
      if (pipeline.draft) {
        graph = {
          nodes: updateKeys(cloneDeep(pipeline.draft.nodes)),
          edges: updateKeys(cloneDeep(pipeline.draft.edges)),
          groups: updateKeys(cloneDeep(pipeline.draft.groups || EMPTY_ARRAY)),
        };
        setState((currentState) => {
          return {
            ...currentState,
            draftGraphJson: pipeline.draft,
          };
        });
      }
    }
    return graph;
  }, [isApproveWithDraftGraph, props, setState, state.draftGraphJson]);

  const isApproveOnlyGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft === null;
  }, [props]);

  // Expand group when deep linking to node within a collapsed group on the
  // current page (via search or test/validation results)
  useEffectOnValueChange(() => {
    const editor = editorRef.current;
    if (editor) {
      const page = editor.getCurrentPage();

      // The page doesn't exist when we create a FP draft but in that case we
      // don't need to handle expanding groups.
      if (page) {
        const groups: EditorGroup[] = page.getGroups();
        const nodes: EditorNode[] = page.getNodes();

        let nodesToReselect: EditorNode[] = [];

        groups
          .filter((group) => group.model.collapsed)
          .filter((group) => {
            nodesToReselect = nodes.filter((node) => node.isSelected && node.model.parent === group.id);
            return nodesToReselect.length > 0;
          })
          .forEach((group) => {
            page.update(group, { collapsed: false, changeShouldNotPromptSave: Math.random() });
          });

        // We have to reselect the node when a group gets expanded in order for
        // the graph to show the anchors on the node.
        nodesToReselect.forEach((node) => {
          page.setSelected(node, true);
        });
      }
    }
  }, [selectedNodeIds]);

  // Get only the approved graph if the graph is approved
  const getApprovedGraph = useCallback(() => {
    let graph: Record<string, any> = {};
    const { pipeline } = props;
    if (pipeline.draftStatus === GRAPH_STATUS.APPROVED) {
      if (state.approvedGraphJson) {
        graph = {
          nodes: state.approvedGraphJson.nodes,
          edges: state.approvedGraphJson.edges,
          groups: state.approvedGraphJson.groups,
        };
      } else {
        graph = {
          nodes: updateKeys(cloneDeep(pipeline.nodes)),
          edges: updateKeys(cloneDeep(pipeline.edges)),
          groups: updateKeys(cloneDeep(pipeline.groups || EMPTY_ARRAY)),
        };

        setState({
          approvedGraphJson: cloneDeep({
            ...state.metadata,
            nodes: graph.nodes,
            edges: graph.edges,
            groups: graph.groups,
          }),
        });
      }
    }
    return graph;
  }, [props, setState, state.approvedGraphJson, state.metadata]);

  const getGraphJsonForSave = useCallback(
    ({
      nodes,
      edges,
      ready: isReady,
      settings,
    }: {
      nodes: null | any[];
      edges: null | any[];
      ready?: boolean;
      settings?: any;
    }) => {
      const { pipeline } = props;
      const ready = isUndefined(isReady) ? pipeline?.draft?.ready || false : isReady;
      let graphJson;
      let draftGraphJson: any;

      nodes = nodes || state.nodes;
      edges = edges || state.edges;

      const editor = editorRef.current;
      const page = editor?.getCurrentPage();
      let groups: Group[] = EMPTY_ARRAY;

      if (page) {
        // Filter out any groups that have no nodes associated with them
        groups = cloneDeep(page.getGroups().map((group: any) => group.model)).filter((group: any) =>
          (nodes as Node[]).some((node: any) => node.groupId === group.id)
        );
      }

      // Remove invalid groupIds from saved nodes
      nodes = (nodes as Node[]).map((node: any) => {
        const { groupId, ...rest } = node;
        if (groupId && !groups.some((group) => group.id === node.groupId)) {
          return { ...rest };
        }

        return node;
      });

      // Save the draft to state first if the current graph is draft
      if (props.displayedGraph === GRAPH_STATUS.DRAFT) {
        const draftOptions = props.isEntityPipeline
          ? state.draftGraphJson
          : {
              ...(state.draftGraphJson || getNewDraftMetadata()),
              ready,
            };

        draftGraphJson = {
          ...draftOptions,
          nodes,
          edges,
          groups,
        };
        // getGraphJsonForSave should be a pure function without the side effect
        // of setting state. Calling setState without a delay can override recent
        // changes to edges and nodes even if we use the callback. The TODO for
        // this is not use the component state at all but just use the
        // currentGraph that's stored in redux.
        delay(() => {
          setState((currentState) => {
            return {
              ...currentState,
              draftGraphJson,
            };
          });
        }, 50);
      } else {
        draftGraphJson = getDraftGraph();
        draftGraphJson = isEmpty(draftGraphJson) ? null : draftGraphJson;
      }

      if (isApproveOnlyGraph() || (isApproveWithDraftGraph() && draftGraphJson)) {
        const { nodes: approvedNodes, edges: approvedEdges } = getApprovedGraph();

        graphJson = {
          ...state.metadata,
          draft: draftGraphJson,
          nodes: approvedNodes,
          edges: approvedEdges,
        };
      } else if (pipeline?.draftStatus === GRAPH_STATUS.NEW) {
        graphJson = {
          ...state.metadata,
          ready,
          nodes,
          edges,
          groups,
        };
      }

      if (settings) {
        if (props.displayedGraph === GRAPH_STATUS.DRAFT) {
          graphJson.draft.settings = {
            ...graphJson.draft.settings,
            ...settings,
          };
        } else {
          graphJson.settings = {
            ...graphJson.settings,
            ...settings,
          };
        }
      }

      return graphJson;
    },
    [
      getApprovedGraph,
      getDraftGraph,
      getNewDraftMetadata,
      isApproveOnlyGraph,
      isApproveWithDraftGraph,
      props,
      setState,
      state.draftGraphJson,
      state.edges,
      state.metadata,
      state.nodes,
    ]
  );

  /**
   * Stores the current state of the editor in pipeline.currentGraph in redux
   * which can be used to lookup configuration data in the NodeConfigPanel or to
   * save the graph from the UnsavedConfirmModal
   */
  const setCurrentGraph = (nodes?: any, edges?: any) => {
    const graphJson = getGraphJsonForSave({ nodes, edges });
    if (graphJson) {
      props.setCurrentGraph(graphJson);
    }
  };

  const validate = useCallback(() => {
    let error = hasDanglingEdge(state.edges);
    if (error) {
      props.showPipelineError(tn('no_dangling'));
    }
    return error;
  }, [props, state.edges]);

  const onCreateVersion = () => {
    props.showCreateVersionModal({ visible: true });
  };

  const onSaveChanges = useCallback(
    async (nodes?: Node[] | null, edges?: Edge[] | null, ready?: boolean, settings?: any) => {
      const error = validate();
      let refreshPipelineOnUpdate = true;
      if (error) {
        return;
      }
      nodes = areValidNodes(nodes) ? nodes : null;
      edges = areValidEdges(edges) ? edges : null;

      const graphJson = getGraphJsonForSave({ nodes, edges, ready, settings } as any);

      // Update the id of the new edges and refresh
      const newEdges = generateGraphEdgeAnchorIds(graphJson);
      if (!isEmpty(newEdges)) {
        refreshPipelineOnUpdate = true;
      }

      if (props.isEntityPipeline) {
        await props.updatePipeline(props.entityId, graphJson, { refreshPipelineOnUpdate });
      } else {
        await props.updatePipeline(props.fieldId as string, graphJson, {
          refreshPipelineOnUpdate,
          entityId: props.entityId,
        });
      }

      setState({
        haveUnsavedChanges: false,
        lastAction: ACTIONS.SAVE,
      });

      // Invalidate our pickst cache since the context / graph have changed
      dispatch(invalidatePicklistValues());

      props.graphChanged({
        changed: null,
        changedScope: null,
        changedId: null,
      });
    },
    [dispatch, getGraphJsonForSave, props, setState, validate]
  );

  // When navigating to a different graph version the default is to replace the
  // current browser history to avoid intermediary paths from being stored in
  // history. replace should be set to false when the user is manually
  // navigating between graph versions.
  const _navigateToGraphVersion = ({
    graphVersion,
    nodeIds,
    replace = true,
  }: {
    graphVersion: string;
    nodeIds?: string[];
    replace?: boolean;
  }) => {
    navigateToGraphVersion({
      ...(props.isFieldPipeline && { fieldId: props.fieldId }),
      entityId: props.entityId,
      graphVersion,
      updateSelectedNodeIdsQueryParam,
      replace,
      nodeIds,
    });
  };

  /**
   * Sets a valid graph status in the url
   */
  const setCorrectDisplayedGraph = (pipeline: any) => {
    // There may be no pipeline if the user is looking at a field pipeline that
    // does not exist.
    if (isEmpty(pipeline)) {
      return;
    }

    const { displayedGraph } = props;

    // displayedGraph is the graph status in the URL
    if (displayedGraph === GRAPH_STATUS.NEW) {
      // Navigate to draft if the user is trying to navigate to new
      // and a draft exists.
      if (pipeline.draft) {
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT, nodeIds: selectedNodeIds });
      } else if (pipeline.draftStatus !== GRAPH_STATUS.NEW) {
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.APPROVED, nodeIds: selectedNodeIds });
      } else if (props.graphVersion?.toUpperCase() !== GRAPH_STATUS.NEW) {
        // If a user directly navigates to a url with no version then default to new
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.NEW, nodeIds: selectedNodeIds });
      }
    } else if (displayedGraph === GRAPH_STATUS.DRAFT) {
      if (pipeline.draftStatus === GRAPH_STATUS.NEW) {
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.NEW, nodeIds: selectedNodeIds });
      } else if (!pipeline.draft) {
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.APPROVED, nodeIds: selectedNodeIds });
      }
    } else if (displayedGraph === GRAPH_STATUS.APPROVED) {
      if (pipeline.draftStatus === GRAPH_STATUS.NEW) {
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.NEW, nodeIds: selectedNodeIds });
      }
    }
  };

  const isDraftOnlyGraph = () => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.NEW && pipeline?.draft === null;
  };

  // FP only function
  const navigateToEntityPipeline = () => {
    const url = replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
      entityId: props.entityId,
      graphVersion: props.graphVersion,
    });
    navigateTo(url, getNavigateParams({ ...props }));
  };

  const setNodeCheckValue = (nodeId: string, value: any) => {
    const editor = editorRef.current;
    editor.executeCommand(() => {
      const page = editor.getCurrentPage();
      const item = page.find(nodeId);
      if (item) {
        page.update(item, {
          checkedNode: value,
        });

        if (item.model?.metadata?.configuration?.loopStart) {
          // Update the checkedNode status for all loop subnodes
          const loopSubnodes = page.getNodes().filter(nodeIsSubnodeOfLoop(item.id, page.getEdges()));

          loopSubnodes.forEach((subNode: { id: string }) => {
            const item = page.find(subNode.id);
            if (item) {
              dispatch(props.setNodeCheck(subNode.id, value));

              page.update(item, {
                checkedNode: value,
              });
            }
          });
        }
      }
    });
  };

  const componentDidUpdateFP = (prevProps: PipelineEditorProps) => {
    const {
      savedNodeConfig,
      pipelineDiscarding,
      pipelineDeleting,
      nodeCheckId,
      nodeCheckValue,
      selectedTestNodeId,
    } = props;

    if (selectedTestNodeId !== prevProps.selectedTestNodeId) {
      setState({
        selectedItemId: selectedTestNodeId,
        testNodeNotFoundVisible: !findTestNode(selectedTestNodeId, state.nodes),
      });
    }

    if (savedNodeConfig !== prevProps.savedNodeConfig) {
      // save node here for our delayed update 👇
      const _selectedNode = state.selectedNode;

      // Queue the manual update of the selected node
      delay(() => {
        // Cancel update if it no longer exists
        if (!_selectedNode) {
          return;
        }
        updateGraphNode(savedNodeConfig);
        const { nodes, edges } = updateGraph({
          nodes: state.nodes,
          edges: state.edges,
          groups: state.groups,
          event: {
            action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
            originModel: {
              id: _selectedNode.id,
            },
            updateModel: savedNodeConfig,
          },
        });

        setSelectedNode({
          ..._selectedNode,
          metadata: {
            ..._selectedNode.metadata,
            configuration: {
              ...(_selectedNode?.metadata?.configuration || {}),
              ...(savedNodeConfig?.configuration || {}),
            },
          },
          label: savedNodeConfig?.label,
        });

        setCurrentGraph(nodes, edges);
        onSaveChanges(nodes, edges);

        setState({
          nodes,
          edges,
          haveUnsavedChanges: false,
          lastAction: ACTIONS.SAVE,
        });
      }, 10);
    }

    // If draft only is discarded, navigate back to parent entity
    if (prevProps.pipelineDiscarding === true && pipelineDiscarding === false) {
      if (isDraftOnlyGraph()) {
        navigateToEntityPipeline();
      }
    }

    // Done deleting the field pipeline, redirect to the entity pipeline
    if (prevProps.pipelineDeleting === true && pipelineDeleting === false) {
      navigateToEntityPipeline();
    }

    // Node check have change
    if (nodeCheckId !== prevProps.nodeCheckId || nodeCheckValue !== prevProps.nodeCheckValue) {
      // check/uncheck the node that changed
      setNodeCheckValue(nodeCheckId, nodeCheckValue);
    }

    return true;
  };

  const updateUrlWithSelectedNodes = (event?: any) => {
    const page = editorRef.current.getCurrentPage();

    let selectedNodes = page.getSelected().filter(itemIsGroupOrNode);

    // If we are drag selecting, then don't select nodes that are in a collapsed group
    if (props.dragSelectMode) {
      selectedNodes = selectedNodes.filter((item: any) => typeof item.collapsedParent !== 'object');
    }

    // If the user is holding shift and clicks on a selected node, we should
    // filter it out of the selected nodes so it's no longer selected.
    const newSelectedNodeIds = map(selectedNodes, 'id').filter(
      (id) => !(id === event?.item?.id && selectedNodeIds.includes(id) && state.shiftKeyActive)
    );

    // If the user is holding shift and is in select mode, then add their new
    // selection to their existing selection.
    const combineSelections = props.dragSelectMode && state.shiftKeyActive;

    updateSelectedNodeIdsQueryParam(
      combineSelections ? uniq([...selectedNodeIds, ...newSelectedNodeIds]) : newSelectedNodeIds
    );
  };

  const onAfterItemUnSelected = () => {
    props.isFieldPipeline && setState({ selectedNode: null });

    const page = editorRef.current.getCurrentPage();
    const selectedNodes = page.getSelected();

    if (!selectedNodes.length) {
      props.setSelectedGraphNode();

      updateUrlWithSelectedNodes();
    }

    if (props.isFieldPipeline && props.isGotoBetweenFieldPipelines) {
      props.setIsGotoBetweenFieldPipelines(false);
    }
  };

  const onNodePanelClose = () => {
    const page = editorRef.current?.getCurrentPage();
    page?.clearSelected();
  };

  const onChangeGraph = (evt: any) => {
    const newVersion = evt.key;
    if (state.haveUnsavedChanges) {
      const url = getGraphVersionUrl(entityId, newVersion.toLowerCase(), fieldId);
      props.setNavigatingTo(url);
      props.showUnsavedConfirmModal(true);
    } else {
      _navigateToGraphVersion({ graphVersion: newVersion, replace: false });
    }
  };

  const componentDidUpdateEP = (prevProps: PipelineEditorProps) => {
    const {
      selectedTestNodeId,
      entityId,
      liveTestGraphId,
      liveTestCompletedTimestamp,
      saveMappingsResponse,
      deleteMappingsResponse,
    } = props;

    // This evaluates to true when we receive the TEST_PIPELINE_DONE
    // notification from viper for this entity
    if (entityId === liveTestGraphId && prevProps.liveTestCompletedTimestamp !== liveTestCompletedTimestamp) {
      props.getPipeline();
    }

    if (selectedTestNodeId !== prevProps.selectedTestNodeId) {
      setState({
        selectedItemId: selectedTestNodeId,
        testNodeNotFoundVisible: !findTestNode(selectedTestNodeId, state.nodes),
      });
    }

    // Update the entity pipeline when a new `Sync to` or `Sync from` is added
    const updatePipelineOnNewMapping =
      saveMappingsResponse !== prevProps.saveMappingsResponse &&
      (saveMappingsResponse?.entityDraftUpdated || saveMappingsResponse?.newEntityDraft);

    // Update the entity pipeline when a mapping has been deleted and the pipeline have changed.
    // Example: A mapping has been deleted on an approved draft which automatically created a draft.
    const updatePipelineOnDeletedMapping =
      deleteMappingsResponse !== prevProps.deleteMappingsResponse &&
      (deleteMappingsResponse?.entityDraftUpdated || deleteMappingsResponse?.newEntityDraft);

    if (updatePipelineOnNewMapping || updatePipelineOnDeletedMapping) {
      props.getPipeline();
    }
  };

  const prevProps = usePreviousValue(props);

  useEffect(() => {
    if (prevProps) {
      // If there are no prevProps this is the initial render and we shouldn't
      // call componentDidUpdate
      isEntityPipeline ? componentDidUpdateEP(prevProps) : componentDidUpdateFP(prevProps);
    }
  });

  const scopeMatch = props.isEntityPipeline ? AppConstants.SCOPE.ENTITY : AppConstants.SCOPE.ATTRIBUTE;
  const copySelectedItems = useCopySelectedItems(editorRef.current, scopeMatch);
  const pasteStoredNodes = usePasteNodes(editorRef.current, scopeMatch);

  // 'Keydown' event listeners
  useEventListener('keydown', (event: any) => {
    const isCanvasFocused = document.activeElement?.nodeName === 'CANVAS';
    const isGraphModeSwitcherButton = ['selectModeToggle', 'panModeToggle'].includes(
      (document.activeElement as any)?.name
    );

    if (isCanvasFocused || isGraphModeSwitcherButton) {
      const pipelineIsDraft = [GRAPH_STATUS.NEW, GRAPH_STATUS.DRAFT].includes(props.graphVersion?.toUpperCase() as any);

      const cmdOrCtrlPressed = isCmdOrCtrlPressed(event);

      switch (event.key) {
        case AppConstants.KEYBOARD_EVENT_KEYS.BACKSPACE:
        case AppConstants.KEYBOARD_EVENT_KEYS.DELETE:
          if (confirmDeleteNeeded) {
            props.showDeleteMultipleNodesModal(true);
          }

          break;

        case AppConstants.KEYBOARD_EVENT_KEYS.COPY: {
          if (pipelineIsDraft && cmdOrCtrlPressed && !event.shiftKey) {
            copySelectedItems();
          }
          break;
        }

        case AppConstants.KEYBOARD_EVENT_KEYS.PASTE: {
          if (pipelineIsDraft && cmdOrCtrlPressed && !event.shiftKey) {
            pasteStoredNodes();
          }
          break;
        }

        case AppConstants.KEYBOARD_EVENT_KEYS.SHIFT: {
          setState({ shiftKeyActive: true });
          break;
        }

        case AppConstants.KEYBOARD_EVENT_KEYS.SPACE: {
          dispatch(setDragSelectMode(true));
          break;
        }
      }
    }
  });

  useEventListener('mouseup', (event: any) => {
    if (spaceUpRef.current) {
      spaceUpRef.current = false;
      dispatch(setDragSelectMode(false));
    }
    mouseDownRef.current = false;
  });

  useEventListener('mousedown', (event: any) => {
    mouseDownRef.current = true;
  });

  // 'Keyup' event listeners
  useEventListener('keyup', (event: any) => {
    switch (event.key) {
      case AppConstants.KEYBOARD_EVENT_KEYS.SHIFT: {
        setState({ shiftKeyActive: false });
        break;
      }

      case AppConstants.KEYBOARD_EVENT_KEYS.SPACE: {
        // if the user is already dragged and selected we don't change the select mode, we wait till the mouseup event is called
        if (mouseDownRef.current) {
          spaceUpRef.current = true;
        } else {
          dispatch(setDragSelectMode(false));
        }
        break;
      }
    }
  });

  // Set the current graph once it loads
  useEffectOnValueChange(() => {
    if (props.pipeline) {
      props.setCurrentGraph(props.pipeline);
    }
  }, [!!props.pipeline]);

  useEffect(() => {
    if (!prevProps) {
      // Bail out of initial render
      return;
    }

    // Handle node config changes
    if (props.savedNodeConfig !== prevProps.savedNodeConfig && prevProps.selectedNode) {
      delay(() => {
        // Manual update the selected graph
        updateGraphNode(props.savedNodeConfig);
        const { nodes, edges } = updateGraph({
          nodes: state.nodes,
          edges: state.edges,
          groups: state.groups,
          event: {
            action: AppConstants.NODE_ACTION.UPDATE_CONFIG,
            originModel: {
              id: prevProps.selectedNode.id,
            },
            updateModel: props.savedNodeConfig,
          },
        });

        setState({
          nodes,
          edges,
          haveUnsavedChanges: false,
          lastAction: ACTIONS.SAVE,
          changeKey: uniqueId(),
        });

        setSelectedNode({
          ...props.savedNodeConfig,
          id: props.savedNodeConfig.id || prevProps.selectedNode.id,
        });
        setCurrentGraph(nodes, edges);
        onSaveChanges(nodes, edges);
      }, 10);
    }

    if (prevProps.pipelineDiscarding === true && props.pipelineDiscarding === false) {
      if (isDraftOnlyGraph()) {
        if (isEntityPipeline) {
          // Redirect to the main entity page when discarding the only draft
          navigate(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }));
        } else {
          // If it's a FP then navigate to the entity pipeline
          navigate(makeUrl(RouteConstants.ENTITY_PIPELINE, { entityId }));
        }
      } else {
        // If there is a published version, navigate to that after discarding
        // the draft
        _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.APPROVED });
      }
    }

    // Once the pipeline has fetched, clear the component state and make sure a
    // valid graph version is in the url
    if (prevProps.pipelineFetching === true && props.pipelineFetching === false) {
      setState(getDefaultState());
      setCorrectDisplayedGraph(props.pipeline);
    }

    // Handle after the pipeline is deleted
    if (prevProps.pipelineDeleting === true && props.pipelineDeleting === false) {
      navigate(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }));
    }

    // Node check have change
    if (prevProps.nodeCheckId !== props.nodeCheckId || prevProps.nodeCheckValue !== props.nodeCheckValue) {
      // check/uncheck the node that changed
      setNodeCheckValue(props.nodeCheckId, props.nodeCheckValue);
    }
  });

  // Handle update group and ungroup actions
  useEffectOnValueChange(() => {
    const groupId = groupNodeUpdate?.groupId;
    const editor = editorRef.current;

    if (!groupId || !editor) {
      return;
    }

    switch (groupNodeUpdate.action) {
      case 'update':
        const model = groupNodeUpdate.data;

        if (!model) {
          break;
        }

        const update = {
          color: model.color,
          description: model.description,
          tags: model.tags,
          label: model.label,
          name: model.name,
        };

        editor.executeCommand(() => {
          const page = editor.getCurrentPage();
          const group = page.getSelected().find((item: any) => item.id === groupId);

          page.update(group, update);
        });

        // onSaveChanges uses the groups from the editor so we don't need to
        // update the state, just save and then reload the graph from the
        // backend.
        onSaveChanges();

        break;
      case 'ungroup':
        const newSelectedNodesIds: string[] = [];

        editor.executeCommand(() => {
          const page = editor.getCurrentPage();
          const group = page.getSelected().find((item: any) => item.id === groupId);

          if (group?.model?.collapsed) {
            // If we ungroup while the group is collapased the nodes don't show
            // on the canvas after removing the group
            page.update(group, { collapsed: false });
          }

          // Detatch nodes from group
          page.getNodes().forEach((node: any) => {
            if (node.model.parent === groupId) {
              page.update(node, { parent: undefined, skipChangeNotification: true });
              newSelectedNodesIds.push(node.id);
            }
          });

          // Remove group
          page.remove(group);
          message.success('Ungrouped group successfully');
        });

        // NOTE: We don't need to update state here since getGraphJsonForSave
        // will use the groups from the editor and remove the invalid groupIds
        // from nodes.
        setCurrentGraph();

        // Select the nodes within the group after removing the group
        updateSelectedNodeIdsQueryParam(newSelectedNodesIds);

        break;
    }

    dispatch(groupNodeUpdateAction(null));
  }, [groupNodeUpdate?.groupId]);

  // Handle nodeKebabMenu actions
  useEffectOnValueChange(() => {
    const nodeId = nodeKebabAction?.nodeId;
    const node = nodeKebabAction?.node;
    const editor = editorRef.current;

    if (!nodeId || !editor) {
      return;
    }

    switch (nodeKebabAction.action) {
      case 'configure':
        // Select only the node for the action
        updateSelectedNodeIdsQueryParam([nodeId]);
        openNodeConfigModal();

        break;
      case 'remove_from_group':
        editor.executeCommand(() => {
          const page = editor.getCurrentPage();
          const pageNode = page.find(nodeId);
          page.update(pageNode, { parent: undefined });
        });

        break;
      case 'delete':
        editor.executeCommand(() => {
          const page = editor.getCurrentPage();
          const pageNode = page.find(nodeId);
          page.remove(pageNode);
        });

        break;
      case 'duplicate':
        dispatch(showConfirmDuplicateModal({ visible: true, node }));
        break;
    }

    dispatch(setNodeKebabAction(null));
  }, [nodeKebabAction]);

  const areGraphDependenciesAvailable = () => {
    const nodesLength = props.isEntityPipeline ? props.connectorEntities?.length : props.attributeNodes?.length;

    return props.pipelineFunctions?.length > 0 && nodesLength > 0 && props.pipelineActions?.length > 0;
  };

  const getNewGraph = () => {
    let graph: any = {};
    const { pipeline = {} } = props;
    if (pipeline.draftStatus === GRAPH_STATUS.NEW) {
      if (state.newGraphJson) {
        graph = {
          nodes: state.newGraphJson.nodes,
          edges: state.newGraphJson.edges,
          groups: state.newGraphJson.groups,
        };
      } else {
        graph = {
          nodes: updateKeys(cloneDeep(pipeline.nodes)),
          edges: updateKeys(cloneDeep(pipeline.edges)),
          groups: updateKeys(cloneDeep(pipeline.groups || EMPTY_ARRAY)),
        };
        setState({
          newGraphJson: {
            ...state.metadata,
            nodes: graph.nodes,
            edges: graph.edges,
            groups: graph.groups,
          },
        });
      }
    }
    return graph;
  };

  const onCreateDraftClick = async () => {
    if (props.isFieldPipeline) {
      await props.createDraftFieldPipeline(props.fieldId as string);

      const newGraphVersion =
        props.graphVersion?.toUpperCase() === GRAPH_STATUS.DRAFT ? GRAPH_STATUS.NEW : GRAPH_STATUS.DRAFT;

      _navigateToGraphVersion({ graphVersion: newGraphVersion });
    } else if (!props.pipelineExists) {
      await props.createDraftEntityPipeline(props.entityId);
      props.getEntities();

      _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT });

      // If the EP doesn't have a published version, the draft status would be
      // /new before AND AFTER the draft is created. In this case we need to
      // manually remount the component to get the new pipeline.
      props.remountComponent();
    } else {
      const approvedDraft = getApprovedGraph();

      // Clone and create a valid draft
      const draftGraph = createDraftGraph(approvedDraft.nodes, approvedDraft.edges);
      const { draftStatus, draft, ...metadataSpread } = state.metadata;

      const pipelineSpecificGraph = props.isEntityPipeline
        ? {
            ...metadataSpread,
            id: ObjectID.generate(),
            parentId: state.metadata.id,
          }
        : getNewDraftMetadata();

      const updatedPipeline = {
        ...state.metadata,
        draft: {
          ...pipelineSpecificGraph,
          nodes: draftGraph.nodes,
          edges: draftGraph.edges,
          groups: approvedDraft.groups,
        },
        nodes: approvedDraft.nodes,
        edges: approvedDraft.edges,
        groups: approvedDraft.groups,
      };

      setState({
        haveUnsavedChanges: false,
        lastAction: ACTIONS.SAVE,
      });

      props.graphChanged({
        changed: null,
        changedScope: null,
        changedId: null,
      });

      const pipelineId = props.fieldId || props.entityId;
      await props.updatePipeline(pipelineId, updatedPipeline);

      _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT });
    }
  };

  // EP only function
  const isGraphReadOnly = () => {
    return currentGraphRef.current.readOnly && currentGraphRef.current.readOnlyMsg.length > 0;
  };

  const getData = () => {
    const { pipeline } = props;

    if (!pipeline) {
      return;
    }
    const { nodes, edges, groups, ...metadata } = pipeline;
    let displayedNodes = null,
      displayedEdges = null,
      displayedGroups = null;
    // Skip if the needed metadata are not available yet.
    if (!areGraphDependenciesAvailable()) {
      return {};
    }

    // Do not render the graph is there is no displayed graph set
    if (!props.displayedGraph) {
      return {};
    }

    if (!state.metadata && metadata?.id) {
      setState({
        metadata,
      });
    }

    // Get the graph that is selected
    let graph;
    switch (props.displayedGraph) {
      case GRAPH_STATUS.NEW:
        graph = getNewGraph();
        break;
      case GRAPH_STATUS.APPROVED:
        graph = getApprovedGraph();
        if (isEmpty(graph) && props.isFieldPipeline) {
          _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.NEW });
          return;
        }
        break;
      case GRAPH_STATUS.DRAFT:
        graph = getDraftGraph();
        if (props.isFieldPipeline) {
          if (isEmpty(graph)) {
            const newGraph = getNewGraph();
            if (!isEmpty(newGraph)) {
              _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.NEW });
            }
            return;
          }
        }
        break;
      default:
        // If the displayedGraph is not valid, redirect the user to a valid
        // displayedGraph
        const graphVersion = getDefaultGraphVersionFromPipeline(pipeline);
        _navigateToGraphVersion({ graphVersion });
        return;
    }
    displayedNodes = graph.nodes;
    displayedEdges = graph.edges;
    displayedGroups = graph.groups;

    // Initialize our node and edges state if needed
    if (displayedNodes) {
      if (state.nodes) {
        displayedNodes = state.nodes;
      } else {
        displayedNodes = updateKeys(cloneDeep(displayedNodes));
        setState({
          nodes: displayedNodes,
        });
      }
      if (state.edges) {
        displayedEdges = state.edges;
      } else {
        displayedEdges = updateKeys(cloneDeep(displayedEdges));
        setState({
          edges: displayedEdges,
        });
      }
      if (state.groups) {
        displayedGroups = state.groups;
      } else {
        displayedGroups = updateKeys(cloneDeep(displayedGroups || EMPTY_ARRAY));
        setState({
          groups: displayedGroups as Group[],
        });
      }

      const metadata = {
        pipelineFunctions: props.pipelineFunctions,
        ...(props.isEntityPipeline
          ? { connectorEntities: props.connectorEntities }
          : { attributeNodes: props.attributeNodes }),
        pipelineActions: props.pipelineActions,
        connectorIdToMetadataMap,
      };

      displayedNodes = getNodesForGraph(getNodesForPipelineGraph(displayedNodes), metadata);
      displayedEdges = getEdgesForGraph(getEdgesForPipelineGraph(displayedEdges));

      return {
        nodes: displayedNodes,
        edges: displayedEdges,
        groups: displayedGroups,
      };
    }
  };

  // TODO: I think this could be turned into a useMemo that returns the
  // pipelineFunctions rather than putting them into state.
  const getFunctions = () => {
    const showLoopFunction = isSettingsEnabled(PipelineSettings.simpleLoops);

    let funcs: any = [];
    if (state.pipelineFunctions.length <= 0 && props.pipelineFunctions.length > 0) {
      const filteredFunctions = props.pipelineFunctions.filter(
        (func) => showLoopFunction || func.name !== AppConstants.LOOP_FUNCTION_NAME
      );
      const pipelineFunctions = generateNodeIds(filteredFunctions);
      if (props.isFieldPipeline) {
        setState({
          pipelineFunctions,
        });
      }

      const scopeMatch = props.isEntityPipeline ? AppConstants.SCOPE.ENTITY : AppConstants.SCOPE.ATTRIBUTE;

      each(pipelineFunctions, (func) => {
        if (!func.hidden && func.scope === scopeMatch) {
          funcs.push({
            ...func,
            icon: getIconFromPath(func.iconPath),
            iconUrl: func.iconPath,
            id: func.id,
            key: func.id,
            ...(props.isEntityPipeline ? { nodeId: func.nodeId } : { configId: func.configId }),
            title: func.displayName || func.name,
            nodeType: AppConstants.NODE_TYPE.FUNCTION,
            shape: AppConstants.GRAPH_NODE_SHAPES.FUNCTION,
            suffix: (
              <Tooltip title={func.helpSummary} placement="topRight">
                <div>
                  <Icon className="flow-item-suffix" type="question-circle" theme="filled" />
                </div>
              </Tooltip>
            ),
          });
        }
      });
      setState({ pipelineFunctions: funcs });
    }
    return state.pipelineFunctions;
  };

  // FP only function
  const getAttributeNodes = () => {
    let sinkFields = [],
      sourceFields = [];
    if (props.attributeNodes?.length > 0) {
      const nodes = transformAttributeNodes(props.attributeNodes, props.connectors);
      sinkFields = map(nodes.sinkFields, (field) => {
        return {
          ...field,
          icon: <SIcon className="flow-item-prefix" src={field.icon} alt={field.name} />,
          iconUrl: field.icon,
          hideLeftStrip: true,
          tooltipMessage: field.connectorName,
          shape: getNodeShape(AppConstants.NODE_TYPE.ATTRIBUTE_SINK),
        };
      });
      sourceFields = map(nodes.sourceFields, (field) => {
        return {
          ...field,
          icon: <SIcon className="flow-item-prefix" src={field.icon} alt={field.name} />,
          iconUrl: field.icon,
          hideLeftStrip: true,
          tooltipMessage: field.connectorName,
          shape: getNodeShape(AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE),
        };
      });
    }

    return {
      sinkFields,
      sourceFields,
    };
  };

  // FP only function
  const generateNewAttributeId = (attributeId: string) => {
    const attributeNodes = generateNewId(attributeId, state.attributeNodes);
    if (attributeNodes !== state.attributeNodes) {
      setState({
        attributeNodes,
      });
    }
  };

  // EP only function
  const getConnectorEntities = () => {
    const entityResult: any[] = [];
    each(props.connectorEntities, (entity) => {
      // Skip core nodes and entities that are not active
      if (entity.status !== AppConstants.CONNECTOR_STATUS.ACTIVE && entity.coreNode !== true) {
        return;
      }
      const iconUrl = entity.iconPath;
      const id = ObjectID.generate();
      entityResult.push({
        key: id,
        id,
        icon: getIconFromPath(iconUrl),
        iconUrl,
        connectorEntityName: entity.name,
        title: entity.name,
        configId: entity.id,
        hideLeftStrip: true,
        nodeType: AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
        shape: getNodeShape(AppConstants.NODE_TYPE.CONNECTOR_ENTITY),
        custom: entity.custom,
        draftStatus: entity.draftStatus,
      });
    });
    return entityResult;
  };

  const { getNodeDisabledMetadata } = useNodeMetadataDisabled();

  const getActions = () => {
    return (
      props?.pipelineActions
        ?.filter((action: any) => !action.hidden)
        .map((action: any) => {
          const id = ObjectID.generate();

          const { disabled, disabledMessage } = getNodeDisabledMetadata(action);

          return {
            key: id,
            id,
            configId: action.id,
            disabled,
            disabledMessage,
            icon: getIconFromPath(action.iconPath),
            iconUrl: action.iconPath,
            nodeType: AppConstants.NODE_TYPE.ACTION,
            shape: AppConstants.GRAPH_NODE_SHAPES.ACTION,
            title: action.displayName || action.name,
            suffix: (
              <Tooltip title={action.helpSummary} placement="topRight">
                <div>
                  <Icon className="flow-item-suffix" type="question-circle" theme="filled" />
                </div>
              </Tooltip>
            ),
          };
        }) || EMPTY_ARRAY
    );
  };

  const onSwitchToDraftClick = () => _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT, replace: false });

  const getAvailableVersions = (): AvailableVersionsModel => {
    const availableVersions: any = {};
    const { APPROVED, DRAFT } = GRAPH_STATUS;
    if (!isEmpty(getApprovedGraph())) {
      availableVersions[APPROVED] = {};
    }
    if (!isEmpty(getDraftGraph()) || !isEmpty(getNewGraph())) {
      availableVersions[DRAFT] = {
        singleVersionTooltip: !availableVersions[APPROVED] ? tn('draft_not_published') : '',
      };
    }
    return availableVersions;
  };

  const findAndUpdateGraphModel = (model: any) => {
    const options = props.isEntityPipeline
      ? {
          connectorEntities: props.connectorEntities,
        }
      : {
          attributeNodes: props.attributeNodes,
          actions: props.pipelineActions,
        };

    let config = getNodeConfig(model, {
      functions: props.pipelineFunctions,
      ...options,
    });
    if (!config) {
      return;
    }
    const values: any = {};
    addImplicitValues(config.configuration, values, config.id);
    editorRef.current.executeCommand(() => {
      const page = editorRef.current.getCurrentPage();
      const item = page.find(model.id);
      if (item) {
        page.update(item, {
          metadata: {
            nodeType: model.nodeType,
            ...values,
            configuration: {
              ...values.configuration,
              ...model.configuration,
            },
          },
        });
      }
    });
    return values;
  };

  // EP only function
  const generateNewConnectorEntityId = (connectorEntityid: string) => {
    const connectorEntities = generateNewId(connectorEntityid, state.connectorEntities);
    if (connectorEntities !== state.connectorEntities) {
      setState({
        connectorEntities,
      });
    }
  };

  const _generateNewFunctionId = (functionId: string) => {
    const pipelineFunctions = generateNewId(functionId, state.pipelineFunctions);
    if (pipelineFunctions !== state.pipelineFunctions) {
      setState({
        pipelineFunctions,
      });
    }
  };

  const _generateNewActionId = (actionId: string) => {
    const pipelineActions = generateNewId(actionId, state.pipelineActions);
    if (pipelineActions !== state.pipelineActions) {
      setState({
        pipelineActions,
      });
    }
  };

  // Sometimes we need to update the editor data after the editor emits an
  // update. In this case we need to queue the updates so we don't run into an
  // infinite loop.
  const updateEditorAfterChanges = ({ nodes, edges, groups, event, nodeGroupValidationMessage }: any) => {
    if (eventsCalled.includes(event)) {
      return;
    }

    eventsCalled.push(event);

    let updateQueueCount = 0;

    const queueUpdate = (fn: () => void) => {
      updateQueueCount++;
      setTimeout(fn, updateQueueCount * 60);
    };

    // If there's a new node or a node's x coordinate changed check for stacked nodes
    if (event.action === 'add' || event.updateModel?.x) {
      // Unstacked any newly stacked nodes
      const unstackedCoordinates = getUnstackedNodeCoordinates(nodes);

      if (unstackedCoordinates.length) {
        unstackedCoordinates.forEach(({ id, ...nodeLocation }) => {
          const page = editorRef.current.getCurrentPage();
          const node = page.find(id);
          node && queueUpdate(() => page.update(node, nodeLocation));
        });
      }
    }

    // Update the childNodeSummary when nodes are added/removed from a group
    const groupIdsToUpdate: string[] = [];
    const nodeInGroupWasDeleted = event.action === 'remove' && event.item?.model?.parent;
    const nodeGroupHasChanged = event.updateModel && 'parent' in event.updateModel;
    const updateIsFromGroupCreation = eventJustBarelyOccured(event.item?.model?.timeAddedToGroup);

    const groupHasChanged = (nodeInGroupWasDeleted || nodeGroupHasChanged) && !updateIsFromGroupCreation;

    if (groupHasChanged) {
      if (event.item?.model?.parent) {
        groupIdsToUpdate.push(event.item?.model?.parent);
      }
      if (event.originModel?.parent) {
        groupIdsToUpdate.push(event.originModel.parent);
      }
      if (event.updateModel?.parent) {
        groupIdsToUpdate.push(event.updateModel.parent);
      }

      uniq(groupIdsToUpdate).forEach((groupIdToUpdate) => {
        const page = editorRef.current.getCurrentPage();
        const group: EditorGroup = page.find(groupIdToUpdate);

        const childNodeSummary = getChildNodeSummaryForGroup(nodes, groups, groupIdToUpdate);
        group && queueUpdate(() => page.update(group, { childNodeSummary }));
      });
    }

    // If a node was added to a group and it cannot be in that group, remove it
    // from the group and put it back into the original location
    if (nodeGroupValidationMessage) {
      const page = editorRef.current.getCurrentPage();
      queueUpdate(() => {
        page.update(event.item, {
          parent: undefined,
          x: event.originModel.x,
          y: event.originModel.y,
          skipChangeNotification: true,
        });
      });
    }

    // If an edge is drawn from a node that supports a predicate edge then
    // automatically create the predicate node and update the edges.
    if (event.action === 'add' && event.item?.isEdge === true && event.item.source?.model?.nodeType === 'FUNCTION') {
      const page = editorRef.current.getCurrentPage();

      const functionId = event.item.source?.model?.metadata?.configuration?.configId;
      const sourceFunction = find(props.pipelineFunctions, { id: functionId });

      let edgeOptions: any;

      if (sourceFunction) {
        edgeOptions = find(sourceFunction.configuration, { name: 'edgeOptions' });
      }
      const predicateFunction = find(props.pipelineFunctions, { name: AppConstants.PREDICATE_FUNCTION_NAME });

      // User should not be able to add a new target edge to an existing
      // predicate node
      const edgeIsAddedToExistingPredicateNode = [
        AppConstants.GRAPH_NODE_SHAPES.PREDICATE_FUNCTION,
        AppConstants.GRAPH_NODE_SHAPES.CASE_BRANCH_FUNCTION,
      ].includes(event.item.target?.model?.shape);

      if (edgeOptions?.edgeType === EdgeType.case && !edgeIsAddedToExistingPredicateNode) {
        // Find the source node and get the values of the case
        const cases = event.item.source?.model?.metadata?.configuration?.case?.cases;
        const caseBranchFunction = find(props.pipelineFunctions, { name: AppConstants.CASE_BRANCH_FUNCTION_NAME });

        let value = cases?.[0]?.caseName || SWITCH_CASE_BUILT_IN_CASE.DEFAULT;

        const caseBranchNodeId = ObjectID.generate();

        const { x: sourceX, y: sourceY } = event.item.source?.model;
        const { x: targetX, y: targetY } = event.item.target?.model;

        // Get the values of other predicate nodes attached to the same source
        // so we can set the value to an option that isn't already selected if
        // possible.
        const otherPredicateNodeIds = edges
          .filter((edge: any) => edge.id !== event.item.id && edge.source.nodeId === event.item.model.source)
          .map((edge: any) => edge.destination.nodeId);

        const otherPredicateNodeValues = nodes
          .filter((node: any) => otherPredicateNodeIds.includes(node.id))
          .map((node: any) => node.configuration.value);

        // let value = edgeOptions.defaultValue;

        const caseNames = cases?.map((caseValue: any) => {
          return caseValue.caseName;
        });
        // If the defaultValue is already on another predicate node then set,
        // then look for an unused option and set the value to it.
        if (otherPredicateNodeValues.includes(value)) {
          some(caseNames, (optionValue, key) => {
            if (!otherPredicateNodeValues.includes(optionValue)) {
              value = optionValue;
              return true;
            }
          });
        }

        // Add predicate node
        queueUpdate(() => {
          const configuration = {
            configId: caseBranchFunction?.id || '1',
            definition: caseBranchFunction?.id || '2',
            value,
            description: '',
            graphVersion: props.graphVersion,
          };

          // Get the new coordinates as halfway between the source and target
          // nodes
          const newX = (targetX - sourceX) / 2 + sourceX;
          const newY = (targetY - sourceY) / 2 + sourceY;

          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.NODE,
            addModel: {
              id: caseBranchNodeId,
              shape: AppConstants.GRAPH_NODE_SHAPES.CASE_BRANCH_FUNCTION,
              type: AppConstants.GRAPH_ITEM_TYPE.NODE,
              label: `${event.item.source?.model?.label} (Case)`,
              description: '',
              typeColor: '#4FC5C2',
              nodeType: AppConstants.NODE_TYPE.FUNCTION,
              parent: null,
              configuration,
              x: newX,
              y: newY,
            },
          });
        });

        // Update the existing edge to connect to the predicate node
        queueUpdate(() => {
          page.update(event.item, {
            target: caseBranchNodeId,
            targetAnchor: 0,
          });
        });

        const addNewEdgeModel = {
          id: ObjectID.generate(),
          source: caseBranchNodeId,
          sourceAnchor: 1,
          target: event.model.target,
          targetAnchor: event.model.targetAnchor,
        };
        // Add a new edge to connect the predicate node with the previously connected target
        queueUpdate(() => {
          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.EDGE,
            addModel: addNewEdgeModel,
          });
        });
      } else if (
        predicateFunction &&
        edgeOptions?.supported &&
        event.item.target?.model &&
        !edgeIsAddedToExistingPredicateNode
      ) {
        const predicateNodeId = ObjectID.generate();

        const { x: sourceX, y: sourceY } = event.item.source?.model;
        const { x: targetX, y: targetY } = event.item.target?.model;

        // Get the values of other predicate nodes attached to the same source
        // so we can set the value to an option that isn't already selected if
        // possible.
        const otherPredicateNodeIds = edges
          .filter((edge: any) => edge.id !== event.item.id && edge.source.nodeId === event.item.model.source)
          .map((edge: any) => edge.destination.nodeId);

        const otherPredicateNodeValues = nodes
          .filter((node: any) => otherPredicateNodeIds.includes(node.id))
          .map((node: any) => node.configuration.value);

        let value = edgeOptions.defaultValue;

        // If the defaultValue is already on another predicate node then set,
        // then look for an unused option and set the value to it.
        if (otherPredicateNodeValues.includes(value)) {
          some(edgeOptions.options, (optionValue, key) => {
            if (!otherPredicateNodeValues.includes(optionValue)) {
              value = optionValue;
              return true;
            }
          });
        }

        // Add predicate node
        queueUpdate(() => {
          const configuration = {
            configId: predicateFunction.id,
            definition: predicateFunction.id,
            value,
            description: '',
            graphVersion: props.graphVersion,
          };

          // Get the new coordinates as halfway between the source and target
          // nodes
          const newX = (targetX - sourceX) / 2 + sourceX;
          const newY = (targetY - sourceY) / 2 + sourceY;

          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.NODE,
            addModel: {
              id: predicateNodeId,
              shape: AppConstants.GRAPH_NODE_SHAPES.PREDICATE_FUNCTION,
              type: AppConstants.GRAPH_ITEM_TYPE.NODE,
              label: `${event.item.source?.model?.label} (decision)`,
              description: '',
              typeColor: '#4FC5C2',
              nodeType: AppConstants.NODE_TYPE.FUNCTION,
              parent: null,
              configuration,
              x: newX,
              y: newY,
            },
          });
        });

        // Update the existing edge to connect to the predicate node
        queueUpdate(() => {
          page.update(event.item, {
            target: predicateNodeId,
            targetAnchor: 0,
          });
        });

        const addNewEdgeModel = {
          id: ObjectID.generate(),
          source: predicateNodeId,
          sourceAnchor: 1,
          target: event.model.target,
          targetAnchor: event.model.targetAnchor,
        };
        // Add a new edge to connect the predicate node with the previously connected target
        queueUpdate(() => {
          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.EDGE,
            addModel: addNewEdgeModel,
          });
        });
      }
    }

    // If a loop node is added, automatically create the After and For Each nodes
    if (event.action === 'add' && event.item?.isNode === true && event?.item?.model?.nodeType === 'FUNCTION') {
      const functionId = event.item.model?.metadata?.configuration?.configId;
      const sourceFunction = find(props.pipelineFunctions, { id: functionId });

      const loopFunction = find(props.pipelineFunctions, { name: AppConstants.LOOP_FUNCTION_NAME });

      // Check if the added node is the loop function
      if (sourceFunction?.id && sourceFunction?.id === loopFunction?.id) {
        // Add the For Each Node
        const forEachFunction = find(props.pipelineFunctions, {
          name: AppConstants.FOR_EACH_FUNCTION_NAME,
        }) as PipelineFunction;

        const forEachNodeId = ObjectID.generate();

        queueUpdate(() => {
          const configuration = {
            configId: forEachFunction.id,
            definition: forEachFunction.id,
            value: undefined, // The For Each node holds no value
            description: '',
            graphVersion: props.graphVersion,
          };

          const { x: loopX, y: loopY } = event.item.model;

          // Set the new coordinates to be below the loop node
          const newX = loopX;
          const newY = loopY + 110;

          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.NODE,
            addModel: {
              id: forEachNodeId,
              shape: AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION,
              type: AppConstants.GRAPH_ITEM_TYPE.NODE,
              label: tn('for_each'),
              description: '',
              typeColor: '#4FC5C2',
              nodeType: AppConstants.NODE_TYPE.FUNCTION,
              parent: null,
              configuration,
              x: newX,
              y: newY,
            },
          });
        });

        const addForEachEdgeModel = {
          id: ObjectID.generate(),
          source: event.item.model.id,
          sourceAnchor: 2,
          target: forEachNodeId,
          targetAnchor: 0,
        };
        // Add a new edge to connect the loop node with the For Each node
        queueUpdate(() => {
          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.EDGE,
            addModel: addForEachEdgeModel,
          });
        });

        // Add the End Loop node
        const endLoopFunction = find(props.pipelineFunctions, {
          name: AppConstants.END_LOOP_FUNCTION_NAME,
        }) as PipelineFunction;

        const endLoopNodeId = ObjectID.generate();

        queueUpdate(() => {
          const configuration = {
            configId: endLoopFunction.id,
            definition: endLoopFunction.id,
            value: undefined, // The For Each node holds no value
            description: '',
            graphVersion: props.graphVersion,
          };

          const { x: loopX, y: loopY } = event.item.model;

          // Set the new coordinates to be far below the loop node
          const newX = loopX;
          const newY = loopY + 310;

          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.NODE,
            addModel: {
              id: endLoopNodeId,
              shape: AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION,
              type: AppConstants.GRAPH_ITEM_TYPE.NODE,
              label: tn('end_loop'),
              description: '',
              typeColor: '#4FC5C2',
              nodeType: AppConstants.NODE_TYPE.FUNCTION,
              parent: null,
              configuration,
              x: newX,
              y: newY,
            },
          });
        });

        const addEndLoopEdgeModel = {
          id: ObjectID.generate(),
          source: endLoopNodeId,
          sourceAnchor: 1,
          target: event.item.model.id,
          targetAnchor: 1,
        };
        // Add a new edge to connect the loop node with the For Each node
        queueUpdate(() => {
          editorRef.current.executeCommand(AppConstants.NODE_ACTION.ADD, {
            type: AppConstants.GRAPH_ITEM_TYPE.EDGE,
            addModel: addEndLoopEdgeModel,
          });
        });
      }
    }

    // When an item is removed, check all the predicate nodes to ensure they
    // still have both a source and destination edge. If not they should be
    // removed.
    if (event.action === AppConstants.NODE_ACTION.REMOVE) {
      const page = editorRef.current.getCurrentPage();
      const graph = page.getGraph();

      graph
        .getNodes()
        .filter((node: any) => node.model.shape === AppConstants.GRAPH_NODE_SHAPES.PREDICATE_FUNCTION)
        .filter((predicateNode: any) => {
          const missingSourceEdge = !edges.some((edge: any) => edge.source.nodeId === predicateNode.id);
          const missingTargetEdge = !edges.some((edge: any) => edge.destination.nodeId === predicateNode.id);
          return missingSourceEdge || missingTargetEdge;
        })
        .forEach((node: any) => {
          const nodeId = node.id;
          queueUpdate(() => {
            const predicateNode = page.find(nodeId);
            graph.remove(predicateNode);
          });
        });

      graph
        .getNodes()
        .filter((node: any) => node.model.shape === AppConstants.GRAPH_NODE_SHAPES.CASE_BRANCH_FUNCTION)
        .filter((predicateNode: any) => {
          const missingSourceEdge = !edges.some((edge: any) => edge.source.nodeId === predicateNode.id);
          const missingTargetEdge = !edges.some((edge: any) => edge.destination.nodeId === predicateNode.id);
          return missingSourceEdge || missingTargetEdge;
        })
        .forEach((node: any) => {
          const nodeId = node.id;
          queueUpdate(() => {
            const predicateNode = page.find(nodeId);
            graph.remove(predicateNode);
          });
        });
    }

    // When a loop start node is removed, remove the associated sub nodes
    if (event.action === AppConstants.NODE_ACTION.REMOVE && event?.item?.model?.metadata?.configuration?.loopStart) {
      const page = editorRef.current.getCurrentPage();
      const graph = page.getGraph();

      const nodes = graph.getNodes();

      const orphanedLoopSubNodes = findOrphanedLoopSubNodes(nodes, edges);
      orphanedLoopSubNodes.forEach((node: any) => {
        const nodeId = node.id;
        queueUpdate(() => {
          const node = page.find(nodeId);
          if (node) {
            // Add the forceDelete property to the node to prevent the
            // onCustomBeforeDelete function from stopping it.
            node.model.forceDelete = true;
            graph.remove(node);
          }
        });
      });
    }

    // When a node is added to the canvas, if it's dropped within a group tell
    // the user to connect the node before trying to add the node to a group.
    if (event.action === AppConstants.NODE_ACTION.ADD && event.item?.isNode) {
      const withinRange = (min: number, max: number, x: number) => {
        return x >= min && x <= max;
      };

      const newNewIsDroppedOnGroup = editorRef.current
        .getCurrentPage()
        .getGroups()
        .some((group: EditorGroup & any) => {
          const groupBox = group.getBBox();
          const itemBox = event.item.bbox;

          return (
            withinRange(groupBox.minX, groupBox.maxX, itemBox.centerX) &&
            withinRange(groupBox.minY, groupBox.maxY, itemBox.centerY)
          );
        });

      const shouldSkipNotification = event.item?.model?.shouldSkipNotification;

      if (newNewIsDroppedOnGroup && !shouldSkipNotification) {
        return message.warning('Connect this node before adding to a group.', 3);
      }
    }
  };

  const onGraphChange = (evt: any) => {
    // Disable graph updates when the test result is visible
    if (props.testResultVisible) {
      return;
    }

    if (
      (props.isEntityPipeline || evt?.model) &&
      !evt?.model?.fragment &&
      evt.action === AppConstants.NODE_ACTION.ADD &&
      evt.item.type === AppConstants.GRAPH_ITEM_TYPE.NODE
    ) {
      // This is triggered when a user drag and drop an item from
      // the right to the graph

      // Normalize the configuration
      evt.model = normalizeConfigId(evt.model);
      const metadata = findAndUpdateGraphModel(evt.model);
      if (metadata) {
        evt.model.metadata = metadata;
        // Make sure the model configuration is in sync with metadata configuration
        // since downstream functions look at the model cofiguration only.
        evt.model.configuration = {
          ...evt.model.configuration,
          ...metadata.configuration,
        };
      }

      // Update the right hand side to generate a new id
      const { nodeType } = evt.model;
      if (props.isEntityPipeline && nodeType === AppConstants.NODE_TYPE.CONNECTOR_ENTITY) {
        generateNewConnectorEntityId(evt.model.id);
      } else if (
        props.isFieldPipeline &&
        (nodeType === AppConstants.NODE_TYPE.ATTRIBUTE_SINK || nodeType === AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE)
      ) {
        generateNewAttributeId(evt.model.id);
      }
      if (nodeType === AppConstants.NODE_TYPE.FUNCTION) {
        _generateNewFunctionId(evt.model.id);
      }
      if (nodeType === AppConstants.NODE_TYPE.ACTION) {
        _generateNewActionId(evt.model.id);
      }

      updateGraphDuplicateNames(editorRef.current, evt.model);
    }

    // Clear the kebab dropdown when a node is removed
    if (evt.action === AppConstants.NODE_ACTION.REMOVE && evt.item?.isNode) {
      dispatch(clearNodeForKebabMenu());
    }

    // Handler when a fragment is dropped to the canvas
    if (evt.action === AppConstants.NODE_ACTION.ADD && evt?.model?.fragment) {
      const fragment = props.fragments.find((frag: any) => frag.id === evt.item.id);
      if (fragment?.fragment) {
        const newIds: Record<string, any> = {};
        const newNodeLocations = getNewFragmentNodeLocations(evt.model, fragment.fragment.nodes);

        const fragmentNodes: any = [];
        const fragmentEdges: any = [];

        fragment.fragment.nodes.forEach((node: any) => {
          const newId = ObjectID.generate();
          newIds[node.templateId] = newId;

          let coreNode;
          const matchingNodeType = props.isEntityPipeline
            ? AppConstants.NODE_TYPE.CORE_ENTITY
            : AppConstants.NODE_TYPE.CORE_ATTRIBUTE;
          if (node.nodeType === matchingNodeType) {
            coreNode = state.nodes.find((node: any) => {
              return node.nodeType === matchingNodeType;
            });
            if (coreNode) {
              newIds[node.templateId] = coreNode.id;
            }
          } else {
            // TODO: Skip core nodes and make the edge connect to the current graph core node
            fragmentNodes.push({
              ...omit(node, ['templateId']),
              id: newId,
              location: newNodeLocations[node.templateId],
            });
          }
        });

        fragment.fragment.edges.forEach((edge: any) => {
          fragmentEdges.push({
            ...omit(edge, ['templateId']),
            id: ObjectID.generate(),
            source: { ...edge.source, nodeId: newIds[edge.source.nodeId] },
            destination: { ...edge.destination, nodeId: newIds[edge.destination.nodeId] },
          });
        });

        setState({
          // Update the first key to trigger the graph to read the new data
          nodes: updateKeys([...state.nodes, ...fragmentNodes]),
          edges: [...state.edges, ...fragmentEdges],
        });

        // Remove the original node
        const page = editorRef.current.getCurrentPage();
        const node = page.find(evt.item.id);
        page.getGraph().remove(node);

        // Select the nodes in the fragment
        updateSelectedNodeIdsQueryParam(map(newIds));
      }
    }

    // Ignore when the test simulation is visible
    const epCheck = !props.nodeCheckMode && !isInternalNodeUpdate(evt) && isUndefined(evt?.updateModel?.checkedNode);
    // Non fragment was dropped to the canvas
    const fpCheck = !isInternalNodeUpdate(evt);

    const checkValue = props.isEntityPipeline ? epCheck : fpCheck;

    // Check if we need to enable the save button
    if (!evt?.model?.fragment && checkValue) {
      // This callback gets called twice in development (strict mode) which
      // causes updateEditorAfterChanges to be called twice with the same event.
      // This needs to be refactored.
      setState((currentState) => {
        const { nodes, edges, groups, nodeGroupValidationMessage } = updateGraph({
          nodes: currentState.nodes,
          edges: currentState.edges,
          groups: currentState.groups,
          event: evt,
        });

        updateEditorAfterChanges({ nodes, edges, groups, event: evt, nodeGroupValidationMessage });
        setCurrentGraph(nodes, edges);

        return {
          ...currentState,
          nodes,
          edges,
          groups,
        };
      });

      // If the event makes the edge invalid, remove it from the graph. Removing
      // the edge from the graph triggers a REMOVE event which will update the state.
      if (evt?.item?.type === AppConstants.GRAPH_ITEM_TYPE.EDGE && evt.action !== AppConstants.GRAPH_ACTION.REMOVE) {
        const graph = editorRef.current.getCurrentPage()?.getGraph();
        if (graph) {
          const graphEdges = graph.getEdges();
          if (edgeIsInvalid(graphEdges, evt.item, evt)) {
            graph.remove(evt.item);
          }
        }
      }

      if (evt.action === AppConstants.GRAPH_ACTION.REMOVE && evt.item?.type === AppConstants.GRAPH_ITEM_TYPE.NODE) {
        onAfterItemUnSelected();
      }

      // Do not treat change graph as graph as changes
      // since it reloading the entire graph
      if (
        (!props.isFieldPipeline || (!props.nodeCheckMode && isUndefined(evt?.updateModel?.checkedNode))) &&
        props.displayedGraph !== GRAPH_STATUS.APPROVED &&
        evt.action !== AppConstants.NODE_ACTION.CHANGE_DATA
      ) {
        const skipUnsavedChanges = !_isGraphEditable() || isNumber(evt.updateModel?.changeShouldNotPromptSave);
        if (!skipUnsavedChanges) {
          setState({
            haveUnsavedChanges: true,
          });
          props.graphChanged({
            changed: true,
            changedScope: AppConstants.SCOPE.ENTITY,
            changedId: props.entityId,
          });
        }
      }
    }
  };

  const removeSelectedNode = () => {
    onGraphChange({
      action: AppConstants.GRAPH_ACTION.REMOVE,
      item: {
        ...(props.isEntityPipeline ? props : state).selectedNode,
        type: AppConstants.GRAPH_ITEM_TYPE.NODE,
      },
    });

    const page = editorRef.current.getCurrentPage();
    const selectedItem: any = first(page.getSelected());
    if (selectedItem?.id) {
      page.getGraph().remove(selectedItem.id);
    }
  };

  const openNodeConfigModal = (flag = true) => {
    setCurrentGraph();
    props.showNodeConfigModal(flag);
  };

  const isRemoveNodeDisabled = () => {
    const disabled = !_isGraphEditable();
    const isCoreNode = [AppConstants.NODE_TYPE.CORE_ENTITY, AppConstants.NODE_TYPE.CORE_ATTRIBUTE].includes(
      state.selectedNode?.nodeType
    );
    return disabled || isCoreNode;
  };

  // FP only function
  const removeNodeDisabledMessage = () => {
    if (state.selectedNode.nodeType === AppConstants.NODE_TYPE.CORE_ATTRIBUTE) {
      return tn('cannot_make_changes_core_attribute');
    }
    return tn('cannot_make_changes_remove');
  };

  const _isGraphEditable = () => {
    const graphStatusIsEditable =
      props?.graphVersion && [GRAPH_STATUS.NEW, GRAPH_STATUS.DRAFT].includes(props.graphVersion.toUpperCase() as any);

    const graphModeIsEditable = graphMode === GRAPH_MODE.DEFAULT || graphMode === GRAPH_MODE.DRAG_SELECT;
    return graphStatusIsEditable && graphModeIsEditable;
  };

  const canCopyNode = () => {
    const selectedNode = (props.isEntityPipeline ? props : state).selectedNode;

    const subNode = isSubNode(selectedNode, { functions: props.pipelineFunctions });

    return (
      !subNode &&
      (selectedNode?.nodeType === AppConstants.NODE_TYPE.FUNCTION ||
        selectedNode?.nodeType === AppConstants.NODE_TYPE.ACTION)
    );
  };

  const canDeleteNode = () => {
    const selectedNode = (props.isEntityPipeline ? props : state).selectedNode;
    return selectedNode?.shape !== AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION;
  };

  const _getNodeActions = () => {
    if (!_isGraphEditable()) {
      return [];
    }

    const disabled = !_isGraphEditable();
    return [
      {
        name: tn('configure_node'),
        key: 'configure-node',
        icon: 'edit',
        disabled,
        disabledMessage: tn('cannot_make_changes_configure'),
        handler: async () => {
          if (state.haveUnsavedChanges) {
            await onSaveChanges();
          }
          if (props.isEntityPipeline) {
            openNodeConfigModal();
          } else {
            setCurrentGraph();
            props.showNodeConfigModal(true);
          }
        },
      },
      {
        name: tn('clone_node'),
        icon: 'copy',
        key: 'clone-node',
        hidden: !canCopyNode(),
        disabled,
        disabledMessage: disabled ? tn('cannot_make_changes_configure') : tn('only_copy_functions_actions'),
        handler: () => {
          copySelectedItems(false);
          pasteStoredNodes();
        },
      },
      {
        name: tn('copy_node'),
        icon: 'copy',
        key: 'copy-node',
        hidden: !canCopyNode(),
        disabled,
        disabledMessage: disabled ? tn('cannot_make_changes_configure') : tn('only_copy_functions_actions'),
        handler: copySelectedItems,
      },
      {
        name: tn('remove_node'),
        icon: 'delete',
        key: 'remove-node',
        hidden: !canDeleteNode(),
        disabled: isRemoveNodeDisabled(),
        disabledMessage: props.isEntityPipeline ? tn('cannot_make_changes_remove') : removeNodeDisabledMessage(),
        handler: removeSelectedNode,
      },
    ].filter((item) => !item.hidden);
  };

  const shouldShowNodePanel = () => {
    const nodePanelSupported = [
      // FP
      AppConstants.NODE_TYPE.CORE_ATTRIBUTE,
      AppConstants.NODE_TYPE.ATTRIBUTE_SINK,
      AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE,
      // EP
      AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
      AppConstants.NODE_TYPE.ENTITY_SINK,
      AppConstants.NODE_TYPE.ENTITY_SOURCE,
      // Both
      AppConstants.NODE_TYPE.FUNCTION,
      AppConstants.NODE_TYPE.ACTION,
      AppConstants.NODE_TYPE.CUSTOM_GROUP,
    ];

    const propsOrState = props.isEntityPipeline ? props : state;

    if (propsOrState.selectedNode?.nodeType) {
      return nodePanelSupported.indexOf(propsOrState.selectedNode.nodeType) !== -1;
    }

    if (propsOrState.selectedNode?.metadata?.nodeType) {
      return nodePanelSupported.indexOf(propsOrState.selectedNode.metadata.nodeType) !== -1;
    }

    return false;
  };

  // EP only function
  const _shouldShowEntityPanel = () => {
    return props.isEntityPipeline && props.selectedNode?.nodeType === AppConstants.NODE_TYPE.CORE_ENTITY;
  };

  // EP only function
  const _getNodeConfigActions = () => {
    return [
      {
        name: 'Configure Node',
        icon: 'edit',
        handler: () => props.showNodeConfigModal(true),
      },
    ];
  };

  // EP only function
  const _getEntityPanel = () => {
    const { entityId } = props;
    return (
      <EntityEditorEntityPanel
        key={entityId}
        entityId={entityId}
        entityName={state.entityName}
        graphVersion={props.graphVersion as GraphStatus}
        editable={_isGraphEditable()}
        actions={_getNodeConfigActions()}
        node={props.selectedNode}
        onClose={onNodePanelClose}
      />
    );
  };

  const _doesNeedConfiguration = (nodeConfig?: any) => {
    let config = nodeConfig;
    if (!config) {
      config = _getNodeConfig();
    }

    return doesNeedConfiguration(config);
  };

  const _getNodeConfig = () => {
    const propsOrState = props.isEntityPipeline ? props : state;
    return getNodeConfig(propsOrState.selectedNode, {
      functions: props.pipelineFunctions,
      attributeNodes: props.attributeNodes,
      actions: props.pipelineActions,
      connectorEntities: props.connectorEntities,
    });
  };

  const onCreateFragment = () => {
    props.enableNodeCheck();
    props.showCreateFragmentModal();
  };

  const getContextPanel = () => {
    const propsOrState = props.isEntityPipeline ? props : state;
    const displayCreateDraft = isApproveOnlyGraph();
    const displaySwitchDraft = isApproveWithDraftGraph() && props.displayedGraph === GRAPH_STATUS.APPROVED;

    if (props.isEntityPipeline && !state.entityName && props.entities) {
      setState({
        entityName: getUrlListItemName(AppConstants.LIST_TYPES.ENTITY, props.entityId, {
          entities: props.entities,
        }),
      });
    }

    if (selectedNodeIds.length > 1) {
      return <MultipleNodesPanel editor={editorRef.current} scope={scopeMatch} />;
    }

    if (shouldShowNodePanel()) {
      const selectedNode = (props.isEntityPipeline ? props : state).selectedNode;
      if (selectedNode?.nodeType === AppConstants.NODE_TYPE.CUSTOM_GROUP) {
        return <GroupNodePanel selectedNode={selectedNode} isEditable={_isGraphEditable()} />;
      }

      const config = _getNodeConfig();
      if (isEmpty(config)) {
        return (
          <EmptyGraphPanel icon={NoConfigurationIcon} onActionClick={removeSelectedNode} actionText={tn('remove_node')}>
            <span>{tn('node_no_longer_valid')}</span>
          </EmptyGraphPanel>
        );
      } else if (_doesNeedConfiguration(config)) {
        return (
          <NodePanel
            key={`node-panel-${config.name}`}
            title={propsOrState.selectedNode.label}
            node={propsOrState.selectedNode}
            changeKey={state.changeKey}
            actions={_getNodeActions() as any}
            onClose={onNodePanelClose}
            graphVersion={props.graphVersion}
          />
        );
      }
      return (
        <EmptyGraphPanel icon={NoConfigurationIcon}>
          <span>{tn('no_configuration_needed')}</span>
        </EmptyGraphPanel>
      );
    }
    if (_shouldShowEntityPanel()) {
      return _getEntityPanel();
    } else if (displayCreateDraft) {
      return (
        <EmptyGraphPanel
          actionButtonType="primary"
          actionDisabled={props.pipelineSaving}
          onActionClick={onCreateDraftClick}
          actionText={tn('create_draft_action')}
          actionPermission={AllPermissions.WRITE_STUDIO}>
          <span>{tn('create_draft')}</span>
        </EmptyGraphPanel>
      );
    } else if (props.isEntityPipeline && isGraphReadOnly()) {
      const test = tn('test_in_progress', {
        newLine: AppConstants.MARKUP.NEW_LINE,
        interpolation: { escapeValue: false },
      });

      return (
        <EmptyGraphPanel icon={RefreshIcon}>
          <span dangerouslySetInnerHTML={{ __html: test }} />
        </EmptyGraphPanel>
      );
    } else if (props.isEntityPipeline && displaySwitchDraft) {
      const switchDraft = tn('switch_draft', {
        newLine: AppConstants.MARKUP.NEW_LINE,
        interpolation: { escapeValue: false },
      });
      return (
        <EmptyGraphPanel onActionClick={onSwitchToDraftClick} actionText={tn('switch_draft_action')}>
          <span dangerouslySetInnerHTML={{ __html: switchDraft }} />
        </EmptyGraphPanel>
      );
    } else if (_isGraphEditable()) {
      const { sinkFields, sourceFields } = getAttributeNodes();

      const entityName = getUrlListItemName(AppConstants.LIST_TYPES.FIELD, props.fieldId as string, {
        entities: props.entities,
      });

      const PanelComponent: any = props.isEntityPipeline ? EntityPipelinePanel : FieldPipelinePanel;

      const panelProps = props.isEntityPipeline
        ? {
            title: state.entityName,
            connectors: getConnectorEntities(),
            getFragmentStatus: props.getFragmentStatus,
          }
        : {
            getEditor: () => editorRef.current,
            data: getData(),
            title: entityName,
            sourceFields,
            sinkFields,
          };

      return (
        <PanelComponent
          {...panelProps}
          functions={getFunctions()}
          actions={getActions()}
          showShareFragmentModal={props.showShareFragmentModal}
          onCreateFragment={onCreateFragment}
          fragments={props.fragments}
          deleteFragment={props.deleteFragment}
          hideFragment={props.hideFragment}
          showFragment={props.showFragment}
          deleteFragmentStatus={props.deleteFragmentStatus}
          deleteFragmentErrorMessage={props.deleteFragmentErrorMessage}
          hideFragmentStatus={props.hideFragmentStatus}
          hideFragmentErrorMessage={props.hideFragmentErrorMessage}
          showFragmentStatus={props.showFragmentStatus}
          showFragmentErrorMessage={props.showFragmentErrorMessage}
        />
      );
    } else if (props.isFieldPipeline && displaySwitchDraft) {
      return (
        <EmptyGraphPanel onActionClick={onSwitchToDraftClick} actionText={tn('switch_draft_action')}>
          <span>{tn('switch_to_draft_with_changes')}</span>
        </EmptyGraphPanel>
      );
    }
  };

  // EP only function
  const setGraphForPublishReadyOnly = () => {
    const { nodes, edges } = state;
    const draftGraph = createDraftGraph(nodes, edges);
    const id = ObjectID.generate();
    const { draftStatus, draft, ...metadataSpread } = state.metadata;

    // Save the draft to the state
    const graph = {
      ...state.metadata,
      nodes,
      edges,
      draft: {
        ...metadataSpread,
        id,
        parentId: state.metadata.id,
        nodes: draftGraph.nodes,
        edges: draftGraph.edges,
      },
    };
    props.setGraphForPublishReadyOnly(graph);
  };

  // EP only function
  const onPublishPipeline = async () => {
    if (props.changed) {
      await onSaveChanges();
      if (props.errorMessage) {
        Modal.error({
          title: props.errorTitle,
          content: props.errorMessage,
        });
      } else {
        publishPipeline();
      }
    } else {
      publishPipeline();
    }
  };

  // EP only function
  const publishPipeline = () => {
    setGraphForPublishReadyOnly();
    const connectorsMap = keyBy(props.connectors, 'id');
    const connectorsMetadataMap = keyBy(props.connectorsMetadata, 'id');
    const hasUnpublishedCustomSynapse = props.pipeline.nodes.some((node: any) => {
      const connector = connectorsMap[node?.configuration?.connectorId];
      return connectorIsCustomDraft(connectorsMetadataMap[connector?.metadataId]);
    });
    props.showPublishDraftModal(true, props.entityId, hasUnpublishedCustomSynapse);
    // Close the test pannel when publishing to prevent users from running tests
    // on a published pipeline
    props.setTestPanelView(TestPanelView.CLOSED);
  };

  // TODO: This function needs to be stabilized because it's unsed in useEffects
  // in other components. It's a big lift since getGraphJsonForSave references
  // lots of other functions that aren't stable.
  const onValidate = useCallback(() => {
    const { validate, entityId, fieldId } = props;
    const { edges, nodes } = state;

    const graphJson = getGraphJsonForSave({ nodes, edges });
    validate(props.isFieldPipeline && fieldId ? fieldId : entityId, graphJson);

    setState({
      lastAction: ACTIONS.VALIDATE,
    });
  }, [getGraphJsonForSave, props, setState, state]);

  // EP only function
  const onTest = () => {
    props.setTestPanelView(TestPanelView.LIVE_RUN);
  };

  // EP only function
  const onStop = () => {
    props.stop(props.entityId);
  };

  // EP only function
  const onStart = () => {
    props.start(props.entityId);
  };

  const updateNodes = () => {
    // Iterate through the graph and enable the mode
    const editor = editorRef.current;
    const page = editor?.getCurrentPage();
    const nodes = page?.getNodes() || EMPTY_ARRAY;
    const groups = page?.getGroups() || EMPTY_ARRAY;

    let errorCountsByNodeId: any;
    let warningCountsByNodeId: any;
    const isPublishedGraph = props.displayedGraph === AppConstants.GRAPH_STATUS.APPROVED;

    if (props.isEntityPipeline) {
      const entityId = props.entityId;
      const entityPipelineId = props.pipeline?.draft ? props.pipeline?.draft.id : props.pipeline.id;

      const fields = getEntity(entityId, props.entities)?.fields;

      let coreNode = null;

      if (nodes) {
        coreNode = nodes.filter((node: any) => node.model.shape === AppConstants.NODE_TYPE_SHAPE_MAP.CORE_ENTITY)?.[0];
      }

      errorCountsByNodeId = getValidationResultCountsByNodeId(
        isPublishedGraph ? pipelineErrors : props.validationErrors,
        ValidationMode.ENTITY,
        coreNode?.id,
        entityId,
        entityPipelineId,
        fields
      );

      warningCountsByNodeId = getValidationResultCountsByNodeId(
        isPublishedGraph ? pipelineWarnings : props.validationWarnings,
        ValidationMode.ENTITY,
        coreNode?.id,
        entityId,
        entityPipelineId,
        fields
      );
    } else {
      errorCountsByNodeId = getValidationResultCountsByNodeId(
        isPublishedGraph ? pipelineErrors : props.validationErrors,
        ValidationMode.FIELD
      );
      warningCountsByNodeId = getValidationResultCountsByNodeId(
        isPublishedGraph ? pipelineWarnings : props.validationWarnings,
        ValidationMode.FIELD
      );
    }

    const groupErrorCountsByGroupId = getValidationResultCountsByGroupId(
      isPublishedGraph ? pipelineErrors : props.validationErrors,
      nodes,
      groups
    );
    const groupWarningsByGroupId = getValidationResultCountsByGroupId(
      isPublishedGraph ? pipelineWarnings : props.validationWarnings,
      nodes,
      groups
    );

    nodes &&
      nodes.forEach((node: any) => {
        editor.executeCommand(() => {
          const errorCount = errorCountsByNodeId ? errorCountsByNodeId[node.id] || 0 : 0;
          const warningCount = warningCountsByNodeId ? warningCountsByNodeId[node.id] || 0 : 0;

          const page = editor.getCurrentPage();
          const item = page.find(node.id);
          page.update(item, {
            errorCount,
            warningCount,
          });
        });
      });

    groups &&
      groups.forEach((group: any) => {
        editor.executeCommand(() => {
          const errorCount = groupErrorCountsByGroupId ? groupErrorCountsByGroupId[group.id] || 0 : 0;
          const warningCount = groupWarningsByGroupId ? groupWarningsByGroupId[group.id] || 0 : 0;

          const page = editor.getCurrentPage();
          const item = page.find(group.id);
          page.update(item, {
            errorCount,
            warningCount,
          });
        });
      });
  };

  const {
    errors: pipelineErrors,
    warnings: pipelineWarnings,
    resultsPanelVisible,
    showPipelineErrorResultsPanel,
  } = usePipelineError({ updateNodes });

  // TODO: Remove this whole function and rely on the selectedNodes from redux
  // store
  const setSelectedNode = (model: any) => {
    setCurrentGraph();
    const newModel = normalizeConfigId(model);
    if (newModel && !newModel.metadata) {
      newModel.metadata = {
        ...newModel,
      };
    }
    props.isFieldPipeline && setState({ selectedNode: newModel });
    props.setSelectedGraphNode(props.isEntityPipeline ? cloneDeep(newModel) : newModel);

    if (props.validationResultsPanelVisible) {
      props.showValidationResultsPanel(false);
    }

    // Hide the pipeline error panel here
    if (resultsPanelVisible) {
      showPipelineErrorResultsPanel(false);
    }

    if (props.testPanelView === TestPanelView.LIVE_RUN || props.testPanelView === TestPanelView.SIMULATED_RUN) {
      props.setTestPanelView(TestPanelView.CLOSED);
    }
  };

  // EP only function
  const navigateToEntities = () => {
    const url = makeUrl(RouteConstants.ENTITY, {
      entityId: props.entityId,
      tabId: currentTab,
    });

    navigateTo(url, getNavigateParams({ ...props }));
  };

  const handleGraphDoubleClick = (evt: any) => {
    if (!props.nodeCheckMode && evt?.item?.type === 'node' && evt?.item?.id) {
      if (_doesNeedConfiguration()) {
        if (_isGraphEditable()) {
          setSelectedNode(evt?.item?.getModel?.());
          openNodeConfigModal();
        }
      }
    }
  };

  // FP only function
  const onReadyToggleChange = (ready: boolean) => {
    setState({ ready });
    onSaveChanges(undefined, undefined, ready);
  };

  const getToolbarProps = () => {
    // TODO: It would give us more control in the UI to use the lastSyncedTime
    // directly rather than getting that as the readOnlyMsg from the backend.
    // Currently we can't show the correct style as defined in SYN-14364.

    // let lastSyncedTime;
    // if (props.lastSyncedTime) {
    //   lastSyncedTime = moment(props.lastSyncedTime).format(SHORT_DATE_TIME_FORMAT);
    // } else {
    //   lastSyncedTime = tn('not_started');
    // }

    const isEditable = _isGraphEditable();

    const epToolbarProps: Partial<GraphToolbarProps> = {
      onPublishPipeline,
      onTest,
      onStop,
      onStart,
      disableValidate: true,
      disableTest: false,
      showTest: false,
      pausedBy: props.pausedBy,
      onChangeGraph,

      goToName: tc('entities'),
      navigateUp: navigateToEntities,
      // lastSyncedTime is not used by the PipelineToolbar. Instead it uses the
      // readOnlyMsg which has the lsat synced time from the backend. I think it
      // would be better to explicitely use the lastSyncedTime and convert it to
      // the user's timezone.
      // lastSyncedTime: `${lastSyncedTime}`,
      readOnlyMsg: '',
    };

    const fpToolbarProps: Partial<GraphToolbarProps> = {
      fieldId,
      onChangeGraph,
      goToName: `${getEntityName(props.entityId, props.entities)}`,
      navigateUp: navigateToEntityPipeline,
      readyToggleValue: state.ready,
      onReadyToggleChange,
    };

    const allActionsDisabled = props.nodeCheckMode;

    const toolbarProps: GraphToolbarProps = {
      ...(props.isEntityPipeline ? epToolbarProps : fpToolbarProps),
      entityId,
      showSave: isEditable,
      publishProps: {},
      emptyToolbar: props.pipelineExists === false,
      onSaveChanges,
      onCreateVersion,
      onValidate,
      showValidate: false,
      disableSave: !state.haveUnsavedChanges,
      draftSelectionText: 'Approved',
      showPublishDraft: false,
      isLoading: false,
      availableVersions: getAvailableVersions(),
      loadingMessage: '',
      showSuccess: false,
      successMessage: '',
      errorTitle: props.errorTitle,
      errorMessage: props.errorMessage,
      allActionsDisabled,
      updateNodes,
    };

    if (state.lastAction === ACTIONS.SAVE) {
      if (!state.haveUnsavedChanges) {
        if (props.pipelineSaving) {
          toolbarProps.isLoading = true;
          toolbarProps.loadingMessage = tc('saving');
        } else if (props.pipelineSaved) {
          toolbarProps.showSuccess = true;
          toolbarProps.successMessage = tc('saved');
        }
      }
    } else if (state.lastAction === ACTIONS.VALIDATE) {
      if (props.pipelineValidating) {
        toolbarProps.isLoading = true;
        toolbarProps.loadingMessage = tc('validating');
      } else if (props.pipelineValidated) {
        toolbarProps.showSuccess = true;
        toolbarProps.successMessage = tn('valid_pipeline');
      }
    }

    if (props.isEntityPipeline) {
      const setDraftReadOnly = () => {
        toolbarProps.disableTest = true;
        toolbarProps.publishProps!.disabled = true;
        toolbarProps.publishProps!.tooltip = toolbarProps.readOnlyMsg;
        currentGraphRef.current.readOnly = true;
        currentGraphRef.current.readOnlyMsg = toolbarProps.readOnlyMsg as string;
      };

      const setDraftEditable = () => {
        toolbarProps.disableTest = false;
        toolbarProps.publishProps!.disabled = false;
        toolbarProps.publishProps!.tooltip = '';
        currentGraphRef.current.readOnly = false;
        currentGraphRef.current.readOnlyMsg = '';
      };

      if (isApproveOnlyGraph()) {
        if (state.draftGraphJson) {
          if (props.displayedGraph === GRAPH_STATUS.DRAFT) {
            toolbarProps.showPublishDraft = true;
            toolbarProps.disableValidate = false;
            toolbarProps.readOnlyMsg = props.pipeline.readOnlyReason;
            if (props.pipeline.draft && props.pipeline.draft.readOnly) {
              setDraftReadOnly();
            } else {
              setDraftEditable();
            }
          }
        }
      } else if (isApproveWithDraftGraph()) {
        if (props.displayedGraph === GRAPH_STATUS.DRAFT) {
          toolbarProps.showPublishDraft = true;
          toolbarProps.disableValidate = false;
          toolbarProps.readOnlyMsg = props.pipeline.draft.readOnlyReason;
          if (props.pipeline.draft.readOnly) {
            setDraftReadOnly();
          } else {
            setDraftEditable();
          }
        }
      }
      // New
      else {
        if (props.displayedGraph === GRAPH_STATUS.NEW) {
          toolbarProps.showPublishDraft = true;
          toolbarProps.disableValidate = false;
          toolbarProps.readOnlyMsg = props.pipeline.readOnlyReason;
          if (props.pipeline.readOnly) {
            setDraftReadOnly();
          } else {
            setDraftEditable();
          }
        }
      }
      if (props.displayedGraph === GRAPH_STATUS.APPROVED) {
        toolbarProps.draftSelectionText = tc('approved');
        toolbarProps.readOnlyMsg = props.pipeline.readOnlyReason;
      } else {
        if (props.pipeline.draft) {
          toolbarProps.readOnlyMsg = props.pipeline.draft.readOnlyReason;
          if (props.pipeline.draft.readOnly) {
            setDraftReadOnly();
          } else {
            setDraftEditable();
          }
        }
        toolbarProps.draftSelectionText = tc('draft');
        toolbarProps.showValidate = true;
        toolbarProps.showTest = true;
      }
    } else {
      if (props.displayedGraph === GRAPH_STATUS.APPROVED) {
        toolbarProps.draftSelectionText = tc('approved');
      } else {
        toolbarProps.draftSelectionText = tc('draft');
        toolbarProps.showValidate = true;
      }
    }

    if (isGraphEditable(props.displayedGraph)) {
      if (props.isEntityPipeline) {
        toolbarProps.showTest = false;
      }
    }

    toolbarProps.rightGroup = (
      <PipelineEditorMoreActions
        {...props}
        graphIsReadOnly={isGraphReadOnly()}
        isApproveWithDraftGraph={isApproveWithDraftGraph()}
        isDraftOnlyGraph={isDraftOnlyGraph()}
      />
    );
    return toolbarProps;
  };

  const getLoadingStatus = () => {
    let loadingMessage = tc('loading');
    let loading = false;
    const connectorOrAttributesFetching = props.isEntityPipeline
      ? props.connectorEntitiesFetching
      : props.attributeNodesFetching;
    if (
      props.pipelineFetching ||
      props.pipelineFunctionsFetching ||
      props.pipelineActionsFetching ||
      props.entitiesFetching ||
      connectorOrAttributesFetching
    ) {
      loading = true;
    }
    if (props.pipelineDeleting) {
      loading = true;
      loadingMessage = tc('deleting_pipeline');
    }
    if (props.pipelineDiscarding) {
      loading = true;
      loadingMessage = tc('discarding_pipeline');
    }
    if (props.isEntityPipeline && props.pipelineApproving) {
      loading = true;
      loadingMessage = tc('publishing_pipeline');
    }
    if (props.pipelineSaving) {
      loading = true;
      loadingMessage = tc('saving_pipeline');
    }
    if (props.pipelineCreating) {
      loading = true;
      loadingMessage = tc('creating_draft');
    }
    return { loadingMessage, loading };
  };

  const saveViewportMatrix = (matrix: number[]) => {
    const pipelineId = props.isEntityPipeline ? props.entityId : (props.fieldId as string);
    updateSyncStudioPipelineViewports(pipelineId, matrix);
  };

  const selectAllNodeCheck = () => {
    const editor = editorRef.current;
    const page = editor.getCurrentPage();
    const nodes = page.getNodes();
    nodes.forEach((node: any) => {
      editor.executeCommand(() => {
        const page = editor.getCurrentPage();
        const item = page.find(node.id);
        if (item && !UNSELECTABLE_NODES.includes(item.model.nodeType)) {
          page.update(item, {
            checkedNode: true,
          });
          props.setNodeCheck(node.id, true);
        }
      });
    });
  };

  const unselectAllNodeCheck = () => {
    const editor = editorRef.current;
    const page = editor.getCurrentPage();
    const nodes = page.getNodes();
    nodes.forEach((node: any) => {
      editor.executeCommand(() => {
        const page = editor.getCurrentPage();
        const item = page.find(node.id);
        if (item && !UNSELECTABLE_NODES.includes(item.model.nodeType)) {
          page.update(item, {
            checkedNode: false,
          });
          props.setNodeCheck(node.id, false);
        }
      });
    });
  };

  const saveFragment = (fragmentFormValues: FragmentModel) => {
    let selectedNodes: any[] = [];
    let selectedEdges: any[] = [];
    const checkValues = props.nodeCheckValues;
    const nodes = state.nodes;
    const edges = state.edges;
    const nodeIds = Object.keys(checkValues).filter((key) => checkValues[key]) || [];
    // TODO: Make this nice to just filter the nodes with edges and pass it down to save fragment
    nodes.forEach((node: any) => {
      if (nodeIds.includes(node.id)) {
        selectedNodes.push(cloneDeep(node));
      }
    });
    edges.forEach((edge: any) => {
      if (nodeIds.includes(edge.destination.nodeId) && nodeIds.includes(edge.source.nodeId)) {
        selectedEdges.push(cloneDeep(edge));
      }
    });
    props.saveFragment(
      {
        ...fragmentFormValues,
        fragment: {
          nodes: selectedNodes,
          edges: selectedEdges,
        },
      } as any,
      props.isEntityPipeline ? AppConstants.PIPELINE_CONTEXT.ENTITY : AppConstants.PIPELINE_CONTEXT.FIELD
    );
  };

  // Returns true if ok to delete node, otherwise false
  const onCustomBeforeDelete = (evt: any) => {
    // If confirmDeleteNeeded is true then abort the editor delete by returning false
    if (confirmDeleteNeeded && !props.deleteMultipleNodesModalVisible) {
      return false;
    }

    if (READONLY_NODE_TYPE.includes(evt?.model?.nodeType)) {
      return false;
    }

    if (LOOP_SUB_NODE_API_NAMES.includes(evt?.model?.metadata?.apiName) && !evt?.model?.forceDelete) {
      return false;
    }

    if (evt?.isEdge) {
      // The onCustomBeforeDelete function is not fired when edges drop due to
      // nodes being removed from the canvas. So we don't need to use the
      // forceDelete property here.
      const editor = editorRef.current;
      const page = editor.getCurrentPage();
      const nodes = page.getNodes();

      const connectingLoopStart = isConnectingLoopStartAndLoopSide(evt, nodes);
      if (connectingLoopStart) {
        return false;
      }
    }

    return true;
  };

  // Find and center the Syncari core node and set zoom to 100%
  const centerSyncariNode = () => {
    const editor = editorRef.current;
    const page = editor.getCurrentPage();
    const syncariNode: EditorNode = page
      .getNodes()
      .find((node: EditorNode) => ['CORE_ENTITY', 'CORE_ATTRIBUTE'].includes(node.model.nodeType));

    if (syncariNode) {
      updateSelectedNodeIdsQueryParam([syncariNode.id]);
      page.focus(syncariNode.id);
    }
  };

  // RENDER
  const { entityId, fieldId } = props;
  const data = getData();
  const { loadingMessage, loading } = getLoadingStatus();

  if (isEntityPipeline && props.pipelineError && !isValidEntity(props.entities, entityId) && !props.entitiesFetching) {
    return <EntityPipelineError error={props.pipelineError.errorMessage} entityId={props.entityId} />;
  }

  if (data?.nodes) {
    const toolbarProps = getToolbarProps();

    const extraProps = isEntityPipeline
      ? {
          entityId,
          renderGraph: props.renderGraph,
          className: 'entity-pipeline-editor',
        }
      : {
          fieldId,
          className: 'field-pipeline-editor',
        };

    const pipelineContext = isEntityPipeline
      ? AppConstants.PIPELINE_CONTEXT.ENTITY
      : AppConstants.PIPELINE_CONTEXT.FIELD;
    const pipelineId = (!isEntityPipeline && fieldId) || entityId;

    return (
      <RealtimePipelineContextProvider
        enabled={settings?.realtimePipeline}
        ipWhitelist={settings?.realtimeIpWhitelist}
        value={{ onSaveChanges }}>
        <Spin tip={loadingMessage} spinning={loading}>
          <>
            <GraphEditor
              {...toolbarProps}
              {...extraProps}
              data={data}
              graphContent={
                props.testResultVisible && state.selectedItemId && state.testNodeNotFoundVisible && <TestNodeNotFound />
              }
              contextPanel={getContextPanel()}
              saveViewportMatrix={saveViewportMatrix}
              pipelineViewportMatrix={props.pipelineViewportMatrix}
              graphMode={graphMode}
              hasToolbar
              toolbar={<PipelineToolbar {...toolbarProps} />}
              onGraphChange={onGraphChange}
              onAfterItemUnSelected={onAfterItemUnSelected}
              onAfterItemSelected={updateUrlWithSelectedNodes}
              onCustomBeforeDelete={onCustomBeforeDelete}
              setSelectedNode={setSelectedNode}
              onGraphDoubleClick={handleGraphDoubleClick}
              setEditor={setEditor}
              bottomGroup={<TestResultDetails pipelineId={pipelineId} pipelineContext={pipelineContext} />}
              minimapSettings={
                <Dropdown
                  overlay={
                    <Menu>
                      <Menu.Item onClick={centerSyncariNode}>{tc('go_to_syncari_node')}</Menu.Item>
                    </Menu>
                  }
                  trigger={['click']}>
                  <Icon type="setting" className="settings-btn" theme="filled" />
                </Dropdown>
              }
            />
            <FragmentModal
              createFragmentVisible={props.createFragmentVisible}
              enableNodeCheck={props.enableNodeCheck}
              showCreateFragmentModal={props.showCreateFragmentModal}
              nodeCheckValues={props.nodeCheckValues}
              clearNodeCheckValues={props.clearNodeCheckValues}
              pipelineContext={pipelineContext}
              saveFragment={saveFragment}
              selectAllNodeCheck={selectAllNodeCheck}
              unselectAllNodeCheck={unselectAllNodeCheck}
              saveFragmentErrorMessage={props.saveFragmentErrorMessage}
              fragmentSaving={props.fragmentSaving}
              resetFragmentModal={props.resetFragmentModal}
              validating={props.pipelineValidating}
              errorMessage={props.errorMessage}
              validate={onValidate}
            />
            <ValidationResultsPanel />
            {props.graphVersion?.toUpperCase() === GRAPH_STATUS.APPROVED && <PipelineErrorResultPanel />}
            <CreateGroupPanel editor={editorRef.current} />
            {isEntityPipeline && (
              <TestRunLivePanel
                onSaveChanges={onSaveChanges}
                validate={onValidate}
                pipelineValidationError={props.errorMessage}
              />
            )}
            <DeleteMultipleNodesModal editor={editorRef.current} />
            <ConfirmUngroupModal />
            <ConfirmDuplicateModal editor={editorRef.current} />
            <RealtimePipelineModal
              editor={editorRef.current}
              nodes={state.nodes}
              edges={state.edges}
              saveChanges={onSaveChanges}
              entityId={entityId}
            />
            <DisableRealtimePipelineModal entityId={entityId} saveChanges={onSaveChanges} />
            <TestRunSimulatedPanel
              pipelineId={pipelineId}
              pipelineContext={pipelineContext}
              onSaveChanges={onSaveChanges}
            />
            <TestAddUpdateSimulatedPanel
              pipelineId={pipelineId}
              pipelineContext={pipelineContext}
              validating={props.pipelineValidating}
              validate={onValidate}
              errorMessage={props.errorMessage}
            />
            <TestResultPanel
              pipelineId={pipelineId}
              pipelineContext={pipelineContext}
              errorMessage={props.errorMessage}
              validate={onValidate}
              onSaveChanges={onSaveChanges}
            />
            <Settings />

            {props.nodeConfigModalVisible && <NodeConfigModal key="node-config-modal" />}
            <CreateVersionModal entityId={entityId} />
          </>
        </Spin>
      </RealtimePipelineContextProvider>
    );
  } else {
    if (props.pipelineExists === false && isValidEntity(props.entities, entityId)) {
      return (
        <Spin tip={loadingMessage} spinning={loading}>
          <GraphEditor
            goToName={tc('entities')}
            navigateUp={navigateToEntities}
            emptyToolbar
            selectedItemId={state.selectedItemId}
            fieldId={fieldId}
            graphMode={graphMode}
            graphContent={
              <EmptyGraphContent
                className="synri-full-width-content"
                icon={<InlineSVG title="Pipeline icon" src={PipelineIcon} />}
                onActionClick={onCreateDraftClick}
                actionDisabled={props.pipelineSaving}
                actionText={tn('create_pipeline_draft')}
                actionPermission={AllPermissions.WRITE_STUDIO}>
                <span dangerouslySetInnerHTML={{ __html: tn('create_pipeline_draft_powerful') }} />
              </EmptyGraphContent>
            }
            setEditor={setEditor}
            onGraphChange={onGraphChange}
            onAfterItemUnSelected={onAfterItemUnSelected}
            setSelectedNode={setSelectedNode}
            className="field-pipeline-editor"
          />
        </Spin>
      );
    }
    return (
      <Spin tip={loadingMessage} spinning={loading} data-testid="field-pipeline-loading">
        <div className={isEntityPipeline ? 'editor-container' : 'field-pipeline-editor h-full'} />
      </Spin>
    );
  }
};

const getPipelineType = (props: PipelineEditorProps) => {
  return {
    isEntityPipeline: !props.fieldId,
    isFieldPipeline: !!props.fieldId,
  };
};

const ConnectedPipeLineEditor = connect(
  (state: RootState, props: PipelineEditorProps): PipelineEditorProps => {
    const pipelineTypes = getPipelineType(props);

    const enhancedProps = pipelineTypes.isEntityPipeline
      ? mapStateToPropsEntityPipeline(state, props)
      : mapStateToPropsFieldPipeline(state, props);

    return {
      ...enhancedProps,
      ...pipelineTypes,
      ...props,
    };
  },
  (dispatch: SyncariThunkDispatch, props: PipelineEditorProps) => {
    const pipelineTypes = getPipelineType(props);

    const enhancedProps = pipelineTypes.isEntityPipeline
      ? mapDispatchToPropsEntityPipeline(dispatch)
      : mapDispatchToPropsFieldPipeline(dispatch);

    return {
      ...enhancedProps,
      ...pipelineTypes,
      getPipeline: () => {
        const graphVersion = getPipelineDraftStatus(props.graphVersion?.toUpperCase());

        if (pipelineTypes.isEntityPipeline) {
          return enhancedProps.getPipeline(props.entityId, graphVersion);
        } else {
          // Fetch the EP so we have the settings in the state.entityPipeline.entityPipeline in the store
          enhancedProps.getEntityPipeline(props.entityId, graphVersion);
          return enhancedProps.getPipeline(props.entityId, props.fieldId as any, graphVersion);
        }
      },
    };
  }
)(PipelineEditor);

const KeyPipelineEditor = (props: PipelineEditorProps) => {
  const [reloadKey, setReloadKey] = useState(1);

  // Allow remounting the PipelineEditor component directly
  const remountComponent = useCallback(() => {
    setReloadKey(Math.random());
  }, []);

  // Unmount and remount the pipeline when the id or graph version changes
  const key = [props.entityId, props.graphVersion, props.fieldId, reloadKey].join('_');
  return <ConnectedPipeLineEditor key={key} {...props} remountComponent={remountComponent} />;
};

export default KeyPipelineEditor;
