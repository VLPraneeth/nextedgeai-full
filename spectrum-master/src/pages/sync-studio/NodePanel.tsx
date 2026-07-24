//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps, useMatch } from '@reach/router';
import { Button, Icon, Spin } from 'antd';
import cx from 'classnames';
import produce from 'immer';
import { find, get, includes, set, uniqueId } from 'lodash';
import { ReactNode, useCallback, useEffect, useMemo, useState } from 'react';

import {
  clearDynamicNodeConfig,
  getAsyncNodeConfig,
  setGroupConfiguration,
  showNodeConfigModal,
} from 'actions/entityPipelineActions';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import Fieldset from 'components/Fieldset';
import FieldTypeBadge from 'components/FieldTypeBadge';
import { NodeModel } from 'components/GraphItemFilter';
import Composite from 'components/inputs/composite';
import Filter from 'components/inputs/filter';
import InputContainer from 'components/inputs/InputContainer';
import SetValueField from 'components/inputs/SetValueField';
import { PicklistValue } from 'components/inputs/types';
import { Stack } from 'components/layout';
import { GraphStatus } from 'components/PipelineToolbar';
import PropertyPanelAction, { PropertyPanelActionModel } from 'components/PropertyPanelAction';
import PropertyPanelTitle from 'components/PropertyPanelTitle';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { SkullInput } from 'components/skull';
import Tabs, { Tab, TabPane } from 'components/Tabs';
import { useEnhancedBatchDispatch, useEnhancedSelector, useEnhancedSelector as useSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { selectEntityById } from 'store/entity/selectors';
import { usePicklistAdditionalConfig, usePicklistValues } from 'store/picklists/hooks';
import { PipelineSyncError } from 'store/pipeline-error/types';
import { useNodeConfig } from 'store/pipeline/hooks';
import { selectDisplayedGraph, selectPipelineActions, selectPipelineFunctions } from 'store/pipeline/selectors';
import { Node } from 'store/pipeline/types';
import { Connector } from 'store/schema/types';
import { ValidationResult } from 'store/validation/types';
import { getValidationResultsByNodeId } from 'store/validation/utils';
import AppConstants from 'utils/AppConstants';
import { noop } from 'utils/AppUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import {
  findConfiguration,
  getFieldInGroup,
  getGroupByFieldSetName,
  getNodeConfig,
  getNodeKeyValueByGraphKey,
  getNodeKeyValues,
  isNodeFunctionAction,
  makePicklistKey,
  makePicklistParamValues,
  shouldShowField,
} from 'utils/NodeConfigUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { syncariConnectorNodeTypes } from './node-config/Config';
import {
  CONFIGURATION_GRAPH_ID,
  CONFIGURATION_GRAPH_VERSION,
  NODE_LABEL_NAME,
  STANDARD_GRAPH_KEYS,
} from './node-config/constants';
import NodePanelFieldGroup from './NodePanelFieldGroup';
import { usePipelineError } from './pipeline-error/PipelineError.hooks';
import { PipelineErrorTabResults } from './pipeline-error/PipelineErrorTabResults';
import { useSelectedNodes, useUpdateSelectedNodeIdsQueryParam } from './pipeline/PipelineEditor.hooks';
import { NodeValidationTab } from './validation/NodeValidationTab';
import { createUniqueValues } from './node-config/utils';

import './NodePanel.less';

const tn = tNamespaced('NodePanel');

// TODO: Merge with GraphItem NodeModel
export interface NodeUIMetadata {
  label?: string;
  description?: string;
  configuration?: Record<string, any>;
}

export interface NodeUIModel extends Partial<Node> {
  metadata?: NodeUIMetadata;
  shape?: string;
}

// TODO: Type group configuration
export type GroupConfiguration = {
  // TODO: this is not an entirely accurate type
  children: SkullInput[];
  [index: string]: any;
};

