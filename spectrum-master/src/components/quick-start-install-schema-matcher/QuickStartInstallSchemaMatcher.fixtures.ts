import { SchemaMatchMap, QuickStartSchemaMatcherItem } from './QuickStartInstallSchemaMatcher.types';

export const installSchemaMatcherItems: QuickStartSchemaMatcherItem[] = [
  {
    entityName: 'Account',
    entityId: '123',
    fields: [
      {
        id: '1',
        displayName: 'FieldName 1',
        apiName: 'fieldname1',
        dataType: 'id',
      },
      {
        id: '2',
        displayName: 'FieldName 3',
        apiName: 'fieldname3',
        dataType: 'list',
      },
    ],
    entityOptions: [
      {
        label: 'Account',
        value: '456',
        fieldOptions: [
          {
            id: 'a1',
            displayName: 'Account Field 1',
            apiName: 'accountField_1',
            dataType: 'id',
          },
          {
            id: 'a2',
            displayName: 'Account Field 2',
            apiName: 'accountField_2',
            dataType: 'list',
          },
        ],
      },
      {
        label: 'Opportunity',
        value: '987',
        fieldOptions: [
          {
            id: 'opp1',
            displayName: 'Opp Field 1',
            apiName: 'oppField_1',
            dataType: 'id',
          },
          {
            id: 'opp2',
            displayName: 'Opp Field 2',
            apiName: 'oppField_2',
            dataType: 'integer',
          },
        ],
      },
    ],
  },
  {
    entityName: 'Opportunity',
    entityId: '234',
    fields: [
      {
        id: '3',
        displayName: 'FieldName 2',
        apiName: 'fieldname2',
        dataType: 'double',
      },
      {
        id: '4',
        displayName: 'FieldName 4',
        apiName: 'fieldname4',
        dataType: 'double',
      },
    ],
    entityOptions: [
      {
        label: 'Opportunity match',
        value: '567',
        fieldOptions: [
          {
            id: 'd',
            displayName: 'FieldName 4 match',
            apiName: 'fieldname4',
            dataType: 'double',
          },
          {
            id: 'dd',
            displayName: 'FieldName 4 match double2',
            apiName: 'fieldnamedouble2',
            dataType: 'double',
          },
          {
            id: 'f',
            displayName: 'FieldName 4 match string',
            apiName: 'fieldnamestring',
            dataType: 'string',
          },
        ],
      },
    ],
  },
];

export const installSchemaMatcherDefaultValue: SchemaMatchMap = {
  '123': {
    matchValue: '456',
    fields: {
      '1': 'a1',
      '2': 'a2',
    },
  },
  '234': {
    matchValue: '567',
    fields: {
      '3': 'd',
      '4': 'd',
    },
  },
};
