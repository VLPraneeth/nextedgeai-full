/* eslint-disable jsx-a11y/anchor-is-valid */
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { message, Modal, Tooltip } from 'antd';
import Menu, { ClickParam } from 'antd/lib/menu';
import Text from 'antd/lib/typography/Text';
import { mapValues } from 'lodash';
import { useEffect, useState } from 'react';

import {
  activateConnector,
  deactivateConnector,
  deleteConnector,
  setModalMode,
  showConnectorModal,
  showWebhookLogsModal,
  testConnector,
} from 'actions/connectorActions';
import Can from 'components/Can';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useNavigateTo from 'hooks/useNavigateTo';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { DATASETS_CONNECTOR_LABEL, FILE_ACTION, FILE_DATA_CONNECTOR_LABEL } from 'pages/imported-files/constants';
import { ConnectorActionModel, KebabNodeConnector } from 'store/app/app.types';
import AppConstants from 'utils/AppConstants';
import { findConnectorMetadataByConnectorId } from 'utils/ConnectorUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

const tn = tNamespaced('ConnectorDropDown');

const ACTION = {
  TEST: 'test',
  EDIT: 'edit',
  ACTIVATE: 'activate',
  DEACTIVATE: 'deactivate',
  DELETE: 'delete',
  REFRESH: 'refresh',
  ACTIVATE_CREATE_PIPELINES: 'activateAndCreatePipelines',
  VIEW_LOGS: 'viewLogs',
};

interface DisabledValue {
  disabled: boolean;
  disabledMessage?: string;
}

type DisabledState = Record<keyof typeof ACTION, DisabledValue>;

