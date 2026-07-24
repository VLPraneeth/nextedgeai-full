//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { merge } from 'lodash';
import { DeepPartial } from 'redux';

import { PredicateGraphNodeUI } from 'pages/sync-studio/types';
import { RootState } from 'reducers';
import AppConstants from 'utils/AppConstants';

export const getNodePanelTestState = (updatedTestState: DeepPartial<RootState> = {}): DeepPartial<RootState> => {
  return merge(
    {
      entityPipeline: {
        dynamicConfig: getLookupSyncariRecord(),
        dynamicConfigValues: {
          '5f6e44f908fd387e82f2285f': getLookupSyncariRecord(),
        },
        dynamicConfigStatus: {
          '5f6e44f908fd387e82f2285f': AppConstants.FETCH_STATUS.SUCCESS,
        },
        connectorEntities: [
          {
            name: 'syncari',
            id: '1234',
          },
        ],
        selectedGraphNode: {},
      },
      pipelineFunction: {
        fieldPipelineFunctions: [getLookupSyncariRecord(), getFilterFunction()],
        entityPipelineFunctions: [],
      },
      pipelineAction: {
        fieldPipelineActions: [],
        entityPipelineActions: [],
      },
      picklist: {
        fetchingPicklistValues: false,
        picklistValues: {
          testing: [
            {
              label: 'test',
            },
          ],
        },
        fetchingPicklistValuesStatus: {
          abcdef: 'idle',
        },
      },
      fieldPipeline: getFieldPipeline(),
      validation: {
        errors: [],
        warnings: [],
      },
      pipelineError: {},
    },

    updatedTestState
  );
};

export const getNode = (updatedNode: DeepPartial<PredicateGraphNodeUI> = {}): PredicateGraphNodeUI => {
  return merge(
    {
      shape: 'function-node',
      label: 'Look Up Syncari Record',
      icon: '/assets/icons/functions/look-up-syncari-record.svg',
      hideLeftStrip: false,
      description: '',
      typeColor: '#4FC5C2',
      nodeType: 'FUNCTION',
      deleteable: false,
      id: '5f6e44f908fd387e82f2285f',
      x: 847,
      y: 263,
      metadata: {
        id: '5f6e44f908fd387e82f2285f',
        name: 'Look Up Syncari Record',
        apiName: 'advancedLookUpSyncariRecordOnField',
        label: 'Look Up Syncari Record',
        subLabel: '',
        inputPorts: [{ portType: 'INPUT', datatype: 'object', maxConnections: 1 }],
        outputPorts: [{ portType: 'OUTPUT', datatype: 'object', maxConnections: 1 }],
        configuration: {
          predicate: {
            predicates: [
              {
                left: {
                  datatype: 'date',
                  label: 'Date',
                  type: 'variable',
                  value: '5f619d7b5dad1b58e7893bf8',
                },
                operator: 'eq',
                right: { value: 'myvalue', type: 'literal' },
                predicateId: '5f6e450908fd387e82f2291c',
                name: 'predicate',
              },
            ],
            groupPredicateId: '5f6e450908fd387e82f2291d',
            operator: 'AND',
          },
          definition: '5f619d7e5dad1b58e7893ccc',
          syncariEntityDefId: '5f619d7b5dad1b58e7893af7',
          configId: '5f619d7e5dad1b58e7893ccc',
        },
        nodeType: 'FUNCTION',
        location: { x: 847, y: 263 },
        displayName: 'Look Up Syncari Record',
        description: '',
        deleteable: false,
        nodeId: '5f6e44f908fd387e82f2285f',
        nodeName: 'Look Up Syncari Record',
      },
    },
    updatedNode
  );
};

