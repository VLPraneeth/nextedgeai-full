//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { delay, first, isEmpty } from 'lodash';
import { Action } from 'redux';
import { ThunkAction } from 'redux-thunk';

import { SyncariThunkDispatch } from 'hooks/redux';
import { RootState } from 'store/types';
import { deleteRequest, get, post } from 'utils/AjaxUtil';
import { getConnectorError, getErrorMessage, handleAsApplicationError } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { thottlePromiseThunk } from 'utils/StoreUtil';
import { replaceToken } from 'utils/UrlUtil';

// Action types that are dispatched by this action
export const ActionTypes = {
  GET_CONNECTORS: 'GET_CONNECTORS',
  GET_CONNECTORS_PENDING: 'GET_CONNECTORS_PENDING',
  GET_CONNECTORS_FULFILLED: 'GET_CONNECTORS_FULFILLED',
  GET_CONNECTORS_FAILED: 'GET_CONNECTORS_FAILED',

  GET_CONNECTOR_ENTITIES_PENDING: 'GET_CONNECTOR_ENTITIES_PENDING',
  GET_CONNECTOR_ENTITIES_FULFILLED: 'GET_CONNECTOR_ENTITIES_FULFILLED',
  REMOVE_CONNECTOR_NODE: 'REMOVE_CONNECTOR_NODE',
  ADD_CONNECTOR_NODE: 'ADD_CONNECTOR_NODE',

  GET_CONNECTOR_PENDING: 'GET_CONNECTOR_PENDING',
  GET_CONNECTOR_FULFILLED: 'GET_CONNECTOR_FULFILLED',
  GET_CONNECTOR_FAILED: 'GET_CONNECTOR_FAILED',

  ACTIVATE_CONNECTOR_PENDING: 'ACTIVATE_CONNECTOR_PENDING',
  ACTIVATE_CONNECTOR_FULFILLED: 'ACTIVATE_CONNECTOR_FULFILLED',
  ACTIVATE_CONNECTOR_FAILED: 'ACTIVATE_CONNECTOR_FAILED',

  DEACTIVATE_CONNECTOR_PENDING: 'DEACTIVATE_CONNECTOR_PENDING',
  DEACTIVATE_CONNECTOR_FULFILLED: 'DEACTIVATE_CONNECTOR_FULFILLED',
  DEACTIVATE_CONNECTOR_FAILED: 'DEACTIVATE_CONNECTOR_FAILED',

  DELETE_CONNECTOR_PENDING: 'DELETE_CONNECTOR_PENDING',
  DELETE_CONNECTOR_FULFILLED: 'DELETE_CONNECTOR_FULFILLED',
  DELETE_CONNECTOR_FAILED: 'DELETE_CONNECTOR_FAILED',

  TEST_CONNECTOR: 'TEST_CONNECTOR',
  TEST_CONNECTOR_PENDING: 'TEST_CONNECTOR_PENDING',
  TEST_CONNECTOR_FULFILLED: 'TEST_CONNECTOR_FULFILLED',
  TEST_CONNECTOR_FAILED: 'TEST_CONNECTOR_FAILED',
  TEST_CONNECTOR_RESET: 'TEST_CONNECTOR_RESET',

  APPROVE_CONNECTOR: 'APPROVE_CONNECTOR',
  APPROVE_CONNECTOR_PENDING: 'APPROVE_CONNECTOR_PENDING',
  APPROVE_CONNECTOR_FULFILLED: 'APPROVE_CONNECTOR_FULFILLED',
  APPROVE_CONNECTOR_FAILED: 'APPROVE_CONNECTOR_FAILED',

  DISCARD_CONNECTOR: 'DISCARD_CONNECTOR',
  DISCARD_CONNECTOR_PENDING: 'DISCARD_CONNECTOR_PENDING',
  DISCARD_CONNECTOR_FULFILLED: 'DISCARD_CONNECTOR_FULFILLED',
  DISCARD_CONNECTOR_FAILED: 'DISCARD_CONNECTOR_FAILED',

  SHOW_CONNECTOR_MODAL: 'SHOW_CONNECTOR_MODAL',
  SHOW_CONNECTOR_SETTING_MODAL: 'SHOW_CONNECTOR_SETTING_MODAL',
  SET_CONNECTOR_STATUS_MESSAGE: 'SET_CONNECTOR_STATUS_MESSAGE',
  SET_MODAL_MODE: 'SET_MODAL_MODE',
  INITIALIZE_CONNECTOR_MODAL: 'INITIALIZE_CONNECTOR_MODAL',

  CREATE_CONNECTOR: 'CREATE_CONNECTOR',
  CREATE_CONNECTOR_PENDING: 'CREATE_CONNECTOR_PENDING',
  CREATE_CONNECTOR_FULFILLED: 'CREATE_CONNECTOR_FULFILLED',
  CREATE_CONNECTOR_FAILED: 'CREATE_CONNECTOR_FAILED',

  OAUTHENTICATE: 'OAUTHENTICATE',
  OAUTHENTICATE_PENDING: 'OAUTHENTICATE_PENDING',
  OAUTHENTICATE_FULFILLED: 'OAUTHENTICATE_FULFILLED',
  OAUTHENTICATE_FAILED: 'OAUTHENTICATE_FAILED',

  OAUTH_GET_REDIRECT_URL: 'OAUTH_GET_REDIRECT_URL',
  OAUTH_GET_REDIRECT_URL_PENDING: 'OAUTH_GET_REDIRECT_URL_PENDING',
  OAUTH_GET_REDIRECT_URL_FULFILLED: 'OAUTH_GET_REDIRECT_URL_FULFILLED',
  OAUTH_GET_REDIRECT_URL_FAILED: 'OAUTH_GET_REDIRECT_URL_FAILED',

  OAUTH_AUTHORIZE_PENDING: 'OAUTH_AUTHORIZE_PENDING',
  OAUTH_AUTHORIZE_FULFILLED: 'OAUTH_AUTHORIZE_FULFILLED',
  OAUTH_AUTHORIZE_FAILED: 'OAUTH_AUTHORIZE_FAILED',

  GET_CONNECTORS_METADATA_PENDING: 'GET_CONNECTORS_METADATA_PENDING',
  GET_CONNECTORS_METADATA_FULFILLED: 'GET_CONNECTORS_METADATA_FULFILLED',
  GET_CONNECTORS_METADATA_FAILED: 'GET_CONNECTORS_METADATA_FAILED',

  SET_CONNECTORS_SETTING_PENDING: 'SET_CONNECTORS_SETTING_PENDING',
  SET_CONNECTORS_SETTING_FULFILLED: 'SET_CONNECTORS_SETTING_FULFILLED',
  SET_CONNECTORS_SETTING_FAILED: 'SET_CONNECTORS_SETTING_FAILED',

  CONNECTOR_ACTIVATION_FAILED: 'CONNECTOR_ACTIVATION_FAILED',
  CONNECTOR_ACTIVATED: 'CONNECTOR_ACTIVATED',

  REFRESH_SCHEMA: 'REFRESH_SCHEMA',
  REFRESH_SCHEMA_COMPLETED: 'REFRESH_SCHEMA_COMPLETED',
  REFRESH_SCHEMA_FAILED: 'REFRESH_SCHEMA_FAILED',

  SHOW_WEBHOOK_LOGS_MODAL: 'SHOW_WEBHOOK_LOGS_MODAL',
};

