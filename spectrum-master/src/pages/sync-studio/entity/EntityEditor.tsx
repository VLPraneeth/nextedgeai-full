//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, useLocation } from '@reach/router';
import { Icon, message, Modal, Spin } from 'antd';
import { cloneDeep, each, find, isEmpty, isNull, isUndefined } from 'lodash';
import { useCallback, useEffect, useRef, useState } from 'react';

import { getConnectors } from 'actions/connectorActions';
import { getSyncStatuses, showNodeConfigModal } from 'actions/entityPipelineActions';
import ConnectorIcon from 'assets/icons/connector.svg';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { GraphEditor } from 'components/GraphEditor';
import { GRAPH_MODE } from 'components/GraphPage';
import { DISTANCE_ICON } from 'components/icons/Icons';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import usePreviousValue from 'hooks/usePreviousValue';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import ConnectorEntityModal from 'pages/sync-studio/entity/ConnectorEntityModal';
import QuickStartPublish from 'pages/sync-studio/entity/quick-start/QuickStartPublish';
import FastMapper from 'pages/sync-studio/fast-mapper';
import { getDervConnectors } from 'selectors/connectorSelectors';
import { getEntitiesFetching, selectEntity, selectEntityGraph } from 'selectors/entitySelectors';
import { deleteEntity, getEntities } from 'store/entity/actions';
import { showFastMapper } from 'store/fast-mapper/slice';
import { useGetImportedFoldersListQuery } from 'store/imported-files/api';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { selectCurrentInstanceId } from 'store/user/selectors';
import { getUserPreference, setUserPreference, updateSyncStudioPipelineViewports } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import { RoleGroup } from 'utils/CapConstants';
import { firstConnectorActivating, getConnectorsForPanel, haveActiveConnectors } from 'utils/ConnectorUtil';
import { applyDefaults, areAllUnmappedEntities, getEntitiesForGraph, getEntityEdgesForGraph } from 'utils/EntityUtil';
import { FeatureFlagName, isFeatureEnabled } from 'utils/FeatureFlagUtil';
import { applyUserPref, extractUserPref, updateKeys } from 'utils/GraphUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { getDefaultGraphVersion, updateGraph } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

import EntityEditorConnectorPanel from './EntityEditorConnectorPanel';
import EntityPanel from './EntityPanel';
import { PipelineDetails } from './PipelineDetails';
import { SyncStudioRootTabs } from './SyncStudioRootTabs';
import SyncStudioTabsPanel, { SyncStudioPanelKeys } from './SyncStudioTabsPanel';

import './EntityEditor.scss';

const tn = tNamespaced('EntityEditor');

const { FETCH_STATUS } = AppConstants;

const ENTITY_EDITOR_VIEWPORT = 'ENTITY_EDITOR_VIEWPORT';

export interface EntityEditorProps {
  tabId: string;
  entityId: string;
}

