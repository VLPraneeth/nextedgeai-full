//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce from 'immer';
import { cloneDeep, find, map } from 'lodash';

import { ActionTypes } from 'actions/connectorActions';
import { DraftStatuses } from 'store/insights-studio/types';
import { AuthTypes } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { ResponseError } from 'utils/AppUtil';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';
import { ValuesOf } from 'utils/TypeUtils';

function _transformConnectors(data: Connector[]) {
  const connectors: Connector[] = [];
  map(data, (connector) => {
    if (connector.status !== AppConstants.CONNECTOR_STATUS.DELETED) {
      const newConnector = {
        // @ts-ignore: we're manually setting key here, in case it's not already defined
        key: connector.id,
        // @ts-ignore: we're manually setting connectorId here, in case it's not already defined
        connectorId: connector.id,
        ...connector,
      };
      if (connector.draft && connector.draft.id) {
        newConnector.draftId = connector.draft.id;
      }
      connectors.push(newConnector);
    }
  });
  return connectors;
}

function _getConnectors(data?: Connector[]) {
  let connectors: Connector[] = [];

  if (data) {
    connectors = _transformConnectors(data);
  }

  return { connectors };
}

export function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.CONNECTOR),
    connectors: undefined,
    fetchingConnectors: false,
    connectorEntities: [],
    fetchingConnectorSettings: false,
    connectorActivated: false,
  };
}

export interface AuthConfig {
  accessToken: string | null;
  clientId: string | null;
  clientSecret: string | null;
  consumerKey: string | null;
  consumerSecret: string | null;
  endpoint: string | null;
  expiresIn: string | number | null;
  lastRefreshed: string | null;
  password: string | null;
  redirectUri: string | null;
  refreshToken: string | null;
  token: string | null;
  tokenId: string | null;
  tokenSecret: string | null;
  userName: string | null;
}

type Entity = {};

export type SchemaRefreshStatus = null | 'NEW' | 'SUCCESS' | 'ERROR' | 'PROCESSING';

export type ConnectorStatus = ValuesOf<typeof AppConstants.CONNECTOR_STATUS>;

export interface Connector {
  apiConfig: { dailyQuota: number };
  authConfig: AuthConfig;
  autoSchemaSyncEntities: any[];
  connectorId: string;
  createdAt: string;
  createdBy: string;
  displayName: string;
  iconUri: string;
  backgroundColor: string;
  draft: any;
  draftId?: string;
  endpoint: string;
  entities?: Entity[];
  errorDetails?: string | null;
  errorMessage?: string | null;
  icon?: string;
  id: string;
  key: string;
  metaConfig: {
    authType?: AuthTypes;
  };
  metadataId: string;
  schemaRefreshStatus: SchemaRefreshStatus;
  name: string;
  oauthRedirectUrl?: string | null;
  setting: { syncRate: number; apiQuota: number; bootstrapWithSyncari: boolean };
  status: ConnectorStatus;
  updatedAt: string;
  updatedBy: string;
}

export interface AuthTypeConfigFields {
  name: keyof AuthConfig;
  dataType: 'text' | 'password';
  label: string;
  helpSummary: string | null;
  required?: boolean;
}

interface AuthTypeConfig {
  authType: AuthTypes;
  fields: AuthTypeConfigFields[];
  label: string;
  helpSummary: string;
}

export interface ConfigureFields {
  name: string;
  dataType: string;
  label: string;
  helpSummary: string | null;
  required?: boolean;
}

export type ConnectorCapability =
  | 'create'
  | 'update'
  | 'delete'
  | 'search'
  | 'getById'
  | 'getByWatermark'
  | 'noWatermark'
  | 'compositeId'
  | 'schemaEditInSyncari'
  | 'schemaCreateField'
  | 'userEditableId'
  | 'userEditableWm'
  | 'userEditableReadOnly';

