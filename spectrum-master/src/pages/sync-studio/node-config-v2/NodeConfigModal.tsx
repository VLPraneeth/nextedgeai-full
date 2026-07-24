//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

/**
 * To see NodeConfigModal in the application:
 *    1) go to Sync Studio
 *    2) Select an entity and edit pipeline
 *    3) Select any node other than the Syncari entity node
 *    4) Click "Configure Node"
 *    5) ConfigForm should slide in from right side
 */

import { Button, Spin } from 'antd';
import cx from 'classnames';
import produce from 'immer';
import {
  cloneDeep,
  delay,
  each,
  filter,
  find,
  first,
  get,
  includes,
  isEmpty,
  isString,
  isUndefined,
  kebabCase,
  set,
} from 'lodash';
import { forwardRef, useCallback, useEffect, useMemo, useState } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { getAsyncNodeConfig, setNodeConfig, showNodeConfigModal } from 'actions/entityPipelineActions';
import { fetchPicklistValues, getPicklistValues } from 'actions/picklistActions';
import EmailInput from 'components/EmailInput';
import HelpLink from 'components/HelpLink';
import InlineMessage from 'components/InlineMessage';
import { CompositeValues } from 'components/inputs/composite/types';
import Filter from 'components/inputs/filter';
import InputContainer from 'components/inputs/InputContainer';
import InputProxy from 'components/inputs/InputProxy';
import { isSupportedDatatype } from 'components/inputs/InputProxy/InputProxy';
import TokenizableFieldGroup from 'components/inputs/TokenizableFieldGroup';
import { isValidSingleTokenValue, isValidTokenValue } from 'components/inputs/tokens/utils';
import { HStack, Stack } from 'components/layout';
import Modal from 'components/Modal';
import { RichTextInput } from 'components/rich-text-input';
import { SkullConfig } from 'components/skull';
import { Text } from 'components/typography';
import { RootState } from 'reducers/index';
import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import { usePicklistAdditionalConfig } from 'store/picklists/hooks';
import {
  selectAttributeNodes,
  selectConnectorEntities,
  selectDisplayedGraph,
  selectPipelineActions,
  selectPipelineFunctions,
} from 'store/pipeline/selectors';
import { NodeConfiguration } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { t, tc } from 'utils/i18nUtil';
import {
  getDynamicConfigParams,
  getGraphKey,
  getNodeConfig,
  getNodeKeyValue,
  getNodeKeyValues,
  hasDynamicConfig,
  isCoreNodeConfig,
  isRequiredValueFulfilled,
  makePicklistKey,
  makePicklistParamValues,
  shouldShowField,
} from 'utils/NodeConfigUtil';
import { NON_PRINTABLE_REGEX } from 'utils/RegexUtil';
import { getTokens, hasToken, humanize, replaceToken } from 'utils/StringUtil';

import {
  CONFIG_MULTI_VALUES,
  CONFIGURATION_GRAPH_ID,
  CONFIGURATION_GRAPH_VERSION,
  NODE_LABEL_NAME,
  OVERRIDE_DEPENDANT_DATATYPE_MAP,
  RAW_VALUE_DATATYPE,
  STANDARD_GRAPH_KEYS,
} from '../node-config/constants';
import { isTokenSupportedDatatype } from '../node-config/utils';
import { usePipelineEditor } from '../pipeline/v2/context/PipelineEditorV2.context';
import { PipelineNodeV2 } from '../pipeline/v2/types/BackendPipeline.types';

import './NodeConfigModal.scss';

export type NodeKeyValue = Record<string, any>;

type NodeKeyValues = NodeKeyValue[];

const BooleanInputContainer = forwardRef<HTMLDivElement, any>((props, ref) => (
  <div ref={ref} className="boolean-container">
    <InputProxy {...props} />
  </div>
));

const RichTextInputContainer = forwardRef<HTMLDivElement, any>((props, ref) => (
  <div ref={ref} className="rich-text-input">
    <RichTextInput {...props} />
  </div>
));

const isTokensSupported = (datatype: any, renderType: string, fieldName: string) => {
  if (!isTokenSupportedDatatype(datatype)) {
    return false;
  }
  if (renderType === AppConstants.INPUT_RENDER_TYPE.SCHEDULE) {
    return false;
  }

  return true;
};