const EntityEditor = ({ tabId, entityId }: EntityEditorProps) => {
  const dispatch = useDispatch();

  const [nodes, setNodes] = useState<any[] | null>(null);
  const [edges, setEdges] = useState<any[] | null>(null);
  const [graphData, setGraphDataState] = useState<{ nodes: any[]; edges: any[] } | null>(null);

  const location = useLocation();

  const editorRef = useRef<any>(null);

  // setEditor is used by the GraphEditor to set the G6 Editor object. The
  // editor can be used to get the currentPage, execute commands on the editor, etc.
  const setEditor = (editor: any) => {
    editorRef.current = editor;
  };

  const instanceId = useSelector(selectCurrentInstanceId);
  const connectors = useSelector(getDervConnectors);
  const { userCan } = useUserRolesForCurrentInstance();
  const { userHasPermission } = useUserHasPermission();

  const entities = useSelector(selectEntityGraph);
  const entitiesFetching = useSelector(getEntitiesFetching);
  const { userPref, preferenceErrorMessage, preferenceSaving } = useSelector((state) => state.user);
  const entityGraphUserPref = userPref.entityGraph;
  const shouldTabsPanelRender =
    isFeatureEnabled(FeatureFlagName.QUICK_START) ||
    userCan(RoleGroup.ADMIN_SUPER_GHOST) ||
    userHasPermission(AllPermissions.WRITE_STUDIO);

  const { entitySyncStatuses, getSyncStatusesStatus } = useSelector((state) => state.entityPipeline);
  const viewingDetails = tabId === 'details';

  const { connections, connectorEntityModalVisible, quickStartPublishVisible, quickStartPublishId } = useSelector(
    (state) => state.entity
  );

  const { data: folders } = useGetImportedFoldersListQuery();

  useMountUnmountEffect(() => {
    dispatch(getUserPreference());
    dispatch(getConnectors());
    if (!entitiesFetching && !entities?.length) {
      dispatch(getEntities());
    }
    dispatch(getSyncStatuses());

    return () => {
      dispatch(showNodeConfigModal(false));
      dispatch(showFastMapper({ visible: false, entityId }));
    };
  });

  const saveUserPref = useCallback(
    (pref: any) =>
      dispatch(
        setUserPreference(
          AppConstants.USER_PREF.ENTITY_GRAPH,
          {
            ...pref,
            instanceId,
          },
          true
        )
      ),
    [dispatch, instanceId]
  );

  const setGraphData = useCallback(() => {
    // Do not render anything util the we have the user pref
    if (isUndefined(entityGraphUserPref) || !entitySyncStatuses) {
      return;
    }

    const nodes = getEntitiesForGraph(entities);
    const edges = getEntityEdgesForGraph(connections);

    if (isEmpty(nodes)) {
      return;
    }

    let pref;
    if (isNull(entityGraphUserPref)) {
      pref = applyDefaults(entities, connections);
      // Save the default pref
      saveUserPref(pref);
    } else {
      pref = entityGraphUserPref;
    }
    applyUserPref(nodes, edges, pref);

    const updatedNodes = updateKeys(nodes).map((node: any) => ({
      ...node,
      lastSyncTime: find(entitySyncStatuses, { syncariEntityId: node.id })?.lastSyncTime,
    }));

    setNodes(updatedNodes);
    setEdges(edges);
    setGraphDataState({
      nodes: updatedNodes,
      edges,
    });
  }, [connections, entities, entityGraphUserPref, entitySyncStatuses, saveUserPref]);

  const missingEntitySyncStatuses = !entitySyncStatuses;
  const previousConnections = usePreviousValue(connections);
  const previousEntities = usePreviousValue(entities);

  useEffect(() => {
    if (previousConnections !== connections || previousEntities !== entities || missingEntitySyncStatuses) {
      setGraphData();
    }
  }, [connections, entities, missingEntitySyncStatuses, previousConnections, previousEntities, setGraphData]);

  // Show user preference saving error if we're not actively saving
  useEffect(() => {
    if (preferenceErrorMessage && !preferenceSaving) {
      Modal.error({
        title: tc('user_preference_title'),
        content: preferenceErrorMessage,
      });
    }
  }, [preferenceErrorMessage, preferenceSaving]);

  const selectedEntity = useSelector((state) => selectEntity(state, { entityId }));

  // TODO: Type selectedEntity
  const setSelectedEntity = (newSelectedEntity: any) => {
    if (newSelectedEntity.nodeId === selectedEntity?.id) {
      return;
    }

    // If the user clicks on an edge then remove the selected entity. If the
    // user selects a node then set that as the selectedNodeId in the url
    const url = newSelectedEntity.edgeType
      ? replaceToken(RouteConstants.ENTITIES, { tabId })
      : replaceToken(RouteConstants.ENTITY, { entityId: newSelectedEntity.nodeId, tabId });
    navigate(url);
  };

  const onEntityPanelClose = () => {
    const page = editorRef.current?.getCurrentPage();
    page?.clearSelected();
    navigate(replaceToken(RouteConstants.ENTITIES, { tabId }));
  };

  const getGraphVersion = () => {
    let pipelineStatus = selectedEntity?.metadata?.pipelineStatus;

    if (!pipelineStatus && editorRef.current) {
      const pages = editorRef.current.getComponentsByType('page');
      each(pages, (page) => {
        const graph = page.getGraph();

        if (graph) {
          const node = graph.find(entityId);

          if (node) {
            pipelineStatus = node.getModel()?.metadata?.pipelineStatus;
          }
        }
      });
    }

    return getDefaultGraphVersion(pipelineStatus);
  };

  const removeEntity = () => {
    Modal.confirm({
      title: tn('confirm_entity_delete_title'),
      content: tn('confirm_entity_delete_description', { entityName: selectedEntity?.label }),
      okText: tc('remove'),
      cancelText: tc('cancel'),
      onOk: async () => {
        const resp = await dispatch(deleteEntity(entityId, true));
        if (resp.success) {
          navigate(replaceToken(RouteConstants.ENTITIES, { tabId }));
        } else {
          message.error(
            <>
              {tn('delete_entity_failed', { entityName: selectedEntity?.label })}
              <br />
              {resp?.error?.errorMessage}
            </>
          );
        }
      },
    });
  };

  const openEntityPipeline = (entityId: string, graphVersion?: string) => {
    graphVersion = graphVersion || getGraphVersion()?.toLowerCase();
    navigate(replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { entityId, graphVersion }));
  };

  // TODO: Type evt
  const handleGraphDoubleClick = (evt: any) => {
    if (evt?.item?.type === 'node' && evt?.item?.id) {
      const entityId = evt.item.id;
      const pipelineStatus = evt.item?.model?.metadata?.pipelineStatus;
      const graphVersion = getDefaultGraphVersion(pipelineStatus);

      openEntityPipeline(entityId, graphVersion);
    }
  };

  const getContextPanel = (skipEntityPanel: boolean) => {
    const entityTypes = [AppConstants.ENTITY_TYPES.STANDARD, AppConstants.ENTITY_TYPES.CUSTOM];

    if (firstConnectorActivating(connectors)) {
      return (
        <EmptyGraphPanel className="empty-synapse" panelIcon={<Icon type="loading" />}>
          <span dangerouslySetInnerHTML={{ __html: tn('first_synapse_activating') }} />
        </EmptyGraphPanel>
      );
    }
    if (areAllUnmappedEntities(entities) && !haveActiveConnectors(connectors) && !Boolean(folders?.length)) {
      const createSynapse = (
        <EmptyGraphPanel
          className="empty-synapse"
          onActionClick={() => navigate(RouteConstants.SYNAPSES)}
          icon={ConnectorIcon}
          actionText="Create Synapse"
          actionDisabled={!userHasPermission(AllPermissions.READ_CONNECTOR)}
          actionTooltip={!userHasPermission(AllPermissions.READ_CONNECTOR) ? tc('permission_error') : undefined}>
          <span dangerouslySetInnerHTML={{ __html: tn('create_and_activate') }} />
        </EmptyGraphPanel>
      );

      return userHasPermission(AllPermissions.WRITE_STUDIO) ? (
        <SyncStudioTabsPanel connectors={getConnectorsForPanel(connectors)} synapsesTab={createSynapse} />
      ) : (
        createSynapse
      );
    }

    // Field property Panel
    const hasEntity = entityTypes.includes(selectedEntity?.entityType as 'standard' | 'custom') || entityId;
    const isQuickStart = location.href.includes(SyncStudioPanelKeys.QuickStartV2);
    if (!skipEntityPanel && hasEntity && !isQuickStart) {
      return (
        <EntityPanel
          key={entityId}
          entityId={entityId}
          entityName={selectedEntity?.displayName || ''}
          actions={[
            {
              id: `map${entityId}`,
              name: tn('edit_pipeline'),
              icon: 'edit',
              handler: () => openEntityPipeline(entityId),
            },
            {
              id: `mapUnmappedFields${entityId}`,
              svgIcon: DISTANCE_ICON,
              name: tn('manage_field_mappings'),
              handler: () => dispatch(showFastMapper({ visible: true, entityId })),
            },
            {
              id: `remove${entityId}`,
              name: tn('remove'),
              disabled: selectedEntity?.pipelineStatus !== AppConstants.SYNCARI_NODE_STATUS.UNMAPPED,
              disabledMessage: tn('cannot_remove'),
              icon: 'delete',
              handler: removeEntity,
            },
          ]}
          onClose={onEntityPanelClose}
        />
      );
    }

    if (shouldTabsPanelRender) {
      return <SyncStudioTabsPanel connectors={getConnectorsForPanel(connectors)} />;
    }

    // Connectors Panel
    return <EntityEditorConnectorPanel connectors={getConnectorsForPanel(connectors)} />;
  };

  // TODO: Type evt
  const shouldSavePreference = (evt: any) => {
    if (evt?.updateModel?.color || evt?.updateModel?.zIndex || evt?.updateModel?.selected) {
      return false;
    }
    if (evt.action === AppConstants.NODE_ACTION.CHANGE_DATA) {
      return false;
    }
    return true;
  };

  // TODO: Type evt
  const onGraphChange = useCallback(
    (evt: any) => {
      const response = updateGraph({ nodes, edges, event: evt });
      if (response) {
        const { nodes, edges } = response;
        setNodes(cloneDeep(nodes));
        setEdges(cloneDeep(edges));

        if (shouldSavePreference(evt)) {
          const pref = extractUserPref(nodes, edges);
          dispatch(
            setUserPreference(
              AppConstants.USER_PREF.ENTITY_GRAPH,
              {
                ...pref,
                instanceId,
              },
              true
            )
          );
        }

        // Re-select the selected node when data changes (which causes the selected
        // node to get unselected for some reason)
        if (evt.action === AppConstants.NODE_ACTION.CHANGE_DATA && entityId) {
          const page = editorRef.current.getCurrentPage();
          const item = page.getGraph().find(entityId);

          if (item) {
            // This updates the node to have the blue selected outline
            page.setSelected(item, true);

            // This sets selected to true for the item so we can render the selected
            // style in the node (like blue text, bottom bar, and icon)
            editorRef.current.executeCommand(() => {
              page.update(item, { selected: true });
            });
          }
        }
      }
    },
    [dispatch, entityId, instanceId, edges, nodes]
  );

  const getLoadingStatus = () => {
    let loadingMessage = tc('loading');
    let loading = false;

    if (entitiesFetching) {
      loading = entitiesFetching;
      loadingMessage = tn('loading_entities');
    } else if (getSyncStatusesStatus === FETCH_STATUS.LOADING && !viewingDetails) {
      loading = true;
      loadingMessage = tn('loading_sync_status');
    }
    return { loadingMessage, loading };
  };

  // TODO: Type evt
  const dragEdgeBeforeShowAnchor = (evt: any) => {
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

  // TODO: Type evt
  const hoverNodeBeforeShowAnchor = (evt: any) => {
    evt.cancel = true;
  };

  const saveViewportMatrix = (matrix: number[]) => {
    updateSyncStudioPipelineViewports(ENTITY_EDITOR_VIEWPORT, matrix);
  };

  const { loadingMessage, loading } = getLoadingStatus();

  const contextPanel = getContextPanel(viewingDetails);

  return (
    <Spin tip={loadingMessage} spinning={loading}>
      <FastMapper />
      <SyncStudioRootTabs />
      {quickStartPublishId && (
        <QuickStartPublish visible={!!quickStartPublishVisible} quickStartId={quickStartPublishId} />
      )}
      {/* We need to prevent this from rendering until loading in complete otherwise the graph won't show data initially */}
      <>
        {viewingDetails ? (
          <PipelineDetails contextPanel={contextPanel} />
        ) : !loading ? (
          <GraphEditor
            entityId={entityId}
            tabId={tabId}
            selectedItemId={entityId}
            data={graphData}
            dragEdgeBeforeShowAnchor={dragEdgeBeforeShowAnchor}
            hoverNodeBeforeShowAnchor={hoverNodeBeforeShowAnchor}
            graphMode={GRAPH_MODE.UPDATE_ONLY}
            selectNodeEdges
            saveViewportMatrix={saveViewportMatrix}
            pipelineViewportMatrix={userPref.syncStudio?.pipelineViewports?.[ENTITY_EDITOR_VIEWPORT]}
            contextPanel={contextPanel}
            setSelectedNode={setSelectedEntity}
            onAfterItemUnSelected={onEntityPanelClose}
            onGraphDoubleClick={handleGraphDoubleClick}
            onGraphChange={onGraphChange}
            setEditor={setEditor}
            className="entity-editor"
          />
        ) : null}
      </>
      {connectorEntityModalVisible && <ConnectorEntityModal key="connector-entity-modal" />}
    </Spin>
  );
};

export default EntityEditor;