// Type Functions and LookupSyncariRecord
export const getLookupSyncariRecord = () => {
  return {
    id: '5f619d7e5dad1b58e7893ccc',
    name: 'advancedLookUpSyncariRecordOnField',
    displayName: 'Look Up Syncari Record',
    helpSummary: 'A function that looks up an entity using a criteria and returns the looked up object',
    helpPath: '',
    iconPath: '/assets/icons/functions/look-up-syncari-record.svg',
    outputType: 'object',
    positionalParams: [{ name: 'value', datatype: 'object' }],
    engineType: 'FUNCTION',
    scope: 'ATTRIBUTE',
    type: 'STANDARD',
    configuration: [
      {
        implicit: true,
        mapping: [{ graphKey: 'label' }],
        datatype: 'string',
        name: 'nodeLabel',
        value: 'Look Up Syncari Record',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'configuration.definition' }],
        datatype: 'string',
        name: 'functionDefinition',
        value: '5f619d7e5dad1b58e7893ccc',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'nodeType' }],
        datatype: 'string',
        name: 'nodeType',
        value: 'FUNCTION',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'inputPorts' }],
        datatype: 'object',
        name: 'inputPorts',
        value: [{ portType: 'INPUT', datatype: 'object', maxConnections: 1 }],
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'outputPorts' }],
        datatype: 'object',
        name: 'outputPorts',
        value: [{ portType: 'OUTPUT', datatype: 'object', maxConnections: 1 }],
      },
      {
        mapping: [{ graphKey: 'configuration.syncariEntityDefId', configKey: 'value' }],
        datatype: 'picklist',
        defaultValue: '',
        helpSummary: null,
        values: [
          { label: 'Account', value: '5f619d7b5dad1b58e7893af2' },
          { label: 'Activity', value: '5f619d7b5dad1b58e7893af7' },
          { label: 'Contact', value: '5f619d7b5dad1b58e7893af3' },
          { label: 'Lead', value: '5f619d7b5dad1b58e7893af4' },
          { label: 'Opportunity', value: '5f619d7b5dad1b58e7893af5' },
          { label: 'Ticket', value: '5f619d7b5dad1b58e7893af6' },
          { label: 'User', value: '5f619d7b5dad1b58e7893af8' },
        ],
        name: 'syncariEntityDefId',
        label: 'Syncari Entity',
        type: 'SyncariEntity',
      },
      {
        mapping: [{ graphKey: 'configuration.predicate' }],
        dependsOn: {
          dependantField: 'configuration.syncariEntityDefId',
          dependantType: 'syncariFilter',
        },
        datatype: 'predicate',
        defaultValue: '',
        helpSummary: null,
        name: 'predicate',
        fieldSet: 'conditionFields',
        label: 'Condition',
      },
      {
        mapping: [{ graphKey: 'configuration.field', configKey: 'value' }],
        dependsOn: {
          dependantField: 'configuration.syncariEntityDefId',
          dependantType: 'AttributeList',
        },
        datatype: 'picklist',
        defaultValue: '',
        helpSummary: null,
        name: 'field',
        fieldSet: 'conditionFields',
        label: 'Field',
      },
      {
        mapping: [{ graphKey: 'configuration.operator', configKey: 'value' }],
        dependsOn: {
          dependantField: 'configuration.field',
          dependantType: 'Operator',
        },
        datatype: 'picklist',
        defaultValue: '',
        helpSummary: null,
        name: 'operator',
        fieldSet: 'conditionFields',
        label: 'Operator',
      },
      {
        mapping: [{ graphKey: 'configuration.value' }],
        datatype: 'string',
        defaultValue: '',
        helpSummary: null,
        name: 'value',
        fieldSet: 'conditionFields',
        label: 'Value',
      },
    ],
    dynamicConfig: true,
    renderer: { renderer: 'form', steps: null },
    title: 'advancedLookUpSyncariRecordOnField',
    key: 'test_key_242',
    iconAlt: 'advancedLookUpSyncariRecordOnField',
    icon: '/assets/icons/functions/look-up-syncari-record.svg',
  };
};

