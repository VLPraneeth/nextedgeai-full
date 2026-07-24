import { Status } from 'components/renderers/types';

import { FieldModel } from './types';

export const getEmptySchemaField = (field: Partial<FieldModel & { hasDraft?: boolean }> = {}): FieldModel => {
  return {
    apiName: 'AboutUs',
    dataStoreName: 'AboutUs',
    picklistValues: [],
    isRequired: false,
    isReadonly: false,
    updatedBy: '5ef7ca568c3f9728a66c65a8',
    displayName: 'About Us',
    isCalculated: false,
    isWatermarkField: false,
    isMultiValueField: false,
    parentAttributeId: '',
    isSyncariDefined: false,
    totalRecords: 100,
    length: 255,
    totalFields: 100,
    references: 0,
    isUnique: false,
    usedIn: [],
    description: 'Description',
    tags: ['tagone'],
    isSystem: false,
    lastUpdated: '2020-07-01T06:38:58.100+0000',
    id: '5efa70ebda58b56af83de2d9',
    isIdField: true,
    sources: [],
    destinations: [],
    status: Status.APPROVED,
    dataType: 'string',
    referenceTo: '',
    referenceTargetField: '',
    compositeKey: '',
    schemaUpdatable: false,
    schemaDeletable: false,
    ...field,
  };
};
