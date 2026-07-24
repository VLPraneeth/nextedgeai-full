import { Redirect, RouteComponentProps, Router, useLocation, useMatch } from '@reach/router';
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import {
  getConnectors as getConnectorsAction,
  showConnectorSettingModal as showConnectorSettingModalAction,
} from 'actions/connectorActions';
import { ReactComponent as BaselinePublishIcon } from 'assets/icons/baseline-publish.svg';
import Button from 'components/Button';
import Can from 'components/Can';
import { HStack } from 'components/layout';
import RouteSpin from 'components/RouteSpin';
import { Toolbar } from 'components/toolbar';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import Error404 from 'pages/errors/Error404';
import { getDervConnectors, selectSyncariConnector } from 'selectors/connectorSelectors';
import { useSynapseRefreshingStatus } from 'store/connectors';
import {
  selectAllConnectorSchemas,
  selectEntitySaveStatus,
  selectEntitySchemaDraftCreateStatus,
  selectEntitySchemaDraftDiscardStatus,
  selectEntitySchemaDraftPublishStatus,
  selectShowingPublishConfirmationModal,
} from 'store/schema/selectors';
import {
  getSchemaForConnector as getSchemaForConnectorAction,
  getSchemaForConnectorEntity as getSchemaForConnectorEntityAction,
  publishEntitySchemaAndRefreshConnector as publishEntitySchemaAction,
  showPublishConfirmationModalForEntityId as showPublishConfirmationModalForEntityIdAction,
} from 'store/schema/thunks';
import { keyBy } from 'utils/Fp';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/StringUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { RootState } from '../../reducers';
import ConnectorSelector from './ConnectorSelector';
import EntityDraftPublishConfirmationModal from './EntityDraftPublishConfirmationModal';
import EntitySchemaModal from './EntitySchemaModal';
import EntitySchemaPanel from './EntitySchemaPanel';
import EntitySchemaTable from './EntitySchemaTable';
import EntitySelector from './EntitySelector';
import FieldSchemaModal from './FieldSchemaModal';
import FieldSchemaPanel from './FieldSchemaPanel';
import FieldSchemaTable from './FieldSchemaTable';
import { Connector, ConnectorSchema, EntityModel, FieldModel, SchemaVersion, VersionedSchemaData } from './types';

import './SchemaStudio.less';

const tc = tNamespaced('Common');
const tn = tNamespaced('SchemaStudio');

interface DefaultSyncariEntityProps extends RouteComponentProps {
  syncariSynapse?: Connector;
}

const DefaultSyncariEntity = ({ syncariSynapse }: DefaultSyncariEntityProps) => {
  if (syncariSynapse?.id) {
    return (
      <Redirect to={replaceToken(RouteConstants.SCHEMA_STUDIO_SYNAPSE, { connectorId: syncariSynapse.id })} noThrow />
    );
  }

  return <Error404 />;
};

const rootConnector = connect(
  (state: RootState, props: SchemaStudioRootProps) => ({
    // TODO: type connectors!
    connectors: getDervConnectors(state),
    connectorEntities: selectAllConnectorSchemas(state),
    syncariSynapse: selectSyncariConnector(state),
    showingPublishConfirmationModalMetadata: selectShowingPublishConfirmationModal(state),
    entitySchemaDraftPublishStatus: selectEntitySchemaDraftPublishStatus(state),
    entitySchemaDraftDiscardStatus: selectEntitySchemaDraftDiscardStatus(state),
    entitySchemaDraftCreateStatus: selectEntitySchemaDraftCreateStatus(state),
    entitySaveStatus: selectEntitySaveStatus(state),
  }),
  (dispatch) =>
    bindActionCreators(
      {
        getConnectors: getConnectorsAction,
        getSchemaForConnector: getSchemaForConnectorAction,
        getSchemaForConnectorEntity: getSchemaForConnectorEntityAction,
        publishEntitySchema: publishEntitySchemaAction,
        showPublishConfirmationModalForEntityId: showPublishConfirmationModalForEntityIdAction,
        showConnectorSettingModal: showConnectorSettingModalAction,
      },
      dispatch
    )
);

interface SchemaStudioRootProps extends RouteComponentProps {
  connectors?: Connector[];
}

type PropsFromRedux = ConnectedProps<typeof rootConnector>;