// TODO: Type Functions and Filter function
export const getFilterFunction = () => {
  return {
    id: '5f619d7d5dad1b58e7893ca8',
    name: 'filter',
    displayName: 'Filter',
    helpSummary: 'A function that passes through its input if its logical expression matches',
    helpPath: '',
    iconPath: '/assets/icons/functions/filter.svg',
    outputType: 'object',
    positionalParams: [{ name: 'value', datatype: 'object' }],
    engineType: 'FUNCTION',
    scope: 'ATTRIBUTE',
    type: 'STANDARD',
    configuration: [
      {
        implicit: false,
        mapping: [{ graphKey: 'configuration.attributeDefinition', configKey: 'value' }],
        datatype: 'picklist',
        defaultValue: '',
        values: [
          {
            datatype: 'string',
            label: 'About Us',
            type: 'variable',
            value: '5f619d7b5dad1b58e7893afb',
          },
          {
            datatype: 'picklist',
            label: 'Account Source',
            type: 'variable',
            value: '5f619d7b5dad1b58e7893b1a',
          },
        ],
        name: 'field',
        fieldSet: 'conditionFields',
        label: 'Field',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'label' }],
        datatype: 'string',
        name: 'nodeLabel',
        value: 'Filter',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'configuration.definition' }],
        datatype: 'string',
        name: 'functionDefinition',
        value: '5f619d7d5dad1b58e7893ca8',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'nodeType' }],
        datatype: 'string',
        name: 'nodeType',
        value: 'FUNCTION',
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'inputPorts' }],
        datatype: 'object',
        name: 'inputPorts',
        value: [{ portType: 'INPUT', datatype: 'object', maxConnections: 1 }],
      },
      {
        implicit: true,
        mapping: [{ graphKey: 'outputPorts' }],
        datatype: 'object',
        name: 'outputPorts',
        value: [{ portType: 'OUTPUT', datatype: 'object', maxConnections: 1 }],
      },
      {
        mapping: [{ graphKey: 'configuration.predicate' }],
        datatype: 'predicate',
        defaultValue: '',
        helpSummary: null,
        name: 'predicate',
        fieldSet: 'conditionFields',
        label: 'Condition',
      },
      {
        mapping: [{ graphKey: 'configuration.operator', configKey: 'value' }],
        dependsOn: {
          dependantField: 'configuration.attributeDefinition',
          dependantType: 'Operator',
        },
        datatype: 'picklist',
        defaultValue: '',
        helpSummary: null,
        name: 'operator',
        fieldSet: 'conditionFields',
        label: 'Operator',
      },
      {
        mapping: [{ graphKey: 'configuration.value' }],
        datatype: 'string',
        defaultValue: '',
        helpSummary: null,
        name: 'value',
        fieldSet: 'conditionFields',
        label: 'Value',
        type: 'literal',
      },
    ],
    dynamicConfig: true,
    renderer: { renderer: 'form', steps: null },
    title: 'filter',
    key: 'test_key_357',
    iconAlt: 'filter',
    icon: '/assets/icons/functions/filter.svg',
  };
};

