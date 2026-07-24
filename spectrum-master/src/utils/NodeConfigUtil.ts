// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { cloneDeep, each, find, get, includes, isArray, isEmpty, isUndefined, set } from 'lodash';

import { DependsOn } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { getPipelineActions, getPipelineFunctions } from 'utils/PipelineUtil';

import { isNotNullOrUndefined } from './TypeUtils';
import { makeUrl } from './UrlUtil';

const CONFIG_ID = 'configuration.configId';

const NODE_TYPE_DATA_MAP = {
  [AppConstants.NODE_TYPE.ENTITY_SINK]: 'connectorEntities',
  [AppConstants.NODE_TYPE.ENTITY_SOURCE]: 'connectorEntities',
  [AppConstants.NODE_TYPE.ATTRIBUTE_SINK]: 'attributeNodes',
  [AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE]: 'attributeNodes',
  [AppConstants.NODE_TYPE.CORE_ATTRIBUTE]: 'attributeNodes',
  [AppConstants.NODE_TYPE.FUNCTION]: 'functions',
  [AppConstants.NODE_TYPE.CORE_ENTITY]: 'connectorEntities',
  [AppConstants.NODE_TYPE.CONNECTOR_ENTITY]: 'connectorEntities',
  [AppConstants.NODE_TYPE.ACTION]: 'actions',
};

const GROUP_TYPES = [AppConstants.INPUT_TYPE.TAB];

const NODE_TYPE_KEY = 'nodeType';

const FIELD_GRAPH_VALUE_MAP = {
  direction: NODE_TYPE_KEY,
};

// Temporary node types that should have a blank value on
// initial config
const TEMPORARY_NODE_TYPES = [AppConstants.NODE_TYPE.CONNECTOR_ENTITY];

function isParentVisible(config, hiddenInputConfigs) {
  let parentVisible = true;
  each(hiddenInputConfigs, (hiddenConfig) => {
    const graphKey = getGraphKey(hiddenConfig);
    if (config?.requiredValue?.graphKey === graphKey) {
      parentVisible = false;
    }
  });
  return parentVisible;
}

function getNodeConnectorEntityKeyValues(metadata, data, config) {
  const result = [];
  config = config || getNodeConfig(metadata, data);
  const hiddenInputConfigs = [];
  if (!config) {
    return result;
  }
  // Find the value of non implicit and put the value to the array
  each(config.configuration, (conf) => {
    if (!conf.implicit || conf.name === 'nodeLabel') {
      // Check if the require value is fulfilled
      if (conf.requiredValue && !isRequiredValueFulfilled(conf, metadata)) {
        hiddenInputConfigs.push(conf);
        return;
      }

      // Check if the parent is visible before displaying
      if (!isParentVisible(conf, hiddenInputConfigs)) {
        return;
      }

      if (conf.datatype === AppConstants.INPUT_TYPE.PICKLIST || conf.datatype === AppConstants.INPUT_TYPE.MULTISELECT) {
        // Normalizing the mapping. The backend is inconsistent with the mapping configuration.
        // Sometimes it a object sometimes its an array.
        const lMapping = isArray(conf.mapping) ? conf.mapping : [conf.mapping];
        each(lMapping, (mep) => {
          if (mep.configKey === 'value') {
            let val = get(metadata, mep.graphKey);
            // Special case temporary node types
            if (mep.graphKey === NODE_TYPE_KEY && includes(TEMPORARY_NODE_TYPES, val)) {
              val = '';
            }
            let displayVal;
            if (val) {
              displayVal = find(conf.values, (valu) => {
                return valu.value === val;
              });
            } else if (conf.defaultValue) {
              val = conf.defaultValue;
            }
            result.push({
              ...conf,
              displayValue: displayVal?.label || '',
              value: displayVal?.value || val,
              name: conf.label || conf.name,
              configName: conf.name,
              defaultValue: displayVal?.value || val,
              graphKey: mep.graphKey,
            });
          }
        });
      } else {
        const graphKey = getGraphKey(conf);
        let val = get(metadata, graphKey);
        if (conf.datatype === AppConstants.INPUT_TYPE.EMAILBODY) {
          if (val) {
            try {
              val = atob(val);
            } catch (e) {
              console.error(`Value: ${val} cannot cannot be converted to string`);
            }
          }
        } else if (typeof val === 'undefined') {
          val = conf.defaultValue;
        }
        result.push({
          ...conf,
          displayValue: val,
          value: val,
          name: conf.label || conf.name,
          configName: conf.name,
          defaultValue: val,
          graphKey,
        });
      }
    }
  });

  return result;
}