/**
 * Get the list of connectors that are available
 */
export function getConnectors() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_CONNECTORS_PENDING,
    });

    return get(DataUrlConstants.CONNECTOR)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_CONNECTORS_FULFILLED,
          connectors: resp.data,
        });
        return resp;
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_CONNECTORS_FAILED,
          error: getErrorMessage(error),
        });
        handleAsApplicationError(dispatch)(error);
      });
  };
}

export function getConnector(connectorId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_CONNECTOR_PENDING,
    });
    const url = replaceToken(DataUrlConstants.GET_CONNECTOR, { connectorId });
    get(url)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_CONNECTOR_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_CONNECTOR_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

/**
 * Show the connector modal
 * @param {Boolean} show - make the connector modal visible
 *                         default to show the connector otherwise, false
 */
export function showConnectorModal(show = true) {
  return {
    type: ActionTypes.SHOW_CONNECTOR_MODAL,
    visible: show,
  };
}

export function showWebhookLogsModal(show = true) {
  return {
    type: ActionTypes.SHOW_WEBHOOK_LOGS_MODAL,
    visible: show,
  };
}

export function showConnectorSettingModal(show = true, record: any) {
  return {
    type: ActionTypes.SHOW_CONNECTOR_SETTING_MODAL,
    visible: show,
    record,
  };
}