// TODO: Type connector attributes
export const getFieldPipeline = () => {
  return {
    attributeNodes: [
      {
        configuration: [
          {
            implicit: true,
            mapping: [{ graphKey: 'label' }],
            datatype: 'string',
            name: 'label',
            value: 'Sync from {attribute}',
          },
          {
            implicit: true,
            mapping: [{ graphKey: 'subLabel' }],
            datatype: 'string',
            name: 'defaultSubLabel',
            value: 'sfdcone Account',
          },
          {
            implicit: true,
            mapping: [{ graphKey: 'nodeType' }],
            datatype: 'string',
            name: 'nodeType',
            value: 'ATTRIBUTE_SOURCE',
          },
          {
            implicit: false,
            mapping: [
              { graphKey: 'configuration.attributeDefinition', configKey: 'value' },
              { graphKey: 'inputPorts', configKey: 'inputPorts' },
              { graphKey: 'outputPorts', configKey: 'outputPorts' },
              { graphKey: 'label', configKey: 'nodeLabel' },
              { graphKey: 'subLabel', configKey: 'subLabel' },
            ],
            datatype: 'picklist',
            values: [
              {
                subLabel: 'sfdcone Account',
                outputPorts: [{ portType: 'OUTPUT', datatype: 'textarea', maxConnections: 1 }],
                inputPorts: [],
                label: 'Account Description',
                value: '5f64f6e3e4ed398bb6317902',
                nodeLabel: 'Sync from Account Description',
              },
              {
                subLabel: 'sfdcone Account',
                outputPorts: [{ portType: 'OUTPUT', datatype: 'id', maxConnections: 1 }],
                inputPorts: [],
                label: 'Account ID',
                value: '5f64f6e3e4ed398bb63178e7',
                nodeLabel: 'Sync from Account ID',
              },
            ],
            name: 'attribute',
            label: 'Field',
          },
          {
            implicit: false,
            mapping: {
              graphKey: 'configuration.defaultValue',
              configKey: 'defaultValue',
            },
            datatype: 'string',
            name: 'defaultValue',
            label: 'Default Value',
          },
        ],
        connectorId: '5f64f3e9e4ed395a23da9704',
        isCoreNode: false,
        name: 'Account',
        connectorName: 'sfdcone',
        label: 'Account',
        id: '5f64f6e3e4ed398bb63178e6_source',
        iconPath: '/assets/icons/logos/salesforce.svg',
        type: 'source',
        entityDefinitionId: '5f64f6e3e4ed398bb63178e6',
      },
      {
        configuration: [
          {
            implicit: true,
            mapping: [{ graphKey: 'label' }],
            datatype: 'string',
            name: 'label',
            value: 'Sync to {attribute}',
          },
          {
            implicit: true,
            mapping: [{ graphKey: 'subLabel' }],
            datatype: 'string',
            name: 'defaultSubLabel',
            value: 'sfdcone Account',
          },
          {
            implicit: true,
            mapping: [{ graphKey: 'nodeType' }],
            datatype: 'string',
            name: 'nodeType',
            value: 'ATTRIBUTE_SINK',
          },
          {
            implicit: false,
            mapping: [
              { graphKey: 'configuration.attributeDefinition', configKey: 'value' },
              { graphKey: 'inputPorts', configKey: 'inputPorts' },
              { graphKey: 'outputPorts', configKey: 'outputPorts' },
              { graphKey: 'label', configKey: 'nodeLabel' },
              { graphKey: 'subLabel', configKey: 'subLabel' },
            ],
            datatype: 'picklist',
            values: [
              {
                subLabel: 'sfdcone Account',
                outputPorts: [],
                inputPorts: [{ portType: 'INPUT', datatype: 'textarea', maxConnections: 1 }],
                label: 'Account Description',
                value: '5f64f6e3e4ed398bb6317902',
                nodeLabel: 'Sync to Account Description',
              },
              {
                subLabel: 'sfdcone Account',
                outputPorts: [],
                inputPorts: [{ portType: 'INPUT', datatype: 'id', maxConnections: 1 }],
                label: 'Account ID',
                value: '5f64f6e3e4ed398bb63178e7',
                nodeLabel: 'Sync to Account ID',
              },
            ],
            name: 'attribute',
            label: 'Field',
          },
          {
            implicit: false,
            mapping: {
              graphKey: 'configuration.defaultValue',
              configKey: 'defaultValue',
            },
            datatype: 'string',
            name: 'defaultValue',
            label: 'Default Value',
          },
          {
            implicit: false,
            mapping: {
              graphKey: 'configuration.alwaysUseDefaultOnEmpty',
              configKey: 'alwaysUseDefaultOnEmpty',
            },
            datatype: 'boolean',
            name: 'alwaysUseDefaultOnEmpty',
            label: 'Always use when empty',
          },
        ],
        connectorId: '5f64f3e9e4ed395a23da9704',
        isCoreNode: false,
        name: 'Account',
        connectorName: 'sfdcone',
        label: 'Account',
        id: '5f64f6e3e4ed398bb63178e6_sink',
        iconPath: '/assets/icons/logos/salesforce.svg',
        type: 'sink',
        entityDefinitionId: '5f64f6e3e4ed398bb63178e6',
      },
      {
        configuration: [
          {
            implicit: false,
            mapping: [
              {
                graphKey: 'configuration.dataAuthorityStrategy',
                configKey: 'value',
              },
            ],
            datatype: 'picklist',
            values: [
              { label: 'Latest Record', value: 'LATEST_RECORD' },
              { label: 'Selected Synapse', value: 'SELECTED_CONNECTOR' },
              { label: 'None', value: 'NONE' },
            ],
            name: 'Data Authority Strategy',
          },
          {
            implicit: false,
            mapping: [{ graphKey: 'configuration.connectorId', configKey: 'value' }],
            dependsOn: {
              dependantType: 'DataAuthorityStrategy',
              dependantField: 'configuration.dataAuthorityStrategy',
            },
            datatype: 'picklist',
            name: 'Synapse',
          },
          {
            implicit: false,
            mapping: [{ graphKey: 'configuration.defaultValue' }],
            datatype: 'string',
            name: 'Default Value',
            value: null,
          },
        ],
        isCoreNode: true,
        name: 'Name',
        id: '5f619d7b5dad1b58e7893afe',
        label: 'Account Name',
        iconPath: '/icons/syncari.png',
        type: 'core',
        entityDefinitionId: '5f619d7b5dad1b58e7893af2',
      },
    ],
  };
};