export interface NodePanelProps extends RouteComponentProps {
  node: NodeUIModel | NodeModel;
  groupConfiguration?: GroupConfiguration;
  // TODO: Check if we can safely remove the changeKey
  changeKey?: string;
  showTitlePanel?: boolean;
  onClose?: () => void;
  actions?: PropertyPanelActionModel[];
  editable?: boolean;
  title?: string;
  graphVersion?: GraphStatus;
}

const NodePanel = ({
  node,
  groupConfiguration,
  showTitlePanel,
  onClose,
  actions,
  editable,
  title = '',
  graphVersion,
}: NodePanelProps) => {
  const [batch, dispatch] = useEnhancedBatchDispatch();
  const [picklistValues, fetchPicklistValues] = usePicklistValues();
  const currentGraph = useSelector((state) => state.pipeline.currentGraph);
  const displayedGraph = useSelector(selectDisplayedGraph);
  const selectedEntityNode = useEnhancedSelector((state) => state?.entityPipeline?.selectedGraphNode ?? {});

  const errors = useEnhancedSelector((state) => state?.validation?.errors ?? EMPTY_ARRAY);
  const warnings = useEnhancedSelector((state) => state?.validation?.warnings ?? EMPTY_ARRAY);
  const { connectorEntities } = useEnhancedSelector((state) => state.entityPipeline);

  const syncariConnectorEntity = connectorEntities.find(
    (connector: Connector) => connector?.coreNode || connector.name.toLowerCase() === 'syncari'
  );

  const { error, loading, success, config: nodeConfig, hasDynamicConfig, fetch: fetchDynamicConfig } = useNodeConfig(
    node
  );

  const { fetchAdditionalConfig, insertAdditionalConfig } = usePicklistAdditionalConfig({
    functionName: nodeConfig?.name,
  });

  const isUpdatRecordAndDynamicNode =
    nodeConfig?.name === AppConstants.SKULL_RENDER_TYPE.UPDATE_EXTERNAL_RECORD && nodeConfig?.dynamicConfig;

  // Fetch any addtional config for any input that needs it
  useEffect(() => {
    if (node?.metadata?.configuration) {
      fetchAdditionalConfig({
        configurations: nodeConfig,
        values: node?.metadata?.configuration,
      });
    }
  }, [fetchAdditionalConfig, node?.metadata?.configuration, nodeConfig, node?.id]);

  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();
  const { selectedNodeIds } = useSelectedNodes();

  const currentEntity = useSelector((state) => selectEntityById(state, nodeConfig?.entityDefinitionId));
  // TODO: Remove this
  // Some inputs does not update when the value changes.
  // Force rerender when we get an updated node or a totally different selected node
  const [inputKey, setInputKey] = useState(() => uniqueId());
  useEffect(() => {
    setInputKey(uniqueId());
  }, [node]);

  const [filteredErrors, setFilteredErrors] = useState<(ValidationResult | PipelineSyncError)[]>(EMPTY_ARRAY);
  const [filteredWarnings, setFilteredWarnings] = useState<(ValidationResult | PipelineSyncError)[]>(EMPTY_ARRAY);

  useEffect(() => {
    if (node.id) {
      setFilteredErrors(getValidationResultsByNodeId(errors, node.id));
      setFilteredWarnings(getValidationResultsByNodeId(warnings, node.id));
    }
  }, [errors, warnings, node.id]);

  useEffect(() => {
    return () => {
      batch(() => {
        dispatch(setGroupConfiguration());
        dispatch(clearDynamicNodeConfig());
      });
    };
  }, [batch, dispatch]);

  useEffect(() => {
    if (currentGraph && hasDynamicConfig && !success && !error) {
      fetchDynamicConfig(currentGraph);
    }
  }, [currentGraph, error, fetchDynamicConfig, hasDynamicConfig, success]);

  const onConfigure = useCallback(() => {
    dispatch(setGroupConfiguration(groupConfiguration));
    dispatch(showNodeConfigModal(true));
  }, [dispatch, groupConfiguration]);

  const [pipelineId, pipelineVersion] = useMemo(() => [currentGraph?.targetId, displayedGraph], [
    displayedGraph,
    currentGraph?.targetId,
  ]);

  const nodeValues = useMemo(() => {
    if (!nodeConfig) {
      return [];
    }

    const { metadata = {} } = node;
    const val = [
      ...(getNodeKeyValues(metadata, undefined, {
        ...nodeConfig,
        configuration: insertAdditionalConfig(nodeConfig.configuration, node?.metadata?.configuration),
      }) || []),
      ...[
        { graphKey: CONFIGURATION_GRAPH_ID, value: pipelineId },
        { graphKey: CONFIGURATION_GRAPH_VERSION, value: pipelineVersion },
      ],
    ];
    return val;
  }, [insertAdditionalConfig, node, nodeConfig, pipelineId, pipelineVersion]);

  const graphValues = useMemo(() => {
    return produce(node.metadata, (draft) => {
      if (draft) {
        set(draft, CONFIGURATION_GRAPH_ID, pipelineId);
        set(draft, CONFIGURATION_GRAPH_VERSION, pipelineVersion);
      }
    });
  }, [node.metadata, pipelineId, pipelineVersion]);

  const getPicklistDisplayValue = useCallback(
    (nodeValue: any) => {
      let valueNode;
      let picklistOptions: PicklistValue[] = [];
      if (nodeValue.dependsOn) {
        const { dependantType, dependantField } = nodeValue.dependsOn;
        const nodeKeyValue = getNodeKeyValueByGraphKey(dependantField, nodeValues);
        if (nodeKeyValue?.value) {
          const key = makePicklistKey(nodeValue.dependsOn, graphValues);
          const params = makePicklistParamValues(nodeValue.dependsOn.params, graphValues);
          fetchPicklistValues({
            id: key,
            dependantId: STANDARD_GRAPH_KEYS.includes(nodeValue.dependsOn?.dependantField)
              ? get(graphValues, nodeValue.dependsOn.dependantField)
              : nodeKeyValue?.value,
            dependantType,
            ...(isUpdatRecordAndDynamicNode && { sendPicklistGroup: true }),
            params,
          });
          picklistOptions = picklistValues[key];
        }
      } else if (Array.isArray(nodeValue.values)) {
        // Reference datatype has the picklist options in the `values` field
        picklistOptions = nodeValue.values;
      }
      if (Array.isArray(nodeValue.value) && Array.isArray(picklistOptions)) {
        valueNode = nodeValue.value
          .map((val: string) => {
            const picklistEntry = picklistOptions.find((picklist) => picklist.value === val);
            return picklistEntry ? picklistEntry.label : val;
          })
          .join(', ');
      } else {
        const selectedItem = find(picklistOptions || nodeValue.values, { value: nodeValue.value });
        if (selectedItem) {
          valueNode = selectedItem.label;
        }
      }
      return valueNode;
    },
    [fetchPicklistValues, graphValues, nodeValues, picklistValues]
  );

  const getKeyValueComponents = useCallback(
    (names?: string[]) => {
      return nodeValues
        .filter((nodeValue) => {
          const { visibilityDependsOnFieldValue } = nodeValue || {};
          const targetEntity = selectedEntityNode?.metadata?.configuration?.entityDefinition;

          if (names && !includes(names, nodeValue.configName)) {
            return false;
          }

          if (nodeValue.name === NODE_LABEL_NAME) {
            return false;
          }

          // Do not include supporting inputs
          if (nodeValue.fieldSet && !includes(AppConstants.PARENT_DATATYPE, nodeValue.datatype)) {
            return false;
          }

          // Do not include the standard graph keys
          if (STANDARD_GRAPH_KEYS.includes(nodeValue.graphKey)) {
            return false;
          }

          // Special exception for the Time Ticker connector entity
          // @ts-ignore
          const configId = node?.metadata?.configuration?.connectorId;
          if (
            syncariConnectorNodeTypes.includes(node.nodeType ?? '') &&
            configId === syncariConnectorEntity.id &&
            nodeValue.coreNodeConfig
          ) {
            return false;
          }

          if (Array.isArray(visibilityDependsOnFieldValue) && visibilityDependsOnFieldValue.length) {
            return visibilityDependsOnFieldValue.every((field) => {
              let actualValue;

              // Special handling for 'direction' field - it maps to nodeType
              if (field.fieldName === 'direction') {
                actualValue = node?.nodeType;
              } else if (field.fieldName.includes('.')) {
                actualValue =
                  get(graphValues, field.fieldName) ||
                  get(node?.metadata, field.fieldName) ||
                  get(node?.configuration, field.fieldName.replace('configuration.', ''));
              } else {
                actualValue = get(graphValues, field.fieldName) || get(node?.metadata, field.fieldName);
              }

              if (field.fieldValue.startsWith('^')) {
                return new RegExp(field.fieldValue).test(actualValue || '');
              } else {
                // Exact match
                return actualValue === field.fieldValue;
              }
            });
          }

          return shouldShowField(graphValues, findConfiguration(nodeValue.configName, nodeConfig));
        })
        .map((nodeValue, _idx, nodeKeyValues) => {
          const groupTitle = nodeValue.label || nodeValue.name;
          let valueNode: ReactNode | undefined;
          const { SET_VALUE_FIELD, SET_VALUE_FIELD1 } = AppConstants.SKULL_RENDER_TYPE;

          switch (nodeValue.datatype) {
            case AppConstants.INPUT_TYPE.PREDICATE:
              valueNode = (
                <Filter
                  name={`${node.id}-${nodeValue.configName}`}
                  displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
                  picklistValues={picklistValues}
                  onChange={noop}
                  onDelete={noop}
                  value={nodeValue.displayValue}
                  defaultValue={nodeValue.displayValue}
                  fetchPicklistValues={({ id, dependantId, dependantType }) =>
                    fetchPicklistValues({
                      id: id as string,
                      dependantId: dependantId as string,
                      dependantType: dependantType as string,
                    })
                  }
                  fieldValues={
                    getFieldInGroup(getGroupByFieldSetName(nodeConfig.configuration, nodeValue.fieldSet))?.values
                  }
                />
              );
              break;
            case AppConstants.INPUT_TYPE.COMPOSITE:
              if ([SET_VALUE_FIELD, SET_VALUE_FIELD1].includes(nodeValue.renderType)) {
                // Check if the node is a legacy Set Value node and convert the
                // data into the new format
                if (nodeValue.value === null) {
                  if ((graphValues as any)?.configuration?.dataType) {
                    nodeValue.value = {
                      type: 'existing',
                      dataType: (graphValues as any).configuration.dataType,
                    };
                  } else if ((graphValues as any)?.configuration?.attributeDefinitionId) {
                    nodeValue.value = {
                      type: 'existing',
                      attributeDefinitionId: (graphValues as any).configuration.attributeDefinitionId,
                    };
                  }
                }

                valueNode = (
                  <SetValueField
                    name={`${node.id}-${nodeValue.configName}`}
                    displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
                    nodeValue={nodeValue}
                    isField={nodeValue.renderType === SET_VALUE_FIELD1}
                    attributeValues={nodeValue.attributeValues}
                  />
                );
              } else {
                let compositeConfig = nodeValue.configuration;
                let defaultValue = nodeValue.displayValue;

                if (nodeValue.defaultValue?.[0]?.repeatId && !nodeValue.defaultValue?.[0]?.compositeValues) {
                  // TODO: NodeValue

                  defaultValue = { compositeValues: nodeValue.defaultValue };

                  compositeConfig = produce(nodeValue?.configuration, (draft: any) => {
                    draft?.forEach((c: any) => {
                      // For update external record with dynamic config, merge the values
                      const updateRecordConfig = isUpdatRecordAndDynamicNode
                        ? draft.find((c: any) => c.allowUserToken)
                        : null;

                      if (c.dependsOn) {
                        const { dependantField, dependantType } = c.dependsOn;

                        const dependantFieldValue = nodeKeyValues?.find((v) => v.graphKey === dependantField)?.value;

                        if (dependantFieldValue) {
                          const key = `${dependantType}${dependantFieldValue}`;
                          const dependantPicklistValues = picklistValues[key];

                          if (dependantPicklistValues) {
                            // For dynamic nodes with update record, merge the values
                            if (isUpdatRecordAndDynamicNode && updateRecordConfig?.values.length > 0) {
                              c.values = createUniqueValues([
                                ...dependantPicklistValues,
                                ...updateRecordConfig?.values,
                              ]);
                            } else {
                              c.values = dependantPicklistValues;
                            }
                          } else {
                            fetchPicklistValues({
                              id: key,
                              dependantType,
                              dependantId: dependantFieldValue,
                              ...(isUpdatRecordAndDynamicNode && { sendPicklistGroup: true }),
                            });
                          }
                        }
                      }
                    });
                  });
                }

                valueNode = (
                  <Composite
                    name={`${node.id}-${nodeValue.configName}`}
                    disabled
                    displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
                    picklistValues={picklistValues}
                    fetchPicklistValues={fetchPicklistValues}
                    configuration={compositeConfig}
                    defaultValue={defaultValue}
                    value={nodeValue.displayValue}
                  />
                );
              }

              break;
            case AppConstants.INPUT_TYPE.CASE:
              valueNode = <InputContainer {...nodeValue} displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY} />;
              break;
            case AppConstants.INPUT_TYPE.PICKLIST:
            case AppConstants.INPUT_TYPE.REFERENCE:
            case AppConstants.INPUT_TYPE.PICKLIST_COMBO:
            case AppConstants.INPUT_TYPE.MULTISELECT_FIELD:
            case AppConstants.INPUT_TYPE.MULTISELECT: {
              valueNode = getPicklistDisplayValue(nodeValue);
              break;
            }
            case AppConstants.INPUT_TYPE.PASSWORD:
              valueNode = AppConstants.PASSWORD_MASK;
              break;
          }

          const displayValue = valueNode || nodeValue.displayValue;
          return (
            <NodePanelFieldGroup
              key={`node-panel-key-${nodeValue.configName}-${inputKey}`}
              helpText={nodeValue.helpSummary}
              required={nodeValue.required}
              dataType={nodeValue.datatype}
              title={groupTitle}>
              {Array.isArray(displayValue) ? displayValue.join(', ') : displayValue}
            </NodePanelFieldGroup>
          );
        });
    },
    [
      fetchPicklistValues,
      getPicklistDisplayValue,
      graphValues,
      inputKey,
      node?.id,
      node?.metadata?.configuration?.connectorId,
      node.nodeType,
      nodeConfig,
      nodeValues,
      picklistValues,
      syncariConnectorEntity?.id,
    ]
  );

  const nodeDataType = currentEntity?.fields.find((field) => field.id === pipelineId)?.dataType;

  const pipelineFunctions = useEnhancedSelector(selectPipelineFunctions);

  const pipelineActions = useEnhancedSelector(selectPipelineActions);

  const nodeInfo = useMemo(() => {
    const config = getNodeConfig(node, {
      functions: pipelineFunctions,
      actions: pipelineActions,
    });

    if ('shape' in node && config && isNodeFunctionAction(node)) {
      return {
        displayName: config.displayName,
        isFunction: node?.shape === AppConstants.NODE_TYPE_SHAPE_MAP.FUNCTION,
        isAction: node?.shape === AppConstants.NODE_TYPE_SHAPE_MAP.ACTION,
      };
    }
    return {};
  }, [node, pipelineActions, pipelineFunctions]);

  const nodeConfigPanel = useMemo(() => {
    if (groupConfiguration) {
      const groupFieldNames = groupConfiguration.children
        .filter((config) => config.parentGroup === groupConfiguration.name)
        .map((config) => config.name);

      return <Stack className="synri-group-configuration">{getKeyValueComponents(groupFieldNames)}</Stack>;
    }

    const nodeLabelConfig = nodeValues.find((nval) => nval.name === NODE_LABEL_NAME);

    return (
      <Stack className="synri-node-panel-field-values">
        {isNodeFunctionAction(node) && nodeInfo?.displayName ? (
          <NodePanelFieldGroup title={nodeInfo?.isFunction ? tn('function_name') : tn('action_name')}>
            {nodeInfo?.displayName}
          </NodePanelFieldGroup>
        ) : null}
        <NodePanelFieldGroup title={tn('label')} helpText={nodeLabelConfig?.helpSummary}>
          {node.label ?? ''}
        </NodePanelFieldGroup>
        {getKeyValueComponents()}
      </Stack>
    );
  }, [getKeyValueComponents, groupConfiguration, node, nodeInfo?.displayName, nodeInfo?.isFunction, nodeValues]);

  const showActions = !groupConfiguration && node?.id && !error && actions && actions.length > 0;

  const NodeConfiguration = (
    <Spin tip={tc('loading')} spinning={loading}>
      {showActions && <PropertyPanelAction actions={actions} />}
      {node?.id && error ? (
        <EmptyGraphPanel
          className="synri-node-panel-error"
          panelIcon={<Icon type="exclamation-circle" theme="filled" />}
          onActionClick={() => node.id && dispatch(getAsyncNodeConfig(node.id, currentGraph))}
          actionText={tc('retry')}>
          <Stack spacing="xxs">
            <span>{tn('unexpected_error')}</span>
            <span>{error}</span>
          </Stack>
        </EmptyGraphPanel>
      ) : groupConfiguration ? (
        <div className={cx('node-configuration-panel node-config-container', !showActions && 'no-actions')}>
          <ScrollableArea>
            {editable && (
              <div className="configure-container">
                <Button onClick={onConfigure} data-userflow-tag={UserflowTags.SyncStudio.ConfigureNode}>
                  {tn('configure')}
                </Button>
              </div>
            )}
            {nodeConfigPanel}
          </ScrollableArea>
        </div>
      ) : (
        <Fieldset
          key={`fieldset-${title}`}
          className={cx('node-configuration-panel', !showActions && 'no-actions')}
          title={tn('node_configuration')}>
          <ScrollableArea>{nodeConfigPanel}</ScrollableArea>
        </Fieldset>
      )}
    </Spin>
  );

  const NodeValidation = (
    <TabPane
      tab={
        <Tab>
          {tn('validation', {
            numberOfValidationResults: filteredErrors.length + filteredWarnings.length,
          })}
        </Tab>
      }
      key="node-validation-tab">
      <NodeValidationTab
        errors={filteredErrors as ValidationResult[]}
        warnings={filteredWarnings as ValidationResult[]}
      />
    </TabPane>
  );

  const entityValidationMatch = useMatch('/sync-studio/entity/:entityId/pipeline/:graphVersion/validation');
  const entityNodeMatch = useMatch('/sync-studio/entity/:entityId/pipeline/:graphVersion');
  const fieldValidationMatch = useMatch(
    '/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion/validation'
  );
  const fieldNodeMatch = useMatch('/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion');
  // TODO Fix the bookmark
  const entityPipelineErrorMatch = useMatch('/sync-studio/entity/:entityId/pipeline/:graphVersion/pipeline-error');
  const fieldPipelineErrorMatch = useMatch(
    '/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion/pipeline-error'
  );

  const getBaseUrl = useCallback(
    (key: string) => {
      if (fieldValidationMatch || fieldNodeMatch || fieldPipelineErrorMatch) {
        switch (key) {
          case 'node-validation-tab':
            return RouteConstants.FIELD_PIPELINE_VALIDATION;
          case 'pipeline-error-tab':
            return RouteConstants.FIELD_PIPELINE_ERROR;
          default:
            return RouteConstants.FIELD_PIPELINE_GRAPH_VERSION;
        }
      } else {
        switch (key) {
          case 'node-validation-tab':
            return RouteConstants.ENTITY_PIPELINE_VALIDATION;
          case 'pipeline-error-tab':
            return RouteConstants.ENTITY_PIPELINE_ERROR;
          default:
            return RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION;
        }
      }
    },
    [fieldNodeMatch, fieldPipelineErrorMatch, fieldValidationMatch]
  );

  const onTabChange = useCallback(
    (key: string) => {
      const match =
        fieldValidationMatch ||
        fieldNodeMatch ||
        entityValidationMatch ||
        entityNodeMatch ||
        entityPipelineErrorMatch ||
        fieldPipelineErrorMatch;

      const url = makeUrl(getBaseUrl(key), {
        entityId: match?.entityId,
        graphVersion: match?.graphVersion,
        fieldId: match?.fieldId,
      });

      updateSelectedNodeIdsQueryParam(selectedNodeIds, url);
    },
    [
      entityNodeMatch,
      entityPipelineErrorMatch,
      entityValidationMatch,
      fieldNodeMatch,
      fieldPipelineErrorMatch,
      fieldValidationMatch,
      getBaseUrl,
      selectedNodeIds,
      updateSelectedNodeIdsQueryParam,
    ]
  );

  const hideValidationTab =
    (selectedEntityNode && selectedEntityNode.shape === AppConstants.NODE_TYPE_SHAPE_MAP.CORE_ENTITY) ||
    graphVersion?.toUpperCase() === AppConstants.GRAPH_STATUS.APPROVED;
  const hideValidationAndPipelineError =
    selectedEntityNode && selectedEntityNode.shape === AppConstants.NODE_TYPE_SHAPE_MAP.CORE_ENTITY;

  const { syncErrors } = usePipelineError({ nodeId: node.id });

  const activeKey =
    entityValidationMatch || fieldValidationMatch
      ? 'node-validation-tab'
      : entityPipelineErrorMatch || fieldPipelineErrorMatch
      ? 'pipeline-error-tab'
      : 'node-configuration-tab';

  return (
    <>
      {showTitlePanel !== false && (
        <PropertyPanelTitle
          icon={nodeDataType && <FieldTypeBadge dataType={nodeDataType} />}
          // TODO: add margin fix to 'synri-node-panel-title'
          className="synri-node-panel-title synri-node-panel-title--no-bottom-margin"
          title={title}
          onClose={onClose}
          helpPath={nodeConfig?.helpPath}
        />
      )}
      {hideValidationAndPipelineError ? (
        NodeConfiguration
      ) : (
        <Tabs
          // TODO: Fix the bookmark
          activeKey={activeKey}
          onChange={onTabChange}>
          {/*
           ** TODO: wrap NodeConfigurationTab sub-component in TabPane when we remove the feature flag.
           ** We wrap the component here to ensure that the component works when the feature
           ** flag is not enabled.
           */}
          <TabPane tab={<Tab>{tn('configuration')}</Tab>} key="node-configuration-tab">
            {NodeConfiguration}
          </TabPane>
          {hideValidationTab ? (
            <TabPane
              tab={<Tab>{tn('pipeline_status', { errorCount: syncErrors?.length ?? 0 })}</Tab>}
              key="pipeline-error-tab">
              <PipelineErrorTabResults nodeId={node?.id} />
            </TabPane>
          ) : (
            NodeValidation
          )}
        </Tabs>
      )}
    </>
  );
};

export default NodePanel;
