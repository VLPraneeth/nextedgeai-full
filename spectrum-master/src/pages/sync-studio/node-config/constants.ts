import AppConstants from 'utils/AppConstants';

export const NODE_LABEL_NAME = 'nodeLabel';

export const RAW_VALUE_DATATYPE = [
  AppConstants.INPUT_TYPE.BOOLEAN,
  AppConstants.INPUT_TYPE.COMPOSITE,
  AppConstants.INPUT_TYPE.DATE,
  AppConstants.INPUT_TYPE.DATETIME,
  AppConstants.INPUT_TYPE.DOUBLE,
  AppConstants.INPUT_TYPE.DOUBLE,
  AppConstants.INPUT_TYPE.EMAILBODY,
  AppConstants.INPUT_TYPE.EMAILLIST,
  AppConstants.INPUT_TYPE.INTEGER,
  AppConstants.INPUT_TYPE.PASSWORD,
  AppConstants.INPUT_TYPE.PREDICATE,
  AppConstants.INPUT_TYPE.STRING,
  AppConstants.INPUT_TYPE.TEXTAREA,
  AppConstants.INPUT_TYPE.CASE,
];

// Override of datatype for dependant types
export const OVERRIDE_DEPENDANT_DATATYPE_MAP: Record<string, string> = {
  [AppConstants.INPUT_TYPE.TEXT]: AppConstants.INPUT_TYPE.TEXTAREA,
};

export const CONFIG_MULTI_VALUES = [AppConstants.INPUT_TYPE.MULTISELECT, AppConstants.INPUT_TYPE.MULTISELECT_FIELD];

// Note: Backend calls it targetId but the UI doesn't know anything about the targetId
// For the UI, its graph id
export const CONFIGURATION_GRAPH_ID = 'configuration.targetId';
export const CONFIGURATION_GRAPH_VERSION = 'configuration.graphVersion';
export const STANDARD_GRAPH_KEYS = [CONFIGURATION_GRAPH_ID, CONFIGURATION_GRAPH_VERSION];