/**
 * Action for creating a connector
 * @param {Object} params - metadata to create a connector
 */
export function createConnectorWizard(
  { connectorId, ...payload }: { connectorId: string },
  options: { test?: boolean; refresh?: boolean } = {}
) {
  let url: string;
  if (connectorId) {
    url = replaceToken(DataUrlConstants.UPDATE_CONNECTOR, { connectorId });
  } else {
    url = DataUrlConstants.CONNECTOR;
  }

  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.CREATE_CONNECTOR_PENDING,
    });
    return post(url, payload)
      .then((resp) => {
        dispatch({
          type: ActionTypes.CREATE_CONNECTOR_FULFILLED,
          payload: resp.data,
        });
        if (options.test) {
          const { data } = resp;
          const connector = {
            ...data,
            connectorId: data.id,
          };
          testConnector([connector], options)(dispatch);
        } else if (options.refresh) {
          getConnectors()(dispatch);
        }
      })
      .catch((error) => {
        getConnectors()(dispatch);
        dispatch({
          type: ActionTypes.CREATE_CONNECTOR_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function setModalMode(modalMode: string, connectorMetadata: any, connectorId: string) {
  return {
    type: ActionTypes.SET_MODAL_MODE,
    modalMode,
    connectorMetadata,
    connectorId,
  };
}

export function removeConnectorNode(nodeId: string) {
  return {
    type: ActionTypes.REMOVE_CONNECTOR_NODE,
    nodeIdToRemove: nodeId,
  };
}

export function activateConnector(connectors: any[], createPipelines: any = false) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.ACTIVATE_CONNECTOR_PENDING,
      createPipelines,
    });
    const url = replaceToken(DataUrlConstants.ACTIVATE_CONNECTOR, {
      name: first(connectors).connectorId,
    });
    const formData = new FormData();
    formData.append('createMappings', createPipelines);
    post(url, formData)
      .then((resp) => {
        const error = getConnectorError(resp);
        if (error) {
          dispatch({
            type: ActionTypes.ACTIVATE_CONNECTOR_FAILED,
            createPipelines,
            error,
          });
        } else {
          dispatch({
            type: ActionTypes.ACTIVATE_CONNECTOR_FULFILLED,
            createPipelines,
          });
        }
        dispatch(getConnectors());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.ACTIVATE_CONNECTOR_FAILED,
          error: getErrorMessage(error),
          createPipelines,
        });
      });

    delay(() => {
      getConnectors()(dispatch);
    }, 1000);
  };
}

