//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { merge } from 'lodash';

import { FilterValue } from 'components/inputs/types';
import { PicklistsState } from 'store/picklists';
import { NodeConfiguration } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';

export const getPredicateValues = (value: FilterValue | Partial<FilterValue> = {}): FilterValue => {
  return merge(
    {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'about us value' },
        },
        {
          predicateId: '5ee29cfc1513bc2ad9b21e36',
          left: {
            datatype: 'textarea',
            picklistGroup: 'Account (Syncari)',
            label: 'Account Description',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2e2',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'account description value' },
        },
      ],
      groupPredicateId: '5ee2ec37e794d775c028eecc',
      operator: 'AND',
    },
    value
  );
};

export const getOperatorPicklist = (value: Partial<PicklistsState> = {}): PicklistsState => {
  return merge(
    {
      fetchingPicklistValues: false,
      fetchingPicklistValuesStatus: AppConstants.FETCH_STATUS.IDLE,
      picklistValues: {
        '5ee15c4d7f939d21244da2cd/testOperator': [
          {
            label: 'Equals',
            unary: false,
            value: 'eq',
          },
        ],
        '5ee15c4d7f939d21244da2e2/testOperator': [
          {
            label: 'Equals',
            unary: false,
            value: 'eq',
          },
        ],
        'FilterMultiValueTextPicklist-5ee15c4d7f939d21244da2cd-findNotMatchingValue': [
          {
            value: 'most_recently_created_with_value',
            label: 'Value From Latest Created Record',
          },
        ],
      },
      Operator5ee15c4d7f939d21244da2cd: [
        {
          label: 'Equals',
          unary: false,
          value: 'eq',
          datatype: 'multivaluetext',
        },
        {
          label: 'Equals Ignore Case',
          unary: false,
          value: 'ieq',
        },
        {
          label: 'Starts With',
          unary: false,
          value: 'starts_with',
        },
        {
          label: 'Is Empty',
          unary: true,
          value: 'empty',
        },
        {
          label: 'Is Not Empty',
          unary: true,
          value: 'not_empty',
        },
        {
          label: 'Not Equals',
          unary: false,
          value: 'ne',
        },
      ],
      Operator5ee15c4d7f939d21244da2e2: [
        {
          label: 'Equals',
          unary: false,
          value: 'eq',
        },
        {
          label: 'Equals Ignore Case',
          unary: false,
          value: 'ieq',
        },
        {
          label: 'Starts With',
          unary: false,
          value: 'starts_with',
        },
        {
          label: 'Is Empty',
          unary: true,
          value: 'empty',
        },
        {
          label: 'Is Not Empty',
          unary: true,
          value: 'not_empty',
        },
        {
          label: 'Not Equals',
          unary: false,
          value: 'ne',
        },
      ],
    },
    value
  );
};

export const getInputConfigs = (): NodeConfiguration[] => {
  return [
    {
      mapping: [{ graphKey: 'configuration.attributeDefinition', configKey: 'value' }],
      datatype: 'picklist',
      defaultValue: '',
      values: [
        {
          datatype: 'string',
          picklistGroup: 'Account (Syncari)',
          label: 'About Us',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2cd',
        },
        {
          datatype: 'textarea',
          picklistGroup: 'Account (Syncari)',
          label: 'Account Description',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2e2',
        },
        {
          datatype: 'picklist',
          picklistGroup: 'Account (Syncari)',
          label: 'Account Source',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2ec',
        },
        {
          datatype: 'textarea',
          picklistGroup: 'Account (Syncari)',
          label: 'Billing Street',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2d3',
        },
        {
          datatype: 'datetime',
          picklistGroup: 'Account (Syncari)',
          label: 'Close Date',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2f2',
        },
        {
          datatype: 'reference',
          picklistGroup: 'Account (Syncari)',
          label: 'Created By ID',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2e5',
        },
        {
          datatype: 'boolean',
          picklistGroup: 'Account (Syncari)',
          label: 'Deleted',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2ce',
        },
        {
          datatype: 'integer',
          picklistGroup: 'Account (Syncari)',
          label: 'Employees',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2e1',
        },
        {
          datatype: 'url',
          picklistGroup: 'Account (Syncari)',
          label: 'Photo URL',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2df',
        },
        {
          datatype: 'double',
          picklistGroup: 'Account (Syncari)',
          label: 'Score',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2f1',
        },
      ],
      name: 'field',
      id: 'field',
      fieldSet: 'conditionFields',
      label: 'Field',
    },
    {
      mapping: [{ graphKey: 'configuration.operator', configKey: 'value' }],
      dependsOn: { dependantField: 'configuration.attributeDefinition', dependantType: 'Operator' },
      datatype: 'picklist',
      defaultValue: '',
      helpSummary: null,
      name: 'operator',
      id: 'operator',
      fieldSet: 'conditionFields',
      label: 'Operator',
    },
    {
      mapping: [{ graphKey: 'configuration.value' }],
      datatype: 'string',
      defaultValue: '',
      helpSummary: null,
      name: 'value',
      id: 'value',
      fieldSet: 'conditionFields',
      label: 'Value',
      type: 'literal',
    },
  ];
};