const connector = connect(
  (state: RootState) => ({
    nodeConfigModalVisible: state.entityPipeline.nodeConfigModalVisible,
    connectorEntities: selectConnectorEntities(state),
    attributeNodes: selectAttributeNodes(state),
    pipelineActions: selectPipelineActions(state),
    pipelineFunctions: selectPipelineFunctions(state),
    currentGraph: state.pipeline.currentGraph,
    dynamicConfigStatus: state.entityPipeline.dynamicConfigStatus,
    dynamicConfigValues: state.entityPipeline.dynamicConfigValues,
    groupConfiguration: state.entityPipeline.groupConfiguration,
    displayedGraph: selectDisplayedGraph(state),
    ...state.picklist,
  }),
  (dispatch) =>
    bindActionCreators(
      {
        showNodeConfigModal,
        setNodeConfig,
        getPicklistValues,
        getAsyncNodeConfig,
        fetchPicklistValues,
      },
      dispatch
    )
);

export const NodeConfigModal = ({
  attributeNodes,
  connectorEntities,
  currentGraph,
  dynamicConfigStatus,
  dynamicConfigValues,
  getAsyncNodeConfig,
  getPicklistValues,
  groupConfiguration,
  nodeConfigModalVisible,
  pipelineActions,
  pipelineFunctions,
  setNodeConfig,
  showNodeConfigModal,
  fetchPicklistValues,
  picklistValues,
  displayedGraph,
  ...rest
}: any) => {
  const context = usePipelineEditor();
  const selectedNodeData = context.selectedGraphNode?.data?.fullNode || (EMPTY_OBJECT as PipelineNodeV2);

  // tracking visibility locally so that the component does not get
  // unmounted before animation finishes
  const [isVisible, setIsVisible] = useState(nodeConfigModalVisible);
  const [fieldValues, setFieldValues] = useState<Record<string, any>>(() => ({}));
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    if (!context.selectedGraphNode) {
      setIsVisible(false);
    }
  }, [context.selectedGraphNode]);

  const staticNodeConfig = useMemo(
    () =>
      getNodeConfig(selectedNodeData, {
        connectorEntities,
        attributeNodes,
        functions: pipelineFunctions,
        actions: pipelineActions,
      }),
    [selectedNodeData, connectorEntities, attributeNodes, pipelineFunctions, pipelineActions]
  );

  const _getStaticNodeConfig = () => staticNodeConfig;

  const {
    fetchAdditionalConfig,
    insertAdditionalConfig,
    insertAdditionalConfigInFunctions,
  } = usePicklistAdditionalConfig({ functionName: staticNodeConfig?.name });

  const getPicklistValueInputDetails = useCallback(
    (graphValues, dependsOn) => {
      const dependantFieldVal = get(graphValues, dependsOn.dependantField);
      const key = makePicklistKey(dependsOn, graphValues);

      const values = picklistValues[key] || rest[key];

      return {
        picklistValuesExist: !!values,
        values,
        dependantFieldVal,
      };
    },
    [picklistValues, rest]
  );

  // GraphValues contains initial value from the node
  // as well as the updates from the user
  const [graphValues, setGraphValues] = useState<Record<string, any>>({ configuration: {} });

  const nodeKeyValues = useMemo(() => {
    return getNodeKeyValues(
      {
        ...selectedNodeData,
        label: selectedNodeData.label,
      },
      {
        connectorEntities: insertAdditionalConfigInFunctions(connectorEntities, graphValues?.configuration),
        attributeNodes,
        functions: insertAdditionalConfigInFunctions(pipelineFunctions, graphValues?.configuration),
        actions: pipelineActions,
      }
    ) as NodeKeyValues;
  }, [
    attributeNodes,
    connectorEntities,
    graphValues?.configuration,
    insertAdditionalConfigInFunctions,
    selectedNodeData,
    pipelineActions,
    pipelineFunctions,
  ]);

  useEffect(() => {
    if (!Object.keys(graphValues.configuration).length) {
      const newGraphKeyValues = {};
      each(nodeKeyValues, (val) => {
        if (val.graphKey) {
          set(newGraphKeyValues, val.graphKey, val.value);
        }
      });

      // Set values for our standard graph values
      set(newGraphKeyValues, CONFIGURATION_GRAPH_ID, currentGraph?.targetId);
      set(newGraphKeyValues, CONFIGURATION_GRAPH_VERSION, displayedGraph);

      setGraphValues(newGraphKeyValues);
    }
  }, [currentGraph?.targetId, displayedGraph, graphValues, nodeKeyValues]);

  // Fetch the dynamic node configuration if needed
  useEffect(() => {
    const nodeConfig = _getStaticNodeConfig();
    if (nodeConfig && _hasDynamicConfig(nodeConfig)) {
      getAsyncNodeConfig(selectedNodeData.id, currentGraph, getDynamicConfigParams(nodeConfig));
    }
    // This only needs to be done once since the state here
    // needed to get the async configuration cannot be changed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fetch any addtional config for any input that needs it
  useEffect(() => {
    const nodeConfig = _getStaticNodeConfig();

    if (selectedNodeData?.configuration) {
      fetchAdditionalConfig({
        configurations: nodeConfig,
        values: selectedNodeData?.configuration,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedNodeData]);

  // handle visibility locally, after visiblility changes, we'll call the actual close fn
  const close = useCallback(() => {
    setIsVisible(false);
  }, []);

  const save = () => {
    if (!graphValues.label && graphValues.nodeLabel) {
      graphValues.label = graphValues.nodeLabel;
    }

    let newGraphValues = cloneDeep(graphValues);
    // Make sure we have the implicit values
    if (!newGraphValues?.configuration) {
      newGraphValues = addImplicitValues(_getNodeConfig()?.configuration, newGraphValues);
    }

    if (newGraphValues.configuration && selectedNodeData?.configuration) {
      newGraphValues.configuration = {
        ...selectedNodeData?.configuration,
        ...newGraphValues.configuration,
      };
    }
    const { nodeType, label } = selectedNodeData;
    newGraphValues.label = _getNewLabel(newGraphValues, label);

    if (_canChangeLabel(selectedNodeData) && !isEmpty(fieldValues?.nodeLabel)) {
      newGraphValues.label = fieldValues.nodeLabel;
    }
    newGraphValues.name = newGraphValues.label;

    newGraphValues = _processEmailBodyValues(newGraphValues);

    context.saveNodeConfiguration({
      id: selectedNodeData.id,
      nodeType,
      ...newGraphValues,
    });

    close();
  };

  const _processEmailBodyValues = (newGraphValues: NodeKeyValue) => {
    return produce(newGraphValues, (draft) => {
      const configuration = _getNodeConfig()?.configuration;
      each(configuration, (config) => {
        if (config.datatype === AppConstants.INPUT_TYPE.EMAILBODY) {
          const graphKey = getGraphKey(config);
          const value = get(draft, graphKey);
          if (value) {
            // Remove any non printable characters
            set(draft, graphKey, btoa(unescape(encodeURIComponent(value.replace(NON_PRINTABLE_REGEX, '')))));
          }
        }
      });
    });
  };

  const _canChangeLabel = (graphNode: any) => {
    if (includes([AppConstants.NODE_TYPE.FUNCTION, AppConstants.NODE_TYPE.ACTION], graphNode?.nodeType)) {
      return true;
    }
  };

  const _getNewLabel = (_graphValues: NodeKeyValue, defaultLabel: string) => {
    let newLabel = defaultLabel;
    if (_graphValues?.label && hasToken(_graphValues.label)) {
      const tokens = getTokens(_graphValues.label);
      const keyVals = getNodeKeyValues(_graphValues, {
        connectorEntities,
        attributeNodes,
        functions: pipelineFunctions,
        actions: pipelineActions,
      });
      const tokenKeyVals: any = {};
      each(tokens, (token) => {
        // Get the label
        const keyVal = find(keyVals, (keyVal) => {
          return keyVal.configName === token;
        });
        if (keyVal) {
          tokenKeyVals[token] = keyVal.displayValue;
        }
      });
      newLabel = replaceToken(_graphValues.label, tokenKeyVals)?.trim();
    }

    return newLabel;
  };

  const addImplicitValues = (configs: any, values: any) => {
    return produce(values, (draft: any) => {
      each(configs, (config: any) => {
        if (config.implicit) {
          // If mapping is an array, we are expecting one value
          // since its an implicit input
          if (config.mapping?.length > 0) {
            set(draft, first(config.mapping as any[]).graphKey, config.value);
          } else {
            draft[config.name] = config.value;
          }
        }
      });
    });
  };

  const _onInputChange = (eventObj: any) => {
    const { name: key, value } = eventObj.target;

    setFieldValues((prev) => ({
      ...prev,
      [key]: value,
    }));

    _updatePrimaryValue(key, value);
  };

  const _onSelectInputChange = (inputName: string, key: string, value?: any, _eventObj?: any) => {
    setFieldValues((prev) => ({
      ...prev,
      [key]: value,
    }));
    _updatePrimaryValue(inputName, value);
  };

  const getPicklistValuesIfNotCached = (nodeConfig: NodeConfiguration, dependantId: string) => {
    if (nodeConfig.dependsOn && dependantId) {
      const key = makePicklistKey(nodeConfig.dependsOn, graphValues, dependantId);
      if (!picklistValues[key]) {
        getPicklistValues({
          dependantId: STANDARD_GRAPH_KEYS.includes(nodeConfig.dependsOn.dependantField)
            ? get(graphValues, nodeConfig.dependsOn.dependantField)
            : dependantId,
          dependantType: nodeConfig.dependsOn.dependantType,
          params: makePicklistParamValues(nodeConfig.dependsOn.params, graphValues),
          key,
        });
      }
    }
  };

  /**
   * This function is called when a value changes. It checks to see if any other
   * fields (items in that particular node configuration array) rely on this
   * value. If so, refetch the picklist endpoint to get updated values.
   */
  const refetchPicklistForDependantNodesOnChange = (
    dependantId: string,
    nodeConfigs: NodeConfiguration[],
    changedConfig: NodeConfiguration
  ) => {
    // dependantId gets added to the url so needs to be a non-empty string
    if (!dependantId || !isString(dependantId)) {
      return;
    }

    const changedNodeGraphKey = getGraphKey(changedConfig);

    nodeConfigs
      .filter((nodeConfig) => {
        return (
          ([AppConstants.INPUT_TYPE.PICKLIST, AppConstants.INPUT_TYPE.MULTISELECT] as string[]).includes(
            nodeConfig.datatype
          ) &&
          nodeConfig?.dependsOn?.dependantField &&
          nodeConfig.dependsOn.dependantType &&
          nodeConfig.dependsOn.dependantField === changedNodeGraphKey &&
          !nodeConfig.values
        );
      })
      .map((nodeConfig) => getPicklistValuesIfNotCached(nodeConfig, dependantId));
  };

  const _updatePrimaryValue = (inputName: string, value: any) => {
    const node = _getNodeConfig();
    if (!node) {
      return;
    }

    const configSource = find(node.configuration, (config) => {
      return config.name === inputName;
    });

    const gValues = cloneDeep(graphValues);

    if (includes(RAW_VALUE_DATATYPE, configSource.datatype) || isValidTokenValue(value)) {
      const key = getGraphKey(configSource);
      if (key) {
        set(gValues, key, value);
      }
    } else {
      let selectedValue: any;

      if (CONFIG_MULTI_VALUES.includes(configSource.datatype)) {
        selectedValue = { value };
      } else {
        let values = configSource.values;

        if (configSource.dependsOn) {
          values = getPicklistValueInputDetails(gValues, configSource.dependsOn).values;
        }

        // Allow partial and raw value from the picklist combo
        if (!selectedValue && configSource.datatype === AppConstants.INPUT_TYPE.PICKLIST_COMBO) {
          selectedValue = find(values, (val) => {
            return val.value.toLowerCase() === value.toLowerCase();
          });
          if (!selectedValue) {
            selectedValue = { value };
          }
        } else {
          selectedValue = find(values, (val) => {
            return val.value === value;
          });
        }

        selectedValue = cloneDeep(selectedValue);
        if (selectedValue && !selectedValue?.key) {
          selectedValue.key = selectedValue.value;
        }
      }

      // Support array and object configuration mapping from the server
      const mappings = Array.isArray(configSource.mapping)
        ? configSource.mapping
        : Object.keys(configSource.mapping || {})?.length
        ? [configSource.mapping]
        : [];
      each(mappings, (mapping) => {
        let value = selectedValue?.[mapping.configKey];

        const key = mapping.graphKey;

        if (CONFIG_MULTI_VALUES.includes(configSource.datatype)) {
          if (value) {
            set(gValues, key, value);
          }
        } else {
          set(gValues, key, value);
        }
      });
    }

    setGraphValues(addImplicitValues(node.configuration, gValues));
    refetchPicklistForDependantNodesOnChange(value, getConfigurations(node.configuration), configSource);
    fetchAdditionalConfig({
      configurations: node as SkullConfig,
      values: gValues?.configuration ?? {},
    });
  };

  const getConfigurations = (configurations: any[]) => {
    return configurations
      ?.map((config) => config.configuration)
      .filter(Boolean)
      .flat()
      .concat(configurations);
  };

  const _getComposite = (config: any, nodeKeyValues: NodeKeyValues) => {
    // Inject the picklist values of the fields that is depending on another field.
    const configuration = produce(config?.configuration, (draft: any) => {
      draft?.forEach((config: any) => {
        if (config.dependsOn) {
          config.values = getPicklistValueInputDetails(graphValues, config.dependsOn).values;
        }
      });
    });

    // Tranform our default value to what the composite is expecting
    const defaultValue = {
      compositeValues: fieldValues?.[config.name]
        ? fieldValues[config.name]
        : getNodeKeyValue(config, nodeKeyValues)?.value,
    };

    const inputProps = {
      ...config,
      ...fieldValues,
      datatype: AppConstants.INPUT_TYPE.COMPOSITE,
      name: config.name,
      configuration,
      fetchPicklistValues,
      renderType: config.renderType,
      picklistValues,
      repeatable: config.repeatable,
      onChange: ({ name, compositeValues }: { name: string; compositeValues: CompositeValues }) => {
        setFieldValues((prev) => ({
          ...prev,
          [name]: compositeValues,
        }));
        _updatePrimaryValue(name, compositeValues);
      },
      defaultValue,
    };

    // Check if the node is a legacy Set Value node and add the legacy values
    // into the setValueField and defaultValue props
    if (
      [AppConstants.SKULL_RENDER_TYPE.SET_VALUE_FIELD, AppConstants.SKULL_RENDER_TYPE.SET_VALUE_FIELD1].includes(
        config.renderType
      ) &&
      !inputProps.setValueField
    ) {
      if (defaultValue.compositeValues?.type) {
        inputProps.setValueField = defaultValue.compositeValues;
      } else if (graphValues?.configuration?.dataType) {
        const setValueFieldValue = { type: 'existing', dataType: graphValues?.configuration?.dataType };
        inputProps.setValueField = setValueFieldValue;
        inputProps.defaultValue.compositeValues = setValueFieldValue;
      } else if (graphValues?.configuration?.attributeDefinitionId) {
        const setValueFieldValue = {
          type: 'existing',
          attributeDefinitionId: graphValues.configuration.attributeDefinitionId,
        };
        inputProps.setValueField = setValueFieldValue;
        inputProps.defaultValue.compositeValues = setValueFieldValue;
      }
    }

    return [InputContainer, inputProps];
  };

  const getInputAndProps = (config: any, nodeKeyValues: NodeKeyValues, skipGroups = true, onChange = undefined) => {
    // Skip implicits
    if (config.name !== NODE_LABEL_NAME && config.implicit) {
      return;
    }

    // Skip group with fieldsets
    if (config.fieldSet && !includes(AppConstants.PARENT_DATATYPE, config.datatype) && skipGroups) {
      return;
    }

    // Skip tabs
    if (config.datatype === AppConstants.INPUT_TYPE.TAB) {
      return;
    }

    let inputProps: any = {
      defaultValue: config.defaultValue,
      datatype: config.datatype,
      name: config.name,
      allowClear: config.allowClear,
      disabled: config.readOnly,
      // TODO: maybe not use renderType for TOKENS in the future?
      renderType:
        RAW_VALUE_DATATYPE.includes(config.datatype) && !config.renderType
          ? AppConstants.INPUT_RENDER_TYPE.TOKENS
          : config.renderType,
    };

    // Support for renderType and datatype dependant inputs
    if (['renderType', 'datatype'].includes(config.dependsOn?.dependantType)) {
      const dependantConfig = _getNodeConfig()?.configuration?.find((configuration: any) => {
        return configuration?.mapping?.[0]?.graphKey === config.dependsOn.dependantField;
      });

      // HACK: Specific handling for the newValue field to get the correct
      // datatype from the setValueField which has a legacy format and a current format.
      if (config.name === 'newValue' && _getNodeConfig()?.name === 'setValue') {
        const dataType = graphValues?.configuration?.setValueField?.dataType || graphValues?.configuration?.dataType;

        // If the datatype is a string or missing we should render as textarea
        if (!dataType || dataType === 'text') {
          inputProps.datatype = 'textarea';
        } else {
          inputProps.datatype = dataType;
        }
      } else if (dependantConfig) {
        const dependantValue =
          fieldValues[dependantConfig.name] || getNodeKeyValue(dependantConfig, nodeKeyValues)?.value;
        if (dependantValue && typeof dependantValue === 'string') {
          inputProps.datatype = OVERRIDE_DEPENDANT_DATATYPE_MAP[dependantValue] || dependantValue;
        }
      }
    }

    switch (inputProps.datatype) {
      case AppConstants.INPUT_TYPE.REFERENCE:
      case AppConstants.INPUT_TYPE.PICKLIST:
      case AppConstants.INPUT_TYPE.PICKLIST_COMBO:
      case AppConstants.INPUT_TYPE.MULTISELECT_FIELD:
      case AppConstants.INPUT_TYPE.MULTISELECT: {
        // Get the key for the main value
        let optionValues: Record<string, string>[] = [];
        if (config.datatype === AppConstants.INPUT_TYPE.REFERENCE) {
          optionValues = config.values;
        } else {
          if (config.values) {
            optionValues = config.values;
          } else if (config.dependsOn) {
            const { values } = getPicklistValueInputDetails(graphValues, config.dependsOn);
            optionValues = values;

            if (!values) {
              delay(() => {
                const dependantFieldVal = get(graphValues, config.dependsOn.dependantField);
                // Calling getPicklistValuesIfNotCached directly for the initial
                // render to pull picklist values
                getPicklistValuesIfNotCached(config, dependantFieldVal);
              }, 10);
            }
          }
        }

        const name = config.name;
        const inputName = config.name;
        let nodeKeyValue = getNodeKeyValue(config, nodeKeyValues);
        let defaultValue = inputProps.defaultValue;

        if (CONFIG_MULTI_VALUES.includes(config.datatype)) {
          defaultValue = nodeKeyValue?.value ?? defaultValue ?? EMPTY_ARRAY;
        } else {
          if (nodeKeyValue) {
            defaultValue = nodeKeyValue?.value ?? defaultValue;
          }
        }

        if (inputProps.datatype === AppConstants.INPUT_TYPE.PICKLIST) {
          // Clear out the picklist value if it's not in the list of available values
          const currentValue = fieldValues[name] || defaultValue;
          if (currentValue && optionValues?.length) {
            const optionValue = optionValues?.find((optionDatum) => optionDatum.value === currentValue);
            inputProps.value = optionValue ? optionValue.value : '';
          }
        }

        inputProps = {
          ...inputProps,
          style: { width: '100%' },
          defaultValue,
          optionData: optionValues,
          onChange: onChange || _onSelectInputChange.bind(this, inputName, name),
        };
        break;
      }
      case AppConstants.INPUT_TYPE.STRING:
      case AppConstants.INPUT_TYPE.TEXTAREA:
      case AppConstants.INPUT_TYPE.INTEGER:
      case AppConstants.INPUT_TYPE.PASSWORD: {
        const keyVal = getNodeKeyValue(config, nodeKeyValues) || {};
        const defaultValue = keyVal?.value;
        const value = fieldValues?.[config.name];
        inputProps = {
          ...inputProps,
          onChange: onChange || _onInputChange,
          value: typeof value === 'string' ? value : defaultValue,
        };

        break;
      }
      case AppConstants.INPUT_TYPE.EMAILLIST:
        return _getEmailInput(config, nodeKeyValues);
      case AppConstants.INPUT_TYPE.EMAILBODY:
        return _getRichTextInput(config);
      case AppConstants.INPUT_TYPE.PREDICATE:
        return _getFilter(config, nodeKeyValues);
      case AppConstants.INPUT_TYPE.BOOLEAN:
        return _getBoolean(config, nodeKeyValues, inputProps);
      case AppConstants.INPUT_TYPE.COMPOSITE:
        return _getComposite(config, nodeKeyValues);
      case AppConstants.INPUT_TYPE.CASE:
        return _getInputContainer(config, nodeKeyValues);
      default:
        inputProps = {
          ...inputProps,
          // Default to string if its not a known datatype
          datatype: isSupportedDatatype(inputProps.datatype) ? inputProps.datatype : AppConstants.INPUT_TYPE.STRING,
          name: config.name,
          value: fieldValues?.[config.name] ?? getNodeKeyValue(config, nodeKeyValues)?.value,
        };
        break;
    }

    return [
      InputProxy,
      {
        ...inputProps,
        onChange: _onInputProxyChange,
      },
    ];
  };

  const _onInputProxyChange = (value: any, fieldName: string) => {
    setFieldValues((prev) => ({
      ...prev,
      [fieldName]: value,
    }));

    _updatePrimaryValue(fieldName, value);
  };

  const _getNodeConfig = (): Record<string, any> | null => {
    const nodeConfig = _getStaticNodeConfig();

    if (!nodeConfig) {
      return null;
    }

    if (_hasDynamicConfig(nodeConfig) && dynamicConfigValues?.[selectedNodeData.id]) {
      return {
        ...dynamicConfigValues[selectedNodeData.id],
        configuration: insertAdditionalConfig(
          dynamicConfigValues[selectedNodeData.id].configuration,
          graphValues?.configuration
        ),
      };
    }

    return {
      ...nodeConfig.configuration,
      configuration: insertAdditionalConfig(nodeConfig.configuration, graphValues?.configuration),
    };
  };

  const _hasDynamicConfig = (nodeConfig?: any) => {
    if (!nodeConfig) {
      nodeConfig = _getStaticNodeConfig();
    }
    return hasDynamicConfig(nodeConfig);
  };

  const _isRequiredValueFulfilled = (config: any) => {
    return isRequiredValueFulfilled(config, { ...selectedNodeData, ...graphValues });
  };

  const _isParentVisible = (config: any, hiddenInputs: any) => {
    let parentVisible = true;
    each(hiddenInputs, (hiddenConfig) => {
      const graphKey = getGraphKey(hiddenConfig);
      if (
        graphKey &&
        config?.requiredValue?.graphKey === graphKey &&
        (isUndefined(hiddenConfig.coreNodeConfig) || hiddenConfig.coreNodeConfig === false)
      ) {
        parentVisible = false;
      }
    });
    return parentVisible;
  };

  const _getInputs = () => {
    const inputs: any[] = [];
    const inputObjects = _getNodeConfig();
    const hiddenInputs: any[] = [];

    let configNames: string[] | undefined;
    if (groupConfiguration) {
      configNames = configNames || [];
      each(groupConfiguration.children, (config) => {
        if (config.parentGroup === groupConfiguration.name) {
          configNames?.push(config.name);
        }
      });
    }

    if (!inputObjects) {
      return null;
    }

    each(inputObjects?.configuration, (config) => {
      let includeInput = true;
      // This is a workaround to not display the value field for predicate
      // nodes. We should have this driven by the backend but we can't use
      // implicit since the value is set by the user in the pipeline.
      if (inputObjects.name === 'predicate' && config.name === 'value') {
        return;
      }

      // This is a workaround to not display a duplicate datatype field that was
      // being rendered as a part of change to make the setValue datatype's BE driven.
      if (inputObjects.name === 'setValueOnEntity' && config.name === 'dataType') {
        includeInput = false;
      }

      // Check if there is a group only show the fields that are
      // in the selected group
      if (configNames && !includes(configNames, config.name)) {
        includeInput = false;
      }

      // Uneditable configuration are not visible in the form
      if (config.uneditable === true) {
        includeInput = false;
      }

      // Check if the require value is fulfilled
      if (config.requiredValue && !_isRequiredValueFulfilled(config)) {
        includeInput = false;
      }
      // Check if the parent is visible before displaying
      if (!_isParentVisible(config, hiddenInputs)) {
        includeInput = false;
      }

      if (!shouldShowField(graphValues, config)) {
        includeInput = false;
      }

      if (isCoreNodeConfig(config)) {
        includeInput = false;
      }

      if (includeInput) {
        const inputAndProps = getInputAndProps(config, nodeKeyValues);
        const label = _getLabel(config);

        if (inputAndProps) {
          const [InputComponent, inputProps] = inputAndProps;

          inputs.push(
            <TokenizableFieldGroup
              key={`input-${inputProps.name}`}
              hideTokenPicker={config.hideTokenPicker}
              // Don't allow tokens for node labels
              disableTokens={!isTokensSupported(config.datatype, inputProps.renderType, inputProps.name)}
              fallbackOnTokenSelect={(token) => _onInputProxyChange(token.token, inputProps.name)}
              className={cx(`node-config-${kebabCase(inputProps.name)}`, 'node-config-input-container')}
              id={inputProps.id}
              name={inputProps.name}
              required={config.required}
              tooltip={config.helpText || config.helpSummary}
              label={label}>
              <InputComponent
                showTokenSelector={false}
                setHasError={(hasError: any) => setHasError(hasError)}
                hasError={hasError}
                {...inputProps}
              />
            </TokenizableFieldGroup>
          );
        }
      } else {
        hiddenInputs.push(config);
      }
    });

    return inputs;
  };

  const _getLabel = (config: any) => {
    if (!config) {
      return null;
    }

    if (config.name === NODE_LABEL_NAME) {
      return t('NodePanel.label');
    }

    return config.label || config.name;
  };

  const _getRichTextInput = (config: any) => {
    const graphKey = getGraphKey(config);
    let value = '';
    if (graphKey) {
      value = get(graphValues, graphKey);
    }

    return [
      RichTextInputContainer,
      {
        onChange: _onInputChange,
        name: config.name,
        value,
      },
    ];
  };

  const _getGroupConfig = (fieldSetName: string) => {
    const node = _getNodeConfig();
    if (node) {
      const groupConfig = filter(node.configuration, (config) => {
        return config.fieldSet === fieldSetName && !includes(AppConstants.PARENT_DATATYPE, config.datatype);
      });
      return groupConfig;
    }
  };

  const _getInputContainer = (config: any, nodeKeyValues: NodeKeyValue) => {
    const defaultValue = getNodeKeyValue(config, nodeKeyValues)?.defaultValue;

    return [
      InputContainer,
      {
        ...config,
        defaultValue,
        onChange: (targetValue: any) => {
          _onSelectInputChange(config.name, config.name, targetValue);
        },
      },
    ];
  };

  const _getFilter = (config: any, nodeKeyValues: NodeKeyValues) => {
    let groupConfig = _getGroupConfig(config.fieldSet);
    let nodeKeyValue = getNodeKeyValue(config, nodeKeyValues);
    let firstConfig = first(groupConfig);
    let fieldValues = firstConfig?.values;

    const allowUserToken = staticNodeConfig?.configuration?.find((config: any) => config.name === firstConfig.name)
      ?.allowUserToken;

    if (firstConfig?.dependsOn) {
      const { values, picklistValuesExist, dependantFieldVal } = getPicklistValueInputDetails(
        graphValues,
        firstConfig.dependsOn
      );

      if (!picklistValuesExist) {
        getPicklistValuesIfNotCached(firstConfig, dependantFieldVal);
      }

      fieldValues = values;
    }

    return [
      Filter,
      {
        key: `node-form-filter-${config.name}`,
        lhsDisabled: config.lhsDisabled,
        allowUserToken,
        name: config.name,
        operatorType: config.operatorType,
        fetchPicklistValues,
        picklistValues,
        fieldValues: groupConfig?.[0]?.values || fieldValues,
        onChange: _onSelectInputChange,
        value: nodeKeyValue.value,
      },
    ];
  };

  // TODO: Shouldn't this use our regular inputs?
  const _getBoolean = (config: any, nodeKeyValues: NodeKeyValues, inputProps: any) => {
    const keyValue = find(nodeKeyValues, (value) => {
      return value.configName === config.name;
    });

    const defaultValue = typeof keyValue?.value !== 'undefined' ? keyValue.value : config.defaultValue;
    const value = fieldValues[config.name] ?? keyValue?.value ?? defaultValue;
    if (typeof value !== 'boolean' && !isValidSingleTokenValue(value)) {
      // If boolean field has a non-boolean value or not a single token, default
      // the value back to the initial value (or false) to prevent having an
      // undefined or leftover string value when switched from another datatype.

      let newValue = false;
      if (typeof keyValue?.value === 'boolean') {
        newValue = keyValue.value;
      } else if (typeof defaultValue === 'boolean') {
        newValue = defaultValue;
      }

      setFieldValues((prev) => ({ ...prev, [config.name]: newValue }));
      _updatePrimaryValue(config.name, newValue);
    }

    return [
      BooleanInputContainer,
      {
        ...inputProps,
        value,
        defaultValue,
        onChange: _onInputProxyChange,
      },
    ];
  };

  const _getEmailInput = (config: any, nodeKeyValues: NodeKeyValues) => {
    const keyVal = getNodeKeyValue(config, nodeKeyValues) || [];
    let value = fieldValues[config.name];

    if (isUndefined(value)) {
      value = keyVal.value;
    }

    if (!Array.isArray(value)) {
      // EmailInput needs a defaultValue of array, not a single string
      value = [value];
    }

    return [
      EmailInput,
      {
        onChange: (targetValue: any) => _onSelectInputChange(config.name, config.name, targetValue),
        name: config.name,
        value: value.filter(Boolean),
      },
    ];
  };

  const title = useMemo(() => {
    const name = groupConfiguration
      ? humanize(groupConfiguration.label || groupConfiguration.name)
      : selectedNodeData.label;
    // TODO: Support connectorEntityName in v2
    // : selectedNodeData.connectorEntityName || selectedNodeData.label;

    return (
      <HStack justify="space-between" align="center" className="full-width node-config-modal-header">
        <Text size="lg">Configure {name}</Text>
        {staticNodeConfig?.helpPath && <HelpLink helpPath={staticNodeConfig.helpPath} />}
      </HStack>
    );
  }, [groupConfiguration, selectedNodeData, staticNodeConfig?.helpPath]);

  const footer = (
    <>
      <Button key="cancel" onClick={close}>
        Cancel
      </Button>
      <Button key="ok" type="primary" onClick={save} disabled={hasError}>
        Save
      </Button>
    </>
  );

  return (
    <Modal
      afterClose={() => {
        showNodeConfigModal(false);
      }}
      className="node-config-modal"
      footer={footer}
      mask
      maskClosable={false}
      onCancel={close}
      title={title}
      visible={isVisible}>
      <div>
        {staticNodeConfig?.displayName && staticNodeConfig?.helpSummary && (
          <InlineMessage allowMultiline initallyExpanded type="info">
            {`${staticNodeConfig.displayName}: ${staticNodeConfig.helpSummary}`}
          </InlineMessage>
        )}
        {_hasDynamicConfig() && !dynamicConfigValues?.[selectedNodeData.id] ? (
          <Spin
            tip={tc('loading')}
            spinning={dynamicConfigStatus?.[selectedNodeData.id] === AppConstants.FETCH_STATUS.LOADING}>
            <div />
          </Spin>
        ) : (
          <Stack>{_getInputs()}</Stack>
        )}
      </div>
    </Modal>
  );
};

export default connector(NodeConfigModal);