export function deactivateConnector(connectors: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DEACTIVATE_CONNECTOR_PENDING,
    });
    const url = replaceToken(DataUrlConstants.DEACTIVATE_CONNECTOR, {
      name: first(connectors).connectorId,
    });
    post(url, new FormData())
      .then((resp) => {
        const error = getConnectorError(resp);
        if (error) {
          dispatch({
            type: ActionTypes.DEACTIVATE_CONNECTOR_FAILED,
            error,
          });
        } else {
          dispatch({
            type: ActionTypes.DEACTIVATE_CONNECTOR_FULFILLED,
          });
        }
        getConnectors()(dispatch);
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DEACTIVATE_CONNECTOR_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function testConnector(connectors: any[], options: { refresh?: boolean } = {}) {
  return (dispatch: SyncariThunkDispatch) => {
    const connector = first(connectors);
    dispatch({
      type: ActionTypes.TEST_CONNECTOR_PENDING,
    });
    const url = replaceToken(DataUrlConstants.TEST_CONNECTOR, {
      name: connector.connectorId,
    });
    return post(url, new FormData())
      .then((resp) => {
        const error = getConnectorError(resp);
        if (!isEmpty(error.errorMessage) || !isEmpty(error.errorMessage)) {
          dispatch({
            type: ActionTypes.TEST_CONNECTOR_FAILED,
            error,
          });
        } else {
          dispatch({
            type: ActionTypes.TEST_CONNECTOR_FULFILLED,
            payload: resp?.data,
            connectorId: connector.connectorId,
          });
        }
        if (options.refresh !== false) {
          getConnectors()(dispatch);
        }
        return { success: resp?.data?.success };
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.TEST_CONNECTOR_FAILED,
          error: getErrorMessage(error),
        });
        return { success: false };
      });
  };
}

export function testConnectorReset() {
  return {
    type: ActionTypes.TEST_CONNECTOR_RESET,
  };
}

export function updateConnector(connectors: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    const url = replaceToken(DataUrlConstants.UPDATE_CONNECTOR, {
      name: first(connectors).connectorId,
    });

    return post(url, new FormData()).then((resp) => {
      getConnectors()(dispatch);
    });
  };
}

export function approveConnector(connectors: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.APPROVE_CONNECTOR,
    });
    const url = replaceToken(DataUrlConstants.APPROVE_CONNECTOR, {
      name: first(connectors).draft.id,
    });
    return post(url, new FormData()).then((resp) => {
      getConnectors()(dispatch);
    });
  };
}

export function discardConnector(connectors: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DISCARD_CONNECTOR,
    });
    const url = replaceToken(DataUrlConstants.DISCARD_CONNECTOR, {
      name: first(connectors).draft.id,
    });
    return post(url, new FormData()).then((resp) => {
      getConnectors()(dispatch);
    });
  };
}

export function deleteConnector(connectors: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DELETE_CONNECTOR_PENDING,
    });
    const url = replaceToken(DataUrlConstants.DELETE_CONNECTOR, {
      name: first(connectors).connectorId,
    });

    return deleteRequest(url)
      .then((resp) => {
        const error = getConnectorError(resp);
        if (error) {
          dispatch({
            type: ActionTypes.DELETE_CONNECTOR_FAILED,
            error,
          });
        } else {
          dispatch({
            type: ActionTypes.DELETE_CONNECTOR_FULFILLED,
          });
        }
        getConnectors()(dispatch);
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DELETE_CONNECTOR_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function oauthGetRedirectUrl({ connectorId, ...payload }: { connectorId: string }, options = {}) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.OAUTH_GET_REDIRECT_URL_PENDING,
    });

    const url = connectorId
      ? replaceToken(DataUrlConstants.UPDATE_CONNECTOR, { connectorId })
      : DataUrlConstants.CONNECTOR;

    return post(url, payload)
      .then((resp) => {
        dispatch({
          type: ActionTypes.OAUTH_GET_REDIRECT_URL_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.OAUTH_GET_REDIRECT_URL_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

function oAuthClose(closeParams: any) {
  const { oAuthWindow, oAuthDispatch } = window.oAuth;
  delay(() => {
    getConnectors()(oAuthDispatch);
    if (!closeParams.success) {
      oAuthDispatch({
        type: ActionTypes.OAUTH_AUTHORIZE_FAILED,
        errorResp: closeParams.errorResp,
      });
    }
  }, 0);
  if (closeParams.success) {
    oAuthWindow.close();
  }
}

/**
 * Initiate the oauthentication
 */
export function oauthenticate({ connectorId }: { connectorId: string }) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.OAUTHENTICATE_PENDING,
    });

    const url = replaceToken(DataUrlConstants.OAUTH_INITIATE, { connectorId });

    return get(url)
      .then((resp) => {
        let authUrl = resp.data.location;
        console.log('Opening auth window with location: ', authUrl);
        const oAuthWindow = window.open(
          authUrl,
          '_target',
          'toolbar=yes,scrollbars=yes,resizable=yes,top=150,left=500,width=650,height=750'
        );
        let timer = setInterval(function () {
          if (!oAuthWindow) {
            console.info('oAuthWindow not found, closing monitor…');
            // Close the oauth monitor when we
            // lose the main window.
            clearInterval(timer);
          } else if (oAuthWindow.closed) {
            clearInterval(timer);
            // TODO: Check if its really success before sending this
            oAuthClose({ success: true });
          }
        }, 1000);

        const oAuthDispatch = dispatch;
        window.oAuth = {
          oAuthWindow,
          oAuthDispatch,
          oAuthClose,
        };
      })
      .catch((resp) => {
        console.log('catch, resp headers: ', resp.headers);
      });
  };
}

