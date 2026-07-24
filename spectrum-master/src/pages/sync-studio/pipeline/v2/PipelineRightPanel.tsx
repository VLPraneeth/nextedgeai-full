import { Icon, Tooltip } from 'antd';
import ObjectID from 'bson-objectid';
import { each, isEmpty, map, toLower } from 'lodash';
import { memo, useCallback, useMemo, useRef } from 'react';

import NoConfigurationIcon from 'assets/images/no-options.svg';
import RefreshIcon from 'assets/images/refresh-icon.svg';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { GRAPH_MODE } from 'components/GraphPage';
import { default as SIcon } from 'components/icons/Icon';
import { getIconFromPath } from 'components/icons/Icons';
import { GraphStatus } from 'components/PipelineToolbar';
import EntityPipelinePanel from 'pages/sync-studio/entity-pipeline/EntityPipelinePanel';
import EntityEditorEntityPanel from 'pages/sync-studio/entity/EntityEditorEntityPanel';
import FieldPipelinePanel from 'pages/sync-studio/field-pipeline/FieldPipelinePanel';
import GroupNodePanel from 'pages/sync-studio/GroupNodePanel';
import { MultipleNodesPanel } from 'pages/sync-studio/node-grouping/MultipleNodesPanel';
import NodePanel from 'pages/sync-studio/NodePanel';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { generateNodeIds, getNodeShape } from 'utils/GraphUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { doesNeedConfiguration, getNodeConfig } from 'utils/NodeConfigUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { navigateToGraphVersion, transformAttributeNodes } from 'utils/PipelineUtil';
import { getUrlListItemName } from 'utils/UrlUtil';

import '@xyflow/react/dist/style.css';
import { useNodeMetadataDisabled, useSelectedNodes, useUpdateSelectedNodeIdsQueryParam } from '../PipelineEditor.hooks';
import { PipelineSettings, usePipelineSettings } from '../settings/Settings.hooks';
import { usePipelineEditor } from './context/PipelineEditorV2.context';
import './PipelineEditorV2.scss';

const { GRAPH_STATUS } = AppConstants;

const tn = tNamespaced('PipelineEditor');