export function getNodeKeyValues(metadata: any, data: any, config?: any) {
  if (!metadata) {
    return;
  }
  switch (metadata.nodeType) {
    case AppConstants.NODE_TYPE.ACTION:
    case AppConstants.NODE_TYPE.ATTRIBUTE_SINK:
    case AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE:
    case AppConstants.NODE_TYPE.CONNECTOR_ENTITY:
    case AppConstants.NODE_TYPE.CORE_ATTRIBUTE:
    case AppConstants.NODE_TYPE.CORE_ENTITY:
    case AppConstants.NODE_TYPE.ENTITY_SINK:
    case AppConstants.NODE_TYPE.ENTITY_SOURCE:
    case AppConstants.NODE_TYPE.FUNCTION:
      return getNodeConnectorEntityKeyValues(metadata, data, config);
    default:
      break;
  }
  return;
}

export function getGraphKey(config) {
  // TODO: config is undefined - crash
  if (isArray(config.mapping)) {
    const graphKey = find(config.mapping, (val, key) => {
      return !isEmpty(val.graphKey);
    });
    if (graphKey) {
      return graphKey.graphKey;
    }
  } else {
    return config?.mapping?.graphKey;
  }
}

export function getNodeKeyValue(config, nodeKeyValues) {
  let graphKey = getGraphKey(config);
  return find(nodeKeyValues, (val) => {
    return graphKey === val.graphKey;
  });
}

export function isNodeFunctionAction(node) {
  return [AppConstants.NODE_TYPE_SHAPE_MAP.FUNCTION, AppConstants.NODE_TYPE_SHAPE_MAP.ACTION].includes(node?.shape);
}

export function getNodeKeyValueByGraphKey(graphKey, nodeKeyValues) {
  return find(nodeKeyValues, (val) => {
    return val.graphKey === graphKey;
  });
}

export function getNodeConfig(node, data) {
  if (!node) {
    return;
  }

  const configList = data[NODE_TYPE_DATA_MAP[node.nodeType]];
  const nodeRoot = node.metadata ? node.metadata : node;
  const nodeId = get(nodeRoot, CONFIG_ID);

  return configList?.find((conf) => conf.id === nodeId);
}

// Call this function for in memory
export function normalizeConfigId(node) {
  let newNode = cloneDeep(node);
  if (!newNode?.configId) {
    return newNode;
  }

  const configuration = newNode?.configuration || {};
  set(configuration, AppConstants.GRAPH_CONFIG_ID_OBJECT_PATH, newNode.configId);
  newNode = {
    ...newNode,
    ...configuration,
  };
  return newNode;
}

export function getConfigId(data) {
  if (data.configId) {
    return data.configId;
  } else if (data.metadata?.configuration?.configId) {
    return data.metadata.configuration.configId;
  } else if (data.metadata?.configId) {
    return data.metadata.configId;
  }
}

/**
 * @param {Object} graph node metadata
 * @param {Object} data object to get the configurations from
 */
export function getNodeConfigGroups(metadata, data) {
  const groups = [];
  const nodeConfig = getNodeConfig(metadata, {
    ...data,
    functions: getPipelineFunctions(data.pipelineContext, {
      fieldPipelineFunctions: data.fieldPipelineFunctions,
      entityPipelineFunctions: data.entityPipelineFunctions,
    }),
    actions: getPipelineActions(data.pipelineContext, {
      fieldPipelineActions: data.fieldPipelineActions,
      entityPipelineActions: data.entityPipelineActions,
    }),
  });
  const groupChildren = {};
  each(nodeConfig?.configuration, (config) => {
    if (includes(GROUP_TYPES, config.datatype)) {
      groups.push(cloneDeep(config));
    } else {
      groupChildren[config.parentGroup] = groupChildren[config.parentGroup] || [];
      groupChildren[config.parentGroup].push(cloneDeep(config));
    }
  });

  each(groups, (group) => {
    group.children = groupChildren[group.name];
  });

  return groups;
}