const useConnectorMenu = () => {
  const connector = useEnhancedSelector((state) => (state.app.kebabMenuNode as KebabNodeConnector)?.connector);
  const { connectors, connectorsMetadata } = useEnhancedSelector((state) => state.connector);

  const { userHasPermission } = useUserHasPermission();

  const [disabledState, setDisabledState] = useState<DisabledState>(mapValues(ACTION, (key) => ({ disabled: false })));

  const dispatch = useEnhancedDispatch();

  const navigate = useNavigateTo();

  const _showEditConnector = (connectorId: string) => {
    const metadata = findConnectorMetadataByConnectorId(connectorId, connectors, connectorsMetadata);
    if (metadata) {
      dispatch(setModalMode(AppConstants.MODAL_MODE.EDIT, metadata, connectorId));
      dispatch(showConnectorModal());
    }
  };

  useEffect(() => {
    const activeConnector = connector?.status === AppConstants.CONNECTOR_STATUS.ACTIVE;
    const inactiveConnector = connector?.status === AppConstants.CONNECTOR_STATUS.INACTIVE;

    setDisabledState((current) => ({
      ...current,
      ACTIVATE: {
        disabled: activeConnector,
        disabledMessage: (activeConnector && tn('cannot_activate_active_connector')) || '',
      },
      DEACTIVATE: {
        disabled: inactiveConnector,
        disabledMessage: (inactiveConnector && tn('cannot_deactivate_inactive_connector')) || '',
      },
    }));
  }, [connector]);

  if (!connector) {
    return null;
  }

  const handler = (action: ClickParam) => {
    const localConnector: ConnectorActionModel & { connectorId: string } = { ...connector, connectorId: connector.id };
    const timeout = 200;

    switch (action.key) {
      case ACTION.TEST:
        const dismissLoading = message.loading(tn('testing_connector', { connector: connector.label }), 0);
        // @ts-ignore the returned promise is not found by ts since
        // testConnector is coming from a .js file
        dispatch(testConnector([localConnector])).then(() => dismissLoading());
        break;
      case ACTION.EDIT:
        _showEditConnector(connector.id);
        break;
      case ACTION.ACTIVATE:
        // @ts-ignore the returned promise is not found by ts since
        // testConnector is coming from a .js file
        dispatch(testConnector([localConnector])).then((res) => {
          if (res.success) {
            dispatch(activateConnector([localConnector]));
          }
        });
        break;
      case ACTION.ACTIVATE_CREATE_PIPELINES:
        dispatch(activateConnector([localConnector], true));
        break;
      case ACTION.VIEW_LOGS:
        {
          const metadata = findConnectorMetadataByConnectorId(connector.id, connectors, connectorsMetadata);
          if (metadata) {
            dispatch(setModalMode('webhookLogs', metadata, connector.id));
            dispatch(showWebhookLogsModal());
          }
        }

        break;
      case ACTION.DEACTIVATE:
        dispatch(deactivateConnector([localConnector]));
        break;
      case ACTION.DELETE:
        Modal.confirm({
          title: tn('delete_connector'),
          content: (
            <span
              // Note: i18next sanitize the token for script injection
              dangerouslySetInnerHTML={{
                __html: tn('delete_confirmation', { name: localConnector.label }),
              }}
            />
          ),
          onOk() {
            dispatch(deleteConnector([localConnector]));
          },
        });
        break;
      case FILE_ACTION.MANAGE_FILES:
        // timeout to account for animation of closing menu
        setTimeout(() => navigate(makeUrl(RouteConstants.IMPORTED_FILES)), timeout);

        break;
      case FILE_ACTION.VIEW_SCHEMA:
        setTimeout(
          () => navigate(makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE, { connectorId: localConnector.connectorId })),
          timeout
        );
        break;
      default:
        break;
    }
  };

  if (connector?.label === DATASETS_CONNECTOR_LABEL) {
    return (
      <Menu onClick={handler}>
        <Can key={FILE_ACTION.VIEW_SCHEMA} permission={AllPermissions.READ_STUDIO}>
          <Menu.Item>
            <Text>{tn('view_schema')}</Text>
          </Menu.Item>
        </Can>
      </Menu>
    );
  }

  if (connector?.label === FILE_DATA_CONNECTOR_LABEL) {
    return (
      <Menu onClick={handler}>
        <Can key={FILE_ACTION.MANAGE_FILES} permission={AllPermissions.READ_FILE_DATA}>
          <Menu.Item className={!userHasPermission(AllPermissions.READ_FILE_DATA) ? 'disabled' : ''}>
            <Text>{tn('manage_files')}</Text>
          </Menu.Item>
        </Can>

        <Can key={FILE_ACTION.VIEW_SCHEMA} permission={AllPermissions.READ_STUDIO}>
          <Menu.Item>
            <Text>{tn('view_schema')}</Text>
          </Menu.Item>
        </Can>
      </Menu>
    );
  }

  const metadata = findConnectorMetadataByConnectorId(connector?.id, connectors, connectorsMetadata);
  // Disabled Edit while we're loading metadata
  const editDisabled = !userHasPermission(AllPermissions.WRITE_CONNECTOR) || !metadata;

  return (
    <Menu onClick={handler}>
      <Can key={ACTION.TEST} permission={AllPermissions.TEST_CONNECTION}>
        <Menu.Item className={!userHasPermission(AllPermissions.TEST_CONNECTION) ? 'disabled' : ''}>
          <Text>{tn('test')}</Text>
        </Menu.Item>
      </Can>
      <Can key={ACTION.EDIT} permission={AllPermissions.WRITE_CONNECTOR}>
        <Menu.Item disabled={editDisabled} className={editDisabled ? 'disabled' : ''}>
          <Text>{tn('edit')}</Text>
        </Menu.Item>
      </Can>
      <Can key={ACTION.ACTIVATE} permission={AllPermissions.WRITE_CONNECTOR}>
        <Menu.Item
          className={
            disabledState.ACTIVATE.disabled || !userHasPermission(AllPermissions.WRITE_CONNECTOR) ? 'disabled' : ''
          }>
          <Tooltip
            placement="right"
            title={userHasPermission(AllPermissions.WRITE_CONNECTOR) ? disabledState.ACTIVATE.disabledMessage : ''}>
            <Text>{tn('activate')}</Text>
          </Tooltip>
        </Menu.Item>
      </Can>
      <Can key={ACTION.ACTIVATE_CREATE_PIPELINES} permission={AllPermissions.WRITE_CONNECTOR}>
        <Menu.Item
          className={
            disabledState.ACTIVATE.disabled || !userHasPermission(AllPermissions.WRITE_CONNECTOR) ? 'disabled' : ''
          }>
          <Tooltip
            placement="right"
            title={userHasPermission(AllPermissions.WRITE_CONNECTOR) ? disabledState.ACTIVATE?.disabledMessage : ''}>
            <Text>{tn('activate_and_create_pipelines')}</Text>
          </Tooltip>
        </Menu.Item>
      </Can>
      {metadata?.webhook && (
        <Can key={ACTION.VIEW_LOGS} permission={AllPermissions.READ_CONNECTOR}>
          <Menu.Item className={!userHasPermission(AllPermissions.READ_CONNECTOR) ? 'disabled' : ''}>
            <Text>{tn('view_logs')}</Text>
          </Menu.Item>
        </Can>
      )}
      <Can key={ACTION.DEACTIVATE} permission={AllPermissions.WRITE_CONNECTOR}>
        <Menu.Item
          className={
            disabledState.DEACTIVATE.disabled || !userHasPermission(AllPermissions.WRITE_CONNECTOR) ? 'disabled' : ''
          }>
          <Tooltip
            placement="right"
            title={userHasPermission(AllPermissions.WRITE_CONNECTOR) ? disabledState.DEACTIVATE.disabledMessage : ''}>
            <Text>{tn('deactivate')}</Text>
          </Tooltip>
        </Menu.Item>
      </Can>
      <Can key={ACTION.DELETE} permission={AllPermissions.WRITE_CONNECTOR}>
        <Menu.Item className={!userHasPermission(AllPermissions.WRITE_CONNECTOR) ? 'disabled' : ''}>
          <Text>{tn('delete')}</Text>
        </Menu.Item>
      </Can>
    </Menu>
  );
};

export default useConnectorMenu;
