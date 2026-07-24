import { ChangesInProgressModalVariants } from 'components/modals/ChangesInProgressModal';
import { SwitchCaseConfigurationValue } from 'components/switch-case/SwitchCase.types';
import { Group, Node } from 'store/pipeline/types';
import { tNamespaced } from 'utils/i18nUtil';

export const CLEAR_ERROR_MESSAGE = 'CLEAR_ERROR_MESSAGE';
export const NAVIGATING_TO = 'NAVIGATING_TO';

export const PHONE_HOME_PENDING = 'PHONE_HOME_PENDING';
export const PHONE_HOME_FULFILLED = 'PHONE_HOME_FULFILLED';
export const PHONE_HOME_FAILED = 'PHONE_HOME_FAILED';

export const SET_GLOBAL_JS_ERROR = 'SET_GLOBAL_JS_ERROR';
export const SET_NODE_FOR_KEBAB_MENU = 'SET_NODE_FOR_KEBAB_MENU';
export const CLEAR_NODE_FOR_KEBAB_MENU = 'CLEAR_NODE_FOR_KEBAB_MENU';

export const APPLICATION_ERROR = 'APPLICATION_ERROR';

export const SET_CHANGES_IN_PROGRESS = 'SET_CHANGES_IN_PROGRESS';
export const SET_CHANGES_IN_PROGRESS_MODAL = 'SET_CHANGES_IN_PROGRESS_MODAL';

const tn = tNamespaced('ApplicationErrors');

export enum ErrorMessage {
  applicationError = 'applicationError',
  gatewayTimeout = 'gatewayTimeout',
  resourceNotFound = 'resourceNotFound',
}

export const getApplicationErrorMessage = (key: ErrorMessage): string => {
  // We need to build this object dynamically so the translations are applied
  const applicationErrors: Record<ErrorMessage, string> = {
    gatewayTimeout: tn('gatewayTimeout'),
    resourceNotFound: tn('resourceNotFound'),
    applicationError: tn('applicationError'),
  };

  return applicationErrors[key];
};

export interface ConnectorActionModel {
  id: string;
  shape: string;
  icon: string;
  typeColor: string;
  label: string;
  x: number;
  y: number;
  status: string;
  statusErrorMessage: string | null;
  statusErrorDetails: string | null;
}

export type KebabNodeConnector = {
  nodeType: 'connector';
  connector: ConnectorActionModel;
};

export type KebabNodeGroup = {
  nodeType: 'group';
  group: Group;
};

export type KebabNode = {
  nodeType: 'node';
  node: Node;
};

export type EdgeOptions = {
  nodeType: 'predicate-node';
  node: Node;
  sourceFunctionId: string;
  sourceConfiguration?: SwitchCaseConfigurationValue;
};

export interface AppState {
  changesInProgress: boolean;
  changesInProgressModal: {
    visible: boolean;
    variant: null | undefined | ChangesInProgressModalVariants;
    discardChangesAction: () => void;
    keepEditingAction: () => void;
  };
  errorMessage: string | null;
  errorTitle: string | null;
  globalErrorMessage: string | null;
  kebabMenuNode?: KebabNodeConnector | KebabNodeGroup | KebabNode | EdgeOptions;
  globalErrorStack: string | null;
  lastActions: any[];
  navigatingTo: string | null;
  phoneHomeErrorMessage: string | null;
  phoneHomeId: string | null;
  sendingPhoneHome: boolean;
}