export function hasDynamicConfig(nodeConfig) {
  return nodeConfig?.dynamicConfig;
}

export function getDynamicConfigParams(nodeConfig) {
  return nodeConfig?.dynamicConfigParams;
}

export function isRequiredValueFulfilled(config, value) {
  if (config.requiredValue && config.requiredValue.graphKey) {
    const val = get(value, config.requiredValue.graphKey);
    const ret = val === config.requiredValue.value;
    return ret;
  }
  return true;
}

/**
 * allows the backend to set some visibility rules for fields.
 * Visibility of a field can depend on the value of another field
 * on the same form.
 *
 * eg, only show schedule for ENTITY_SOURCE nodes
 */
export function shouldShowField(graphValues, config) {
  if (!config?.dependsOnFieldValue || !config?.visibilityDependsOnFieldValue) {
    return true;
  }

  function matchesVisibilityConfig(visibilityConfig) {
    const { fieldName, fieldValue } = visibilityConfig;

    if (FIELD_GRAPH_VALUE_MAP[fieldName]) {
      return get(graphValues, FIELD_GRAPH_VALUE_MAP[fieldName]) === fieldValue;
    } else if (isNotNullOrUndefined(get(graphValues, fieldName))) {
      try {
        return new RegExp(fieldValue).test(get(graphValues, fieldName));
      } catch {
        return get(graphValues, fieldName) === fieldValue;
      }
    } else {
      return get(graphValues, getGraphKey(config)) === fieldValue;
    }
  }
  if (isArray(config.visibilityDependsOnFieldValue)) {
    return config.visibilityDependsOnFieldValue.every((value) => matchesVisibilityConfig(value));
  } else {
    return matchesVisibilityConfig(config.visibilityDependsOnFieldValue);
  }
}

export function doesNeedConfiguration(nodeConfig?: any) {
  if (!nodeConfig) {
    return false;
  }
  if (includes([AppConstants.NODE_TYPE.FUNCTION, AppConstants.NODE_TYPE.ACTION], nodeConfig.engineType)) {
    return true;
  }
  return find(nodeConfig.configuration, (config) => {
    return config.implicit === false || isUndefined(config.implicit);
  });
}

export function findConfiguration(name, nodeConfig) {
  return nodeConfig.configuration?.find((configuration) => {
    return configuration.name === name;
  });
}

export function getDependantField(config, nodeConfig) {
  if (!config?.mapping?.graphKey) {
    return;
  }
  return nodeConfig?.configuration?.find((c) => {
    return config.mapping.graphKey === c.dependsOn?.dependantField;
  });
}

export function findConfigurationByGraphKey(graphKey, nodeConfig) {
  return nodeConfig.configuration?.find((configuration) => {
    return configuration?.mapping?.graphKey === graphKey;
  });
}

export const getGroupByFieldSetName = (configurations, fieldSetName) =>
  configurations?.filter(
    (config) => config.fieldSet === fieldSetName && !includes(AppConstants.PARENT_DATATYPE, config.datatype)
  );

// The field is always the first entry in the group config
export const getFieldInGroup = (groupConfig) => Array.isArray(groupConfig) && groupConfig[0];

// The overrideId is used to set the id directly instead of using the
// graphValues (which may not be up to date if the value recently changed)
export const makePicklistKey = (
  { dependantType, dependantField, params }: DependsOn,
  graphValues,
  overrideId?: string
) => {
  const id = overrideId || get(graphValues, dependantField);
  const key = `${dependantType}${id}`;
  return params ? makeUrl(key, {}, makePicklistParamValues(params, graphValues)) : key;
};

export const makePicklistParamValues = (params, graphValues) => {
  if (!params || !graphValues) {
    return {};
  }
  return Object.keys(params).reduce((acc, key) => {
    const value = get(graphValues, params[key].value);
    return value ? { ...acc, [params[key].name]: value } : acc;
  }, {});
};

export const isCoreNodeConfig = (config) => {
  return config.coreNodeConfig === true;
};
