// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Dropdown, Icon, Menu, Modal, Spin } from 'antd';
import { cloneDeep, delay, filter, first, isEmpty, isEqual, keyBy, uniqueId } from 'lodash';
import { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { getConnectors, getConnectorsMetadata, setModalMode, showConnectorModal } from 'actions/connectorActions';
import { ReactComponent as InfoSign } from 'assets/icons/info-icon-solid.svg';
import SynapseIcon from 'assets/syncari-icons/grayscale/synapse-studio.svg';
import EmptyGraphContent from 'components/EmptyGraphContent';
import { GraphEditor } from 'components/GraphEditor';
import { GRAPH_MODE } from 'components/GraphPage';
import InlineSVG from 'components/icons/InlineSvg';
import { UserHasPermission } from 'hooks/useUserHasPermission';
import ConnectorWizardModal from 'pages/connector/ConnectorWizardModal';
import { Error403 } from 'pages/errors/Error403';
import { DataCardError } from 'pages/insights-studio/components/data-card-error/DataCardError';
import { getDervConnectors } from 'selectors/connectorSelectors';
import { getEntities } from 'store/entity/actions';
import { RootState } from 'store/types';
import { getUserPreference, setUserPreference, updateSyncStudioPipelineViewports } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import { setWindowTitle } from 'utils/AppUtil';
import { findConnectorMetadata } from 'utils/ConnectorMetadataUtil';
import {
  arrangeNodes,
  getConnectorGraph,
  getDefaultSyncariNode,
  getUserConnectors,
  SYNCARI_CENTER_ID,
} from 'utils/ConnectorUtil';
import { applyUserPref, extractNodesFromGraph, extractUserPref, generateNewId, generateNodeIds } from 'utils/GraphUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { updateGraph } from 'utils/PipelineUtil';
import { isGuidedDemoAccount, isGuidedDemoConnector } from 'utils/GuidedDemo';

import { ConnectorDefaultMappings } from './ConnectorDefaultMappings';
import { ConnectorDetails } from './ConnectorDetails';
import { ConnectorDetailsContextProvider } from './ConnectorDetailsContext';
import ConnectorGraphEmptyPage from './ConnectorGraphEmptyPage';
import ConnectorPanel from './ConnectorPanel';
import { WebhookLogsModal } from './custom-synapse/webhook/WebhookLogsModal';
import './ConnectorEditor.less';

const tn = tNamespaced('ConnectorEditor');

export const CONNECTOR_EDITOR_VIEWPORT = 'CONNECTOR_EDITOR_VIEWPORT';

class ConnectorEditor extends Component {
  state = {
    selectedNode: {},
    selectionInitialized: false,
    nodes: null,
    edges: null,
    emptyGraphVisible: true,
  };

  componentDidMount() {
    this.props.getUserPreference();
    this.props.getConnectors();
    if (!this.props.connectorMetadata) {
      this.props.getConnectorsMetadata();
    }
    const { connectorMetadata } = this.props;

    if (connectorMetadata) {
      this.setState({
        connectorMetadata: this._preProcessConnectorMetadata(),
      });
    }

    if (!isEmpty(this.props.connectors)) {
      this.setState({
        emptyGraphVisible: getUserConnectors(this.props.connectors) < 1,
      });
    }

    setWindowTitle(tn('title'));
  }

  _preProcessConnectorMetadata = () => {
    let newMeta = generateNodeIds(cloneDeep(this.props.connectorMetadata), {
      blankDraggable: this.state.emptyGraphVisible,
    });

    return filter(newMeta, (meta) => {
      const isSynapse = meta.type === AppConstants.CONNECTOR_METADATA_TYPE.SYNAPSE;
      return isSynapse && (!this.props.isGuidedDemo || isGuidedDemoConnector(meta));
    });
  };

  componentDidUpdate(prevProps, prevStates) {
    // Initialize our connectorMetadata
    if (!isEqual(this.props.connectorMetadata, prevProps.connectorMetadata)) {
      this.setState({
        connectorMetadata: this._preProcessConnectorMetadata(),
      });
    }

    // Update our connector drag state when the graph switch from empty.
    if (this.state.emptyGraphVisible !== prevStates.emptyGraphVisible) {
      this.setState({
        connectorMetadata: this._preProcessConnectorMetadata(),
      });
    }

    // Intialize our node state with new nodes
    if (this.props.fetchingConnectors === false && prevProps.fetchingConnectors === true) {
      this.setState({
        nodes: null,
        edges: null,
      });
    }

    // A node is needed to be removed
    if (this.props.nodeIdToRemove !== prevProps.nodeIdToRemove) {
      this.removeNode(this.props.nodeIdToRemove);
    }

    // Connector was delete with error
    if (
      this.props.connectorDeleting === false &&
      prevProps.connectorDeleting === true &&
      this.props.connectorDeleteErrorMessage
    ) {
      Modal.error({
        title: tn('delete_connector_title'),
        content: this.props.connectorDeleteErrorMessage,
      });
    }

    // Connector activate error
    if (
      this.props.connectorActivating === false &&
      prevProps.connectorActivating === true &&
      this.props.connectorActivateErrorMessage
    ) {
      if (!this.props.connectorModalVisible) {
        Modal.error({
          title: tn('activate_connector_title'),
          content: this.props.connectorActivateErrorMessage,
        });
      }
    }

    // Connector deactivate error
    if (
      this.props.connectorDeactivating === false &&
      prevProps.connectorDeactivating === true &&
      this.props.connectorDeactivateErrorMessage
    ) {
      if (!this.props.connectorModalVisible) {
        Modal.error({
          title: tn('deactivate_connector_title'),
          content: this.props.connectorDeactivateErrorMessage,
        });
      }
    }

    if (
      this.props.connectorTesting === false &&
      prevProps.connectorTesting === true &&
      this.props.connectorTestErrorMessage
    ) {
      if (!this.props.connectorModalVisible) {
        Modal.error({
          title: tn('test_connector_title'),
          content: this.props.connectorTestErrorMessage,
        });
      }
    }

    // Connector just got created
    if (this.props.connectorCreating === false && prevProps.connectorCreating === true) {
      this._saveNewNodePref();
    }

    // An add node event happens
    if (this.props.addConnectorNode !== prevProps.addConnectorNode) {
      this.addConnectorNode(this.props.addConnectorNode);
    }

    if (this.props.connectors !== prevProps.connectors) {
      this.setState({
        emptyGraphVisible: getUserConnectors(this.props.connectors) < 1,
      });
    }

    if (this.props.connectorActivated !== prevProps.connectorActivated) {
      if (this.props.connectorActivated === true) {
        this.props.getConnectors();
      }
    }

    // Connector successfully activated
    if (this.props.activatedConnectorId !== prevProps.activatedConnectorId) {
      this.updateConnectorStatus(this.props.activatedConnectorId, AppConstants.CONNECTOR_STATUS.ACTIVE);
      this.props.getEntities();
    }

    // Show a persistent error message when there is an error saving the user preference
    if (
      this.props.preferenceErrorMessage &&
      this.props.preferenceSaving === false &&
      prevProps.preferenceSaving === true
    ) {
      Modal.error({
        title: tc('user_preference_title'),
        content: this.props.preferenceErrorMessage,
      });
    }
  }

  updateConnectorStatus = (connectorId, status) => {
    const editor = this.editor;
    editor?.executeCommand(() => {
      const page = editor.getCurrentPage();
      const item = page.find(connectorId);
      if (item) {
        page.update(item, {
          status,
        });
      }
    });
  };

  removeNode = (nodeId) => {
    const page = this.editor.getCurrentPage();
    const node = page.find(nodeId);
    if (node) {
      page.getGraph().remove(node);
      this.onGraphChange({
        action: AppConstants.GRAPH_ACTION.REMOVE,
        item: {
          ...node,
          type: AppConstants.GRAPH_ITEM_TYPE.NODE,
        },
      });
    }
  };

  addConnectorNode = (addModel) => {
    // Add our syncari node if first node
    if (this.state.emptyGraphVisible) {
      this.editor.executeCommand(AppConstants.NODE_ACTION.ADD, {
        type: AppConstants.GRAPH_ITEM_TYPE.NODE,
        addModel: getDefaultSyncariNode(),
      });
      let pref = this.props.connectorGraphUserPref ? cloneDeep(this.props.connectorGraphUserPref) : { nodes: {} };
      const { id, x, y } = getDefaultSyncariNode();
      if (pref?.nodes && !pref?.nodes[id]) {
        pref.nodes = {
          ...pref.nodes,
          [id]: { id, x, y },
        };
        this._savePref(pref, true);
      }
    }

    this.editor.executeCommand(AppConstants.NODE_ACTION.ADD, {
      type: AppConstants.GRAPH_ITEM_TYPE.NODE,
      addModel,
    });
    this.setState({
      emptyGraphVisible: false,
    });
    this.addSyanpse(addModel);
  };

  _savePref = (pref, refreshPref) => {
    pref = this.addSyncariNodePref(pref);
    this.props.setUserPreference(
      AppConstants.USER_PREF.CONNECTOR_GRAPH,
      {
        ...pref,
        instanceId: this.props.instanceId,
      },
      refreshPref
    );
  };

  _updateKeys = (items) => {
    let item = first(items);
    if (item) {
      item.key = uniqueId();
    }
    return items;
  };

  _saveNewNodePref = () => {
    if (!isEmpty(this.state.newNodePref)) {
      const connectorId = this.props.createdConnectorId;
      let pref = cloneDeep(this.props.connectorGraphUserPref);
      let nodes = pref.nodes;
      if (nodes && nodes[this.state.newNodePref.id]) {
        delete nodes[this.state.newNodePref.id];
      }
      pref = {
        ...pref,
        nodes: {
          ...pref.nodes,
          [connectorId]: {
            ...this.state.newNodePref,
            id: connectorId,
          },
        },
      };
      this.setState({
        newNodePref: {},
      });
      this._savePref(pref, true);
    }
  };

  _getData = () => {
    let { nodes, edges } = this.state;

    // Wait to render the nodes and edges until the connector metadata has
    // loaded so we can properly show the Draft/Published tags for custom
    // synapses.
    if (isEmpty(this.state.connectorMetadata)) {
      return;
    }

    if (!nodes) {
      const { nodes: newNodes, edges: newEdges } = getConnectorGraph(
        this.props.connectors,
        this.state.connectorMetadata
      );
      if (newNodes) {
        nodes = this._updateKeys(cloneDeep(newNodes));
        edges = this._updateKeys(cloneDeep(newEdges));
      }
    }

    if (isEmpty(nodes)) {
      return;
    }

    if (nodes?.length > 0) {
      if (this.props.connectorGraphUserPref) {
        applyUserPref(nodes, [], this.props.connectorGraphUserPref);
      } else {
        nodes = arrangeNodes(nodes, this.editor);
      }
    }

    if (!isEmpty(nodes) && !this.state.nodes) {
      this.setDelayedState({
        nodes,
        edges,
      });
    }

    return {
      nodes,
      edges,
    };
  };

  // TODO: This is just a workaround. Need to fix the setState to be removed out of the
  // render and move all the states in redux
  setDelayedState = (state) => delay(() => this.setState(state));

  setSelectedNode = (node) => {};

  _getContextPanel = () => {
    if (this.props.fetchingConnectorsMetadata && isEmpty(this.state.connectorMetadata)) {
      return (
        <div className="center-spacer">
          <Spin tip={tn('loading_connectors')} spinning />
        </div>
      );
    }
    return <ConnectorPanel connectorMetadata={this.state.connectorMetadata} />;
  };

  setEditor = (editor) => {
    this.editor = editor;
    this.setState({
      editorReady: true,
    });
  };

  shouldSavePreference(evt) {
    if (evt?.updateModel?.color || evt?.updateModel?.zIndex) {
      return false;
    }
    if (evt.action === AppConstants.NODE_ACTION.CHANGE_DATA) {
      return false;
    }
    return true;
  }

  addSyncariNodePref(pref) {
    if (pref.nodes && !pref?.nodes?.[SYNCARI_CENTER_ID]) {
      const { x, y, id } = getDefaultSyncariNode();
      pref.nodes[SYNCARI_CENTER_ID] = { id, x, y };
    }
    return pref;
  }

  onGraphChange = (evt) => {
    if (this.shouldSavePreference(evt)) {
      let nodes = this.state.nodes || [];
      let edges = this.state.edges || [];

      const graph = updateGraph({ nodes, edges, event: evt });
      nodes = graph.nodes;
      edges = graph.edges;
      this.setState({
        nodes: cloneDeep(nodes),
        edges: cloneDeep(edges),
      });

      // Moving forward, we should use the graphs data map to get the current positions
      // of the nodes and edges
      this._savePref(extractUserPref(extractNodesFromGraph(evt.item?.dataMap)), true);
    }

    if (evt.item?.isNode) {
      switch (evt.action) {
        case AppConstants.NODE_ACTION.ADD:
          this.addSyanpse(evt.item.getModel());
          break;
        default:
          break;
      }
    }
  };

  addSyanpse = (model) => {
    if (model) {
      const meta = findConnectorMetadata(model.configId, this.state.connectorMetadata);
      this.props.setModalMode(AppConstants.MODAL_MODE.ADD, cloneDeep(meta));
      this.setState({
        newNodePref: {
          id: model.id,
          x: String(model.x),
          y: String(model.y),
        },
      });
      this.props.showConnectorModal();
    }

    const connectorMetadata = generateNewId(model.id, this.state.connectorMetadata);
    if (connectorMetadata !== this.state.connectorMetadata) {
      this.setState({
        connectorMetadata,
      });
    }
  };

  _getLoadingStatus = () => {
    let loadingMessage = tc('loading');
    let loading = false;

    // check for the metadata as well
    if (
      (isEmpty(this.props.connectors) || isEmpty(this.props.connectorMetadata)) &&
      (this.props.fetchingConnectors || this.props.fetchingConnectorsMetadata)
    ) {
      loading = true;
      loadingMessage = tn('loading_connectors');
    } else if (this.props.userPreferenceFetching) {
      loading = this.props.userPreferenceFetching;
      loadingMessage = tn('loading_user_preferences');
    }
    return { loadingMessage, loading };
  };

  hoverNodeBeforeShowAnchor = (evt) => {
    evt.cancel = true;
  };

  dragEdgeBeforeShowAnchor = (evt) => {
    if (evt.dragEndPointType === 'target') {
      if (evt.edge?.target?.id !== evt.target.id) {
        evt.cancel = true;
      }
    }
    if (evt.dragEndPointType === 'source') {
      if (evt.edge?.source?.id !== evt.source.id) {
        evt.cancel = true;
      }
    }
  };

  saveViewportMatrix = (matrix: number[]) => {
    updateSyncStudioPipelineViewports(CONNECTOR_EDITOR_VIEWPORT, matrix);
  };

  autoArrange = () => {
    if (!this.props.connectorGraphUserPref || !this.state.nodes) {
      return;
    }

    const nodes = arrangeNodes(this.state.nodes, this.editor);
    this.setState({
      // Update the keys to flag our editor to update the whole graph
      nodes: this._updateKeys(cloneDeep(nodes)),
    });

    const nodePref = keyBy(
      nodes.map(({ x, y, id }) => ({
        id,
        x,
        y,
      })),
      (nPref) => nPref.id
    );
    this._savePref(
      {
        ...this.props.connectorGraphUserPref,
        nodes: nodePref,
      },
      true
    );
  };

  getMinimapSettings = () => (
    <Dropdown
      overlay={
        <Menu>
          <Menu.Item onClick={this.autoArrange}>{tn('auto_arrange_synapse')}</Menu.Item>
        </Menu>
      }
      trigger={['click']}
    >
      <Icon type="setting" className="settings-btn" theme="filled" />
    </Dropdown>
  );

  render() {
    const contextPanel = this._getContextPanel();
    const errorMessage = this.props.connectorsMetadataError?.errorMessage || this.props.connectorsError?.errorMessage;

    const data = this._getData();
    const { loadingMessage, loading } = this._getLoadingStatus();
    return (
      <UserHasPermission>
        {(userHasPermission) =>
          userHasPermission(AllPermissions.READ_CONNECTOR) === false ? (
            <Error403 />
          ) : (
            <Spin tip={loadingMessage} spinning={loading} classname="connector-editor">
              {errorMessage ? (
                <EmptyGraphContent
                  className="synri-full-width-content"
                  icon={<InlineSVG title="Pipeline icon" src={SynapseIcon} />}
                  onActionClick={() => {}}
                >
                  <DataCardError
                    error={{ title: tn('connections_loading_error'), body: tc('unexpected_error') }}
                    tooltip={errorMessage}
                    icon={<InfoSign />}
                  />
                </EmptyGraphContent>
              ) : (
                <ConnectorDetailsContextProvider>
                  {loading ? (
                    <div className="synri-connector-editor-container" />
                  ) : (
                    <GraphEditor
                      className="synri-connector-editor"
                      contextPanel={contextPanel}
                      data={data}
                      // Unsaved connector should be allowed to be deleted programatically.
                      // Saved connectors can only be deleted through the kebab menu behind
                      // a confirmation. The graph will be reloaded after its deleted.
                      onCustomBeforeDelete={(evt) => evt.model?.status === AppConstants.CONNECTOR_STATUS.NEW}
                      dragEdgeBeforeShowAnchor={this.dragEdgeBeforeShowAnchor}
                      emptyGraphPage={this.state.emptyGraphVisible ? <ConnectorGraphEmptyPage /> : null}
                      hoverNodeBeforeShowAnchor={this.hoverNodeBeforeShowAnchor}
                      graphMode={GRAPH_MODE.UPDATE_ONLY_CONNECTOR}
                      onGraphChange={this.onGraphChange}
                      saveViewportMatrix={this.saveViewportMatrix}
                      pipelineViewportMatrix={
                        this.props.syncStudioPrefs?.pipelineViewports?.[CONNECTOR_EDITOR_VIEWPORT]
                      }
                      renderGraph={this.props.renderGraph}
                      selectNodeEdges={false}
                      setEditor={this.setEditor}
                      setSelectedNode={this.setSelectedNode}
                      minimapSettings={this.getMinimapSettings()}
                      hasToolbar
                    />
                  )}
                  {this.props.connectorModalVisible && <ConnectorWizardModal key="connector-wizard-modal" />}
                  {this.props.webhookLogsModalVisible && <WebhookLogsModal key="webhook-logs-modal" />}
                  <ConnectorDetails />
                  <ConnectorDefaultMappings />
                </ConnectorDetailsContextProvider>
              )}
            </Spin>
          )
        }
      </UserHasPermission>
    );
  }
}

const mapStateToProps = (state: RootState, props) => ({
  connectors: getDervConnectors(state, props),
  connectorMetadata: state.connector.connectorsMetadata,
  fetchingConnectorsMetadata: state.connector.fetchingConnectorsMetadata,
  connectorsMetadataError: state.connector.connectorsMetadataError,
  connectorsError: state.connector.connectorsError,
  fetchingConnectors: state.connector.fetchingConnectors,
  connections: state.entity.connections,
  fetchingEntities: state.entity.fetchingEntities,
  connectorGraphUserPref: state.user.userPref.connectorGraph,
  syncStudioPrefs: state.user.userPref.syncStudio,
  userPreferenceFetching: state.user.userPref.userPreferenceFetching,
  nodeIdToRemove: state.connector.nodeIdToRemove,

  connectorDeleting: state.connector.connectorDeleting,
  connectorDeleteErrorMessage: state.connector.connectorDeleteErrorMessage,
  connectorActivating: state.connector.connectorActivating,
  connectorActivateErrorMessage: state.connector.connectorActivateErrorMessage,
  connectorDeactivating: state.connector.connectorDeactivating,
  connectorDeactivateErrorMessage: state.connector.connectorDeactivateErrorMessage,
  connectorTesting: state.connector.connectorTesting,
  connectorTestErrorMessage: state.connector.connectorTestErrorMessage,
  connectorModalVisible: state.connector.connectorModalVisible,
  webhookLogsModalVisible: state.connector.webhookLogsModalVisible,

  activatedConnectorId: state.connector.activatedConnectorId,

  connectorCreating: state.connector.connectorCreating,
  createdConnectorId: state.connector.createdConnectorId,
  addConnectorNode: state.connector.addConnectorNode,
  instanceId: state.user.currentInstanceNextEdgeId,
  isGuidedDemo: isGuidedDemoAccount(state.user.email),
  preferenceErrorMessage: state.user.preferenceErrorMessage,
  preferenceSaving: state.user.preferenceSaving,
});

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      getConnectors,
      getConnectorsMetadata,
      getEntities,
      setUserPreference,
      getUserPreference,
      showConnectorModal,
      setModalMode,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(ConnectorEditor);