const SchemaStudioRoot = ({
  navigate,

  syncariSynapse,
  connectors,
  connectorEntities,
  showingPublishConfirmationModalMetadata,

  entitySchemaDraftPublishStatus,
  entitySchemaDraftDiscardStatus,
  entitySchemaDraftCreateStatus,
  entitySaveStatus,

  getConnectors,
  getSchemaForConnector,
  getSchemaForConnectorEntity,
  publishEntitySchema,
  showPublishConfirmationModalForEntityId,
  showConnectorSettingModal,
}: SchemaStudioRootProps & Omit<PropsFromRedux, 'connectors'>) => {
  const [currentlySelectedEntity, setCurrentlySelectedEntity] = useState<EntityModel | undefined>();
  const [currentlySelectedField, setCurrentlySelectedField] = useState<FieldModel | undefined>();
  const [entityModalVisible, setEntityModalVisible] = useState<boolean>(false);
  const [entityIdForModal, setEntityIdForModal] = useState<string | undefined>();

  const [fieldModalVisible, setFieldModalVisible] = useState<boolean>(false);
  const [fieldForModal, setFieldForModal] = useState<FieldModel | undefined>();

  const Error403 = useForbiddenRedirect({
    studioPermissions: [AllPermissions.READ_CONNECTOR, AllPermissions.READ_STUDIO],
    operator: PermissionsComparisonOperator.AND,
  });

  const closeModal = useCallback(() => showPublishConfirmationModalForEntityId(null, null), [
    showPublishConfirmationModalForEntityId,
  ]);

  const connectorIdMatch = useMatch('/schema-studio/synapse/:connectorId');
  const entityNameMatch = useMatch('/schema-studio/synapse/:connectorId/entity/:entityName/:version');

  const location = useLocation();

  // When the user navigates to another page, clear the selected entity and
  // field values
  useEffect(() => {
    setCurrentlySelectedEntity(undefined);
    setCurrentlySelectedField(undefined);
  }, [location]);

  const connectorId = entityNameMatch?.connectorId || connectorIdMatch?.connectorId;
  const entityName = entityNameMatch?.entityName;
  const schemaVersion: SchemaVersion = (entityNameMatch?.version || 'published') as SchemaVersion;

  const currentConnector = useMemo(() => {
    if (connectorId && connectors?.length) {
      return connectors.find((connector) => connector.id === connectorId);
    }
  }, [connectorId, connectors]);

  const currentConnectorEntityMap: Record<string, VersionedSchemaData<ConnectorSchema>> = useMemo(() => {
    if (currentConnector && connectorEntities) {
      const entityList = connectorEntities?.[currentConnector?.id]?.data || [];
      return keyBy('apiName', entityList as any);
    }

    return {};
  }, [currentConnector, connectorEntities]);

  const currentEntity = useMemo(() => {
    return entityName ? currentConnectorEntityMap[entityName] : null;
  }, [entityName, currentConnectorEntityMap]);

  const { isRefreshing } = useSynapseRefreshingStatus(connectorId);

  const handleEntityChange = useCallback(
    (entityApiName: string, version: SchemaVersion) => {
      navigate?.(
        replaceToken(RouteConstants.SCHEMA_STUDIO_SYNAPSE_ENTITY, {
          connectorId,
          entityApiName,
          version,
        })
      );
    },
    [connectorId, navigate]
  );

  // Redirect users to a valid published/draft version if they're viewing a
  // version that doesn't exist
  useEffect(() => {
    if (schemaVersion === 'draft' && !currentEntity?.draft && currentEntity?.published) {
      handleEntityChange(currentEntity.apiName, 'published');
    } else if (schemaVersion === 'published' && !currentEntity?.published && currentEntity?.draft) {
      handleEntityChange(currentEntity.apiName, 'draft');
    }
  }, [currentEntity?.apiName, currentEntity?.draft, currentEntity?.published, handleEntityChange, schemaVersion]);

  const entityId = currentEntity?.[schemaVersion]?.fields.id;

  useEffect(() => {
    if (!connectors) {
      getConnectors();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // fetch synapse details
  useEffect(() => {
    if (connectors && currentConnector) {
      getSchemaForConnector(currentConnector.id);
    }
  }, [connectors, currentConnector, getSchemaForConnector]);

  // fetch Entity details
  useEffect(() => {
    if (entityId) {
      getSchemaForConnectorEntity(entityId);
    }
  }, [entityId, getSchemaForConnectorEntity]);

  /* toasts for entity draft actions */
  useToastForFetchStatusChange(entitySchemaDraftDiscardStatus, {
    error: tn('deleting_draft_error'),
    success: tn('deleting_draft_success'),
  });

  useToastForFetchStatusChange(entitySchemaDraftCreateStatus, {
    error: tn('creating_draft_error'),
    success: tn('creating_draft_success'),
  });

  useToastForFetchStatusChange(entitySaveStatus, {
    error: tn('saving_entity_error'),
    success: tn('saving_entity_success'),
  });
  /* end toasts */

  const handleConnectorChange = useCallback(
    (connector?: Connector) => {
      if (connector) {
        navigate?.(replaceToken(RouteConstants.SCHEMA_STUDIO_SYNAPSE, { connectorId: connector?.id }));
      }
    },
    [navigate]
  );

  const backToName = entityId ? currentConnector?.name : undefined;
  const handleNavigateUp = useCallback(() => {
    handleConnectorChange(currentConnector);
  }, [currentConnector, handleConnectorChange]);

  const onRequestPublishDraft = React.useCallback(
    (entityId: string, connectorId: string) => {
      if (connectorId && entityId) {
        navigate?.(replaceToken(RouteConstants.SCHEMA_STUDIO_SYNAPSE, { connectorId }));
      }
    },
    [navigate]
  );

  if (!connectors) {
    return Error403 ?? <RouteSpin title={tn('loading') as string} />;
  }

  const editEntity = (entityId: string) => {
    setEntityIdForModal(entityId);
    setEntityModalVisible(true);
  };

  const createEntity = () => {
    setEntityIdForModal(undefined);
    setEntityModalVisible(true);
  };

  const handleConfigureEntity = () => {
    if (currentConnector) {
      showConnectorSettingModal?.(true, currentConnector);
    }
  };

  const editField = (field: FieldModel) => {
    setFieldForModal(field);
    setFieldModalVisible(true);
  };

  const createField = () => {
    setFieldForModal(undefined);
    setFieldModalVisible(true);
  };

  return (
    Error403 ?? (
      <div className="schema-studio-container">
        <div className="schema-studio-left-content">
          <Toolbar
            backToName={backToName}
            onRequestBack={handleNavigateUp}
            className={cx(schemaVersion)}
            leftChildren={
              !currentEntity || !entityId ? (
                <ConnectorSelector
                  selected={currentConnector}
                  connectors={connectors}
                  onChange={handleConnectorChange}
                />
              ) : (
                <HStack>
                  <EntitySelector
                    entities={currentConnectorEntityMap}
                    currentVersion={schemaVersion}
                    currentEntityId={entityId}
                    currentEntity={currentEntity}
                    onChange={handleEntityChange}
                  />
                  {schemaVersion === 'draft' && (
                    <Tooltip title={isRefreshing && tc('unavailable_during_refresh')}>
                      {/* Weird tooltip bug here in antd. See https://github.com/react-component/tooltip/issues/18#issuecomment-650864750 */}
                      <span style={{ cursor: isRefreshing ? 'not-allowed' : 'pointer' }}>
                        <Can permission={AllPermissions.WRITE_STUDIO}>
                          <Button
                            data-userflow-tag={UserflowTags.SchemaStudio.EntityVersionSelector}
                            disabled={isRefreshing}
                            style={{ pointerEvents: isRefreshing ? 'none' : 'initial' }}
                            type="primary"
                            onClick={() => showPublishConfirmationModalForEntityId(entityId, connectorId || null)}>
                            <Icon component={BaselinePublishIcon as React.FC} />
                            Publish
                          </Button>
                        </Can>
                      </span>
                    </Tooltip>
                  )}
                </HStack>
              )
            }
          />
          <Router className="schema-studio-content">
            <EntitySchemaTable
              key={currentConnector?.id}
              path="/synapse/:connectorId"
              isSyncariConnector={syncariSynapse?.id === connectorId}
              schemaVersion={schemaVersion}
              onSelectEntityRow={setCurrentlySelectedEntity}
              selectedEntity={currentlySelectedEntity}
              showNewEntity={createEntity}
              showConfigureEntityModal={handleConfigureEntity}
            />
            <FieldSchemaTable
              path="/synapse/:connectorId/entity/:entityApiName/:version"
              isSyncariConnector={syncariSynapse?.id === connectorId}
              connectorId={connectorId}
              entityId={entityId}
              hasDraft={!!currentEntity?.draft}
              schemaVersion={schemaVersion}
              handleEntityChange={handleEntityChange}
              onSelectFieldRow={setCurrentlySelectedField}
              selectedField={currentlySelectedField}
              showNewField={() => createField()}
              synapse={currentConnector}
            />
            <DefaultSyncariEntity syncariSynapse={syncariSynapse as any} default />
          </Router>
        </div>

        {connectorId && !entityId && (
          <>
            <EntitySchemaPanel
              entityId={currentlySelectedEntity?.id}
              editEntity={editEntity}
              onClose={() => setCurrentlySelectedEntity(undefined)}
              isSyncariConnector={syncariSynapse?.id === connectorId}
            />
            <EntitySchemaModal
              connectorId={currentConnector?.id}
              visible={entityModalVisible}
              onClose={() => {
                setEntityModalVisible(false);
              }}
              entityId={entityIdForModal}
            />
          </>
        )}
        {entityId && (
          <>
            <FieldSchemaPanel
              connectorId={connectorId}
              field={currentlySelectedField}
              entityId={entityId}
              editField={editField}
              onClose={() => setCurrentlySelectedField(undefined)}
              isSyncariConnector={syncariSynapse?.id === connectorId}
              synapse={currentConnector}
            />
            <FieldSchemaModal
              visible={fieldModalVisible}
              entityId={entityId}
              entity={currentEntity}
              onClose={() => {
                setFieldModalVisible(false);
              }}
              isSyncariConnector={syncariSynapse?.id === connectorId}
              field={fieldForModal}
              synapse={currentConnector}
            />
          </>
        )}

        {showingPublishConfirmationModalMetadata?.entityId && showingPublishConfirmationModalMetadata?.connectorId && (
          <EntityDraftPublishConfirmationModal
            entityId={showingPublishConfirmationModalMetadata?.entityId}
            connectorId={showingPublishConfirmationModalMetadata?.connectorId}
            onRequestClose={closeModal}
            onConfirm={onRequestPublishDraft}
          />
        )}
      </div>
    )
  );
};

export default rootConnector(SchemaStudioRoot as any);