export interface ConnectorMetadata {
  id: string;
  name: string;
  type: string | null;
  displayName: string | null;
  blankDraggable?: boolean;
  configId: string;
  description: string | null;
  category: string | null;
  iconUri: string | null;
  backgroundColor?: string;
  helpUrl: string | null;
  idFieldName: string | null;
  watermarkFieldName: string | null;
  createdAtFieldName: string | null;
  updatedAtFieldName: string | null;
  watermarkCustomizable: boolean;
  defaultApiLimit: number;
  supportedAuthTypes: AuthTypeConfig[] | null;
  configureFields: ConfigureFields[];
  watermarkFieldOverrides: Record<string, string | null>;
  disabledMessage: string | null;
  oauthUri: string | null;
  capabilities?: ConnectorCapability[];
  creatable?: boolean;
  hideFromSynapseList?: boolean;
  custom?: boolean;
  draftStatus?: DraftStatuses;
  httpSource?: boolean;
  webhook?: boolean;
}

export interface ConnectorState {
  activatedConnectorId?: string;
  addConnectorNode: any;
  connectorActivateErrorMessage?: string;
  connectorActivated: boolean;
  connectorActivating: boolean;
  connectorCreating: boolean;
  connectorDeactivateErrorMessage?: string;
  connectorDeactivating: boolean;
  connectorDeleteErrorMessage?: string;
  connectorDeleting: boolean;
  connectorId?: string;
  connectorEntities?: any[];
  connectorModalVisible: boolean;
  webhookLogsModalVisible: boolean;
  connectorRecord: {};
  connectorSettingModalVisible: boolean;
  nodeTootipMessage?: string;
  connectorTestErrorMessage?: string;
  connectorTesting: boolean;
  connectors: Connector[];
  connectorsMetadata: ConnectorMetadata[];
  createConnectorErrorMessage?: string;
  createdConnectorId?: string;
  fetchingConnectorSettings: boolean;
  fetchingConnectors: boolean;
  fetchingConnectorsMetadata: boolean;
  gettingConnector: boolean;
  modalConnectorMetadata: ConnectorMetadata;
  modalMode: any;
  nodeIdToRemove?: string;
  oAuthAuthorizing: boolean;
  oAuthErrorMsg?: string;
  oAuthErrorData?: {};
  oAuthErrorMessage?: string;
  oAuthData?: any;
  oauthRedirectUrlErrorMessage?: string;
  oauthRedirectUrlGetting: boolean;
  connectorsError: undefined | ResponseError;
  connectorsMetadataError: undefined | ResponseError;
}

interface Action {
  type: string;
  [index: string]: any;
}