export const PipelineRightPanel = memo((props: any) => {
  const editorRef = useRef<any>(null);
  const currentGraphRef = useRef({
    readOnly: false,
    readOnlyMsg: '',
  });
  const { selectedNodeIds } = useSelectedNodes();
  const { isSettingsEnabled } = usePipelineSettings();
  const { getNodeDisabledMetadata } = useNodeMetadataDisabled();
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();
  const { selectedGraphNode } = usePipelineEditor();

  const isApproveWithDraftGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft !== null;
  }, [props]);

  const isApproveOnlyGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft === null;
  }, [props]);

  const shouldShowNodePanel = () => {
    // Don't render the right panel for node configuration. We can propbably
    // remove all of this once we're settled on the modal configuration.

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

    if (props.selectedNode?.nodeType) {
      return nodePanelSupported.indexOf(props.selectedNode.nodeType) !== -1;
    }

    if (props.selectedNode?.metadata?.nodeType) {
      return nodePanelSupported.indexOf(props.selectedNode.metadata.nodeType) !== -1;
    }

    return false;
  };

  const scopeMatch = props.isEntityPipeline ? AppConstants.SCOPE.ENTITY : AppConstants.SCOPE.ATTRIBUTE;

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

  const _isGraphEditable = () => {
    const graphStatusIsEditable =
      props?.graphVersion && [GRAPH_STATUS.NEW, GRAPH_STATUS.DRAFT].includes(props.graphVersion.toUpperCase() as any);

    const graphModeIsEditable = graphMode === GRAPH_MODE.DEFAULT || graphMode === GRAPH_MODE.DRAG_SELECT;
    return graphStatusIsEditable && graphModeIsEditable;
  };

  const _getNodeConfig = () => {
    return getNodeConfig(props.selectedNode, {
      functions: props.pipelineFunctions,
      attributeNodes: props.attributeNodes,
      actions: props.pipelineActions,
      connectorEntities: props.connectorEntities,
    });
  };

  const _doesNeedConfiguration = (nodeConfig?: any) => {
    let config = nodeConfig;
    if (!config) {
      config = _getNodeConfig();
    }

    return doesNeedConfiguration(config);
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

    const node: any = {
      metadata: selectedGraphNode?.data.fullNode,
      ...selectedGraphNode?.position,
      ...selectedGraphNode?.data.extraData,
    };
    return (
      <EntityEditorEntityPanel
        key={entityId}
        entityId={entityId}
        entityName={props.entitySchema.displayName}
        graphVersion={props.graphVersion as GraphStatus}
        editable={_isGraphEditable()}
        actions={_getNodeConfigActions()}
        node={node}
        onClose={() => {
          props.setSelectedGraphNode(null);
        }}
      />
    );
  };

  // EP only function
  const isGraphReadOnly = () => {
    return currentGraphRef.current.readOnly && currentGraphRef.current.readOnlyMsg.length > 0;
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

  const functions = useMemo(() => {
    const showLoopFunction = isSettingsEnabled(PipelineSettings.simpleLoops);

    let funcs: any = [];
    // if (state.pipelineFunctions.length <= 0 && props.pipelineFunctions.length > 0) {
    const filteredFunctions = props.pipelineFunctions.filter(
      (func: any) => showLoopFunction || func.name !== AppConstants.LOOP_FUNCTION_NAME
    );
    const pipelineFunctions = generateNodeIds(filteredFunctions);
    //   if (props.isFieldPipeline) {
    //     setState({
    //       pipelineFunctions,
    //     });
    //   }

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
    //   setState({ pipelineFunctions: funcs });
    // }
    // return state.pipelineFunctions;
    return funcs;
  }, [isSettingsEnabled, props.isEntityPipeline, props.pipelineFunctions]);

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

  const onCreateFragment = () => {
    props.enableNodeCheck();
    props.showCreateFragmentModal();
  };

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

  const onSwitchToDraftClick = () => _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT, replace: false });

  const displayCreateDraft = isApproveOnlyGraph();
  const displaySwitchDraft = isApproveWithDraftGraph() && props.displayedGraph === GRAPH_STATUS.APPROVED;

  if (selectedNodeIds.length > 1) {
    return <MultipleNodesPanel editor={editorRef.current} scope={scopeMatch} />;
  }

  if (shouldShowNodePanel()) {
    const selectedNode = props.selectedNode;
    if (selectedNode?.nodeType === AppConstants.NODE_TYPE.CUSTOM_GROUP) {
      return <GroupNodePanel selectedNode={selectedNode} isEditable={_isGraphEditable()} />;
    }

    const config = _getNodeConfig();
    if (isEmpty(config)) {
      return (
        <EmptyGraphPanel
          icon={NoConfigurationIcon}
          // onActionClick={removeSelectedNode}
          actionText={tn('remove_node')}>
          <span>{tn('node_no_longer_valid')}</span>
        </EmptyGraphPanel>
      );
    } else if (_doesNeedConfiguration(config)) {
      const node: any = {
        metadata: selectedGraphNode?.data.fullNode,
        ...selectedGraphNode?.position,
        ...selectedGraphNode?.data.extraData,
        id: selectedGraphNode?.data.fullNode.id,
        configuration: {
          configId: selectedGraphNode?.data.fullNode.configuration.configId,
        },
        configId: selectedGraphNode?.data.fullNode.configuration.configId,
        label: selectedGraphNode?.data.fullNode.name,
      };
      return (
        // Need to implement the actions once we implement the state
        <NodePanel
          key={`node-panel-${config.name}`}
          title={props.selectedNode.label}
          node={node}
          // node={props.selectedNode}
          // changeKey={state.changeKey}
          // actions={_getNodeActions() as any}
          onClose={() => {
            props.setSelectedGraphNode(null);
          }}
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
        // onActionClick={onCreateDraftClick}
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
      <EmptyGraphPanel
        // onActionClick={onSwitchToDraftClick}
        actionText={tn('switch_draft_action')}>
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
          // title: state.entityName,
          connectors: getConnectorEntities(),
          getFragmentStatus: props.getFragmentStatus,
        }
      : {
          getEditor: () => editorRef.current,
          // data: getData(),
          title: entityName,
          sourceFields,
          sinkFields,
        };

    return (
      <div>
        <PanelComponent
          {...panelProps}
          functions={functions}
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
      </div>
    );
  } else if (props.isFieldPipeline && displaySwitchDraft) {
    return (
      <EmptyGraphPanel onActionClick={onSwitchToDraftClick} actionText={tn('switch_draft_action')}>
        <span>{tn('switch_to_draft_with_changes')}</span>
      </EmptyGraphPanel>
    );
  } else {
    return null;
  }
});