export function oauthAuthorize(url: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.OAUTH_AUTHORIZE_PENDING,
    });

    return get(url)
      .then((resp) => {
        dispatch({
          type: ActionTypes.OAUTH_AUTHORIZE_FULFILLED,
          payload: resp,
        });
        if (window.opener && window.opener.oAuth) {
          window.opener.oAuth.oAuthClose({
            success: true,
          });
        }
      })
      .catch((resp) => {
        dispatch({
          type: ActionTypes.OAUTH_AUTHORIZE_FAILED,
          errorResp: resp.response?.data,
        });
        console.log('Failed oauth authorize: ', resp);
        if (window.opener && window.opener.oAuth) {
          window.opener.oAuth.oAuthClose({
            success: false,
            errorResp: resp.data,
          });
        }
      });
  };
}

export const getConnectorsMetadata = (): ThunkAction<void, RootState, unknown, Action<string>> => (
  dispatch: SyncariThunkDispatch
) =>
  thottlePromiseThunk('getConnectorsMetadata', () => {
    dispatch({
      type: ActionTypes.GET_CONNECTORS_METADATA_PENDING,
    });

    return get(DataUrlConstants.CONNECTORS_METADATA)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_CONNECTORS_METADATA_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((errorResp) => {
        dispatch({
          type: ActionTypes.GET_CONNECTORS_METADATA_FAILED,
          error: getErrorMessage(errorResp),
        });
      });
  });

export function setNodeTooltipMessage(message: string) {
  return {
    type: ActionTypes.SET_CONNECTOR_STATUS_MESSAGE,
    message,
  };
}

export function initializeConnectorModal() {
  return {
    type: ActionTypes.INITIALIZE_CONNECTOR_MODAL,
  };
}

export function setConnectorSetting(params: any, connectorId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SET_CONNECTORS_SETTING_PENDING,
    });
    const url = replaceToken(DataUrlConstants.SET_CONNECTOR_SETTING, {
      connectorId,
    });

    return post(url, params)
      .then((resp) => {
        dispatch({
          type: ActionTypes.SET_CONNECTORS_SETTING_FULFILLED,
          savedConnectorSettings: { autoSchemaSyncEntities: resp.data, connectorId },
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SET_CONNECTORS_SETTING_FAILED,
        });
      });
  };
}

export function addConnectorNode(connectorData: any) {
  return {
    type: ActionTypes.ADD_CONNECTOR_NODE,
    connectorData,
  };
}