export default function connectorReducer(state: ConnectorState = _getDefaultState(), action: Action) {
  switch (action.type) {
    case ActionTypes.GET_CONNECTORS_PENDING:
      return {
        ...state,
        fetchingConnectors: true,
        fetchingConnectorSettings: true,
        connectorActivated: false,
        connectorsError: undefined,
      };
    case ActionTypes.GET_CONNECTORS_FULFILLED:
      return {
        ...state,
        fetchingConnectors: false,
        fetchingConnectorSettings: false,
        connectorsError: undefined,
        ..._getConnectors(action.connectors),
      };
    case ActionTypes.GET_CONNECTORS_FAILED:
      return {
        ...state,
        connectorsError: action.error,
        fetchingConnectors: false,
      };
    case ActionTypes.SET_CONNECTORS_SETTING_PENDING:
      return {
        ...state,
        fetchingConnectorSettings: true,
      };
    case ActionTypes.SET_CONNECTORS_SETTING_FULFILLED: {
      let connectorData = cloneDeep(state.connectors);
      for (var i = 0; i < connectorData.length; i++) {
        if (connectorData[i].connectorId === action.savedConnectorSettings.connectorId) {
          connectorData[i].autoSchemaSyncEntities = action.savedConnectorSettings.autoSchemaSyncEntities;
        }
      }

      return {
        ...state,
        connectors: [...connectorData],
        connectorSettingModalVisible: false,
        fetchingConnectorSettings: false,
      };
    }
    case ActionTypes.SET_CONNECTORS_SETTING_FAILED:
      return {
        ...state,
        fetchingConnectorSettings: false,
      };
    case ActionTypes.SHOW_CONNECTOR_MODAL:
      return {
        ...state,
        connectorModalVisible: action.visible,
      };
    case ActionTypes.SHOW_WEBHOOK_LOGS_MODAL:
      return {
        ...state,
        webhookLogsModalVisible: action.visible,
      };
    case ActionTypes.SHOW_CONNECTOR_SETTING_MODAL:
      return {
        ...state,
        connectorSettingModalVisible: action.visible,
        connectorRecord: action.record,
      };
    case ActionTypes.OAUTH_AUTHORIZE_PENDING:
      return {
        ...state,
        oAuthAuthorizing: true,
        oAuthErrorMsg: '',
      };
    case ActionTypes.OAUTH_AUTHORIZE_FULFILLED:
      return {
        ...state,
        oAuthAuthorizing: false,
        oAuthErrorMsg: '',
        oAuthData: action.payload,
      };
    case ActionTypes.OAUTH_AUTHORIZE_FAILED:
      return {
        ...state,
        oAuthErrorMsg: 'Failed OAuth',
        oAuthErrorData: action.errorResp,
      };
    case ActionTypes.OAUTH_GET_REDIRECT_URL_PENDING:
      return {
        ...state,
        oauthRedirectUrlGetting: true,
        oauthRedirectUrlErrorMessage: '',
      };
    case ActionTypes.OAUTH_GET_REDIRECT_URL_FULFILLED:
      return {
        ...state,
        oauthRedirectUrl: action.payload.oauthRedirectUrl,
        oauthRedirectUrlGetting: false,
        connectorId: action.payload.id,
        createdConnectorId: action.payload.id,
      };
    case ActionTypes.OAUTH_GET_REDIRECT_URL_FAILED:
      return {
        ...state,
        oauthRedirectUrlErrorMessage: action.error.errorMessage,
        oauthRedirectUrlGetting: false,
      };
    case ActionTypes.INITIALIZE_CONNECTOR_MODAL:
      return {
        ...state,
        connectorId: undefined,
        oauthRedirectUrl: undefined,
        oAuthErrorData: undefined,
        oAuthErrorMsg: undefined,
        oauthRedirectUrlErrorMessage: undefined,
        connectorTestErrorMessage: undefined,
        createConnectorErrorMessage: undefined,
      };
    case ActionTypes.GET_CONNECTORS_METADATA_PENDING:
      return {
        ...state,
        connectorsMetadataError: undefined,
        fetchingConnectorsMetadata: true,
      };
    case ActionTypes.GET_CONNECTORS_METADATA_FULFILLED:
      return {
        ...state,
        fetchingConnectorsMetadata: false,
        connectorsMetadataError: undefined,
        connectorsMetadata: action.payload,
      };
    case ActionTypes.GET_CONNECTORS_METADATA_FAILED:
      return {
        ...state,
        connectorsMetadataError: action.error,
        fetchingConnectorsMetadata: false,
      };
    case ActionTypes.CREATE_CONNECTOR_PENDING:
      return {
        ...state,
        connectorCreating: true,
        createConnectorErrorMessage: '',
      };
    case ActionTypes.CREATE_CONNECTOR_FULFILLED:
      return {
        ...state,
        connectorCreating: false,
        createConnectorErrorMessage: '',
        createdConnectorId: action.payload.id,
        connectorId: action.payload.id,
      };
    case ActionTypes.CREATE_CONNECTOR_FAILED:
      return {
        ...state,
        connectorCreating: false,
        createConnectorErrorMessage: action.error.errorMessage,
      };
    case ActionTypes.GET_CONNECTOR_PENDING:
      return {
        ...state,
        gettingConnector: true,
      };
    case ActionTypes.GET_CONNECTOR_FULFILLED:
      return {
        ...state,
        gettingConnector: false,
      };
    case ActionTypes.ADD_CONNECTOR_NODE:
      return {
        ...state,
        addConnectorNode: action.connectorData,
      };
    case ActionTypes.SET_MODAL_MODE:
      return {
        ...state,
        modalMode: action.modalMode,
        modalConnectorMetadata: action.connectorMetadata,
        connectorId: action.connectorId,
      };
    case ActionTypes.TEST_CONNECTOR_PENDING:
      return {
        ...state,
        connectorTestErrorMessage: '',
        connectorTesting: true,
      };
    case ActionTypes.TEST_CONNECTOR_FULFILLED:
      return {
        ...state,
        connectorTestErrorMessage: '',
        connectorTesting: false,
        connectorId: action.connectorId,
      };
    case ActionTypes.TEST_CONNECTOR_FAILED:
      return {
        ...state,
        connectorTesting: false,
        connectorTestErrorMessage: action.error.errorMessage,
      };
    case ActionTypes.TEST_CONNECTOR_RESET:
      return {
        ...state,
        connectorTesting: false,
        connectorTestErrorMessage: '',
      };
    case ActionTypes.REMOVE_CONNECTOR_NODE:
      return {
        ...state,
        nodeIdToRemove: action.nodeIdToRemove,
      };
    case ActionTypes.SET_CONNECTOR_STATUS_MESSAGE:
      return {
        ...state,
        nodeTootipMessage: action.message,
      };

    case ActionTypes.ACTIVATE_CONNECTOR_PENDING:
      return {
        ...state,
        connectorActivating: true,
        connectorActivateErrorMessage: '',
        createPipelines: action.createPipelines,
        activatedConnectorId: undefined,
      };
    case ActionTypes.ACTIVATE_CONNECTOR_FULFILLED:
      return {
        ...state,
        connectorActivating: false,
        connectorActivateErrorMessage: '',
        createPipelines: action.createPipelines,
        connectorActivated: false,
      };
    case ActionTypes.ACTIVATE_CONNECTOR_FAILED:
      return {
        ...state,
        connectorActivating: false,
        createPipelines: action.createPipelines,
        connectorActivateErrorMessage: action.error.errorMessage,
      };
    case ActionTypes.DEACTIVATE_CONNECTOR_PENDING:
      return {
        ...state,
        connectorDeactivating: true,
        connectorDeactivateErrorMessage: '',
      };
    case ActionTypes.DEACTIVATE_CONNECTOR_FULFILLED:
      return {
        ...state,
        connectorDeactivating: false,
        connectorDeactivateErrorMessage: '',
      };
    case ActionTypes.DEACTIVATE_CONNECTOR_FAILED:
      return {
        ...state,
        connectorDeactivating: false,
        connectorDeactivateErrorMessage: action.error.errorMessage,
      };
    case ActionTypes.DELETE_CONNECTOR_PENDING:
      return {
        ...state,
        connectorDeleting: true,
        connectorDeleteErrorMessage: '',
      };
    case ActionTypes.DELETE_CONNECTOR_FULFILLED:
      return {
        ...state,
        connectorDeleting: false,
        connectorDeleteErrorMessage: '',
      };
    case ActionTypes.DELETE_CONNECTOR_FAILED:
      return {
        ...state,
        connectorDeleting: false,
        connectorDeleteErrorMessage: action.error.errorMessage,
      };
    case ActionTypes.CONNECTOR_ACTIVATED:
      return {
        ...state,
        connectorActivateErrorMessage: '',
        activatedConnectorId: action.payload?.connectorId,
      };
    case ActionTypes.REFRESH_SCHEMA:
      return produce(state, (draft) => {
        const connectorData = find(draft.connectors, { id: action.payload.connectorId });

        if (connectorData) {
          connectorData.schemaRefreshStatus = 'PROCESSING';
        }
      });

    case ActionTypes.REFRESH_SCHEMA_COMPLETED:
      return produce(state, (draft) => {
        const connectorData = find(draft.connectors, { id: action.payload.connectorId });

        if (connectorData) {
          connectorData.schemaRefreshStatus = 'SUCCESS';
        }
      });

    case ActionTypes.REFRESH_SCHEMA_FAILED:
      return produce(state, (draft) => {
        const connectorData = find(draft.connectors, { id: action.payload.connectorId });

        if (connectorData) {
          connectorData.schemaRefreshStatus = 'ERROR';
        }
      });

    case ActionTypes.CONNECTOR_ACTIVATION_FAILED:
      return {
        ...state,
        connectorActivating: false,
        activatedConnectorId: action.payload?.connectorId,
        connectorActivateErrorMessage: action.payload?.errorMessage,
      };

    case ActionTypes.GET_CONNECTOR_ENTITIES_PENDING:
      return {
        ...state,
        fetchingConnectorSettings: true,
      };
    case ActionTypes.GET_CONNECTOR_ENTITIES_FULFILLED:
      let connectorData = cloneDeep(state.connectors || []);

      for (let i = 0; i < connectorData.length; i++) {
        if (connectorData[i].connectorId === action.payload.connectorId) {
          connectorData[i].entities = action.payload.entities;
        }
      }
      return {
        ...state,
        fetchingConnectorSettings: false,
        connectors: [...connectorData],
      };

    default:
      return state;
  }
}
