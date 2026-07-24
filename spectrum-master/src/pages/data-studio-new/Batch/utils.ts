import { EntityField } from 'store/entity/types';

export const UNSUPPORTED_DATATYPES = ['timestamp', 'object', 'id', 'list'];

export const getFirstAvailableField = (allFields: EntityField[], fieldKeysToExclude: string[] = []) => {
  return allFields.find((f) => !UNSUPPORTED_DATATYPES.includes(f.dataType) && !fieldKeysToExclude.includes(f.apiName));
};
