//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { merge } from 'lodash';

import { CompositeValueContainer } from 'components/inputs/composite/types';
import { OperatorValue, PicklistValues } from 'components/inputs/types';
import { FieldDataType } from 'components/types';
import { EntityPipelineState } from 'store/entity-pipeline/types';
import { NodeConfiguration } from 'store/pipeline/types';
import { makeFakeToken } from 'store/tokens/utils';

export const getValue = (update: Partial<CompositeValueContainer>): CompositeValueContainer => {
  return merge(
    {
      repeatId: '6010a31c83107c49275123ad',
      newValue: { name: 'newValue', value: '' },
      updateField: { name: 'updateField', value: '600a0072a7c745cb6fa840e7' },
    },
    update
  );
};

export const getConfiguration = (): NodeConfiguration[] => [
  {
    mapping: { graphKey: 'configuration.updateField', configKey: 'value' },
    dependsOn: { dependantField: 'configuration.syncariEntityDefId', dependantType: 'AttributeList' },
    datatype: 'picklist',
    defaultValue: null,
    helpSummary: null,
    name: 'updateField',
    fieldSet: 'updateFields',
    label: 'Update Field',
    id: 'updateField',
    values: [
      { datatype: 'string', label: 'About Us', type: 'variable', value: '600a0072a7c745cb6fa840e0' },
      { datatype: 'picklist', label: 'Account Source', type: 'variable', value: '600a0072a7c745cb6fa840ff' },
      { datatype: 'double', label: 'Annual Revenue', type: 'variable', value: '600a0072a7c745cb6fa84107' },
      { datatype: 'string', label: 'Billing City', type: 'variable', value: '600a0072a7c745cb6fa840e7' },
    ],
  },
  {
    mapping: { graphKey: 'configuration.newValue', configKey: 'value' },
    datatype: 'object',
    defaultValue: null,
    helpSummary: null,
    name: 'newValue',
    fieldSet: 'updateFields',
    label: 'New Value',
    id: 'newValue',
    renderType: 'tokens',
  },
];

export const getPicklistValues = (): PicklistValues<OperatorValue[]> => {
  return {
    '600a0072a7c745cb6fa840e0/predicateOperator': [
      { label: 'Equals', unary: false, value: 'eq' },
      { label: 'Equals Ignore Case', unary: false, value: 'ieq' },
      { label: 'Starts With', unary: false, value: 'starts_with' },
      { label: 'Is Empty', unary: true, value: 'empty' },
      { label: 'Is Not Empty', unary: true, value: 'not_empty' },
      { label: 'Not Equals', unary: false, value: 'ne' },
      { label: 'Contains', unary: false, value: 'contains' },
    ],
  };
};

export const fakeNodeId = '5faea9a080fd6f4198487bde';
export const getEntityPipelineState = (nodeId: string = fakeNodeId): Partial<EntityPipelineState> => {
  return {
    selectedGraphNode: {
      shape: 'logo-only-node',
      label: 'Sync to Last Modified',
      icon: '/assets/icons/logos/googlesheets.svg',
      hideLeftStrip: true,
      description: 'Google Sheets Account',
      typeColor: '#3EC675',
      nodeType: 'ATTRIBUTE_SINK',
      id: nodeId,
      x: 900,
      y: 400,
      metadata: {
        id: nodeId,
        name: 'Sync to Last Modified',
        apiName: 'syncariLastModified',
        label: 'Sync to Last Modified',
        subLabel: 'Google Sheets Account',
        inputPorts: [
          {
            portType: 'INPUT',
            datatype: 'datetime',
            maxConnections: 1,
          },
        ],
        outputPorts: [],
        configuration: {
          alwaysUseDefaultOnEmpty: false,
          attributeDefinition: '5faea95e80fd6f4198487b9e',
          defaultValue: '{{record.values.lastModified}}-{{record.values.syncarirecordid}}-{{record.values.name}}',
          configId: '5faea95e80fd6f4198487b9a_sink',
        },
        nodeType: 'ATTRIBUTE_SINK',
        location: {
          x: '900',
          y: '400',
        },
        displayName: 'Sync to Last Modified',
        description: 'Google Sheets Account',
        nodeId,
        nodeName: 'Sync to Last Modified',
      },
      tooltipMessage: 'Google Sheets Account',
    },
  };
};

export const testTokens = {
  [fakeNodeId]: {
    Syncari: [
      {
        ...makeFakeToken('{{syncari.test.token}}', 'Test Token'),
        datatype: 'double' as FieldDataType,
        group: 'Syncari',
      },
    ],
    Synapse: [
      {
        ...makeFakeToken('{{record.values.name}}', 'Name'),
        group: 'Synapse',
      },
    ],
  },
};
