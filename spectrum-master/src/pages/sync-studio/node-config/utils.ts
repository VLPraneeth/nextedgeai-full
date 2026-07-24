import { InputDataType, InputRenderType } from 'components/inputs/types';
import AppConstants from 'utils/AppConstants';
import { ArrayToUnion } from 'utils/TypeUtils';

export const SUPPORTS_TOKENS = [
  AppConstants.INPUT_TYPE.BOOLEAN,
  AppConstants.INPUT_TYPE.DATE,
  AppConstants.INPUT_TYPE.DATETIME,
  AppConstants.INPUT_TYPE.DOUBLE,
  AppConstants.INPUT_TYPE.ID,
  AppConstants.INPUT_TYPE.INTEGER,
  AppConstants.INPUT_TYPE.PASSWORD,
  AppConstants.INPUT_TYPE.STRING,
  AppConstants.INPUT_TYPE.TEXTAREA,
  AppConstants.INPUT_TYPE.URL,
  AppConstants.INPUT_TYPE.TIMESTAMP,
  AppConstants.INPUT_TYPE.OBJECT,

  // Added for RHS token support when LHS is a temp variable
  // of the following types.
  // TODO: See if this needs to be more strict.
  AppConstants.INPUT_TYPE.TEXT,
  AppConstants.INPUT_TYPE.EMAIL,
  AppConstants.INPUT_TYPE.PHONE,

  // TODO: these have some unique needs for updating because of dependant values
  // AppConstants.INPUT_TYPE.PICKLIST,
  // AppConstants.INPUT_TYPE.AUTOCOMPLETE,
  AppConstants.INPUT_TYPE.REFERENCE,

  // TODO: Need to figure out styling for multiselect inputs
  // AppConstants.INPUT_TYPE.EMAILLIST,
  // AppConstants.INPUT_TYPE.MULTISELECT,

  // TODO: need to compose richtext/token inputs
  // AppConstants.INPUT_TYPE.EMAILBODY,

  // TODO: Probably not possible to tokenize as a whole
  // AppConstants.INPUT_TYPE.PREDICATE,
  // AppConstants.INPUT_TYPE.COMPOSITE,

  AppConstants.INPUT_TYPE.EXTERNAL_ID,
] as const;

type TokenSupportedDatatype = ArrayToUnion<typeof SUPPORTS_TOKENS>;

export const isTokenSupportedDatatype = (variableToCheck: any): variableToCheck is TokenSupportedDatatype => {
  return typeof variableToCheck !== 'undefined' && SUPPORTS_TOKENS.includes(variableToCheck);
};

export const canShowToken = (metadata?: { renderType?: InputRenderType; datatype?: InputDataType }) => {
  if (metadata?.renderType === AppConstants.INPUT_RENDER_TYPE.DATASET_VARIABLE_PICKER) {
    return false;
  }
  return isTokenSupportedDatatype(metadata?.datatype);
};

export const getMergedValues = (updateFieldValues: any[], dependentValues: any[] = []) => {
  const values: any[] = [];

  // Add dependent values if they exist
  if (Array.isArray(dependentValues)) {
    values.push(...dependentValues);
  }

  // Add updateField values if available
  if (updateFieldValues?.length > 0) {
    values.push(...updateFieldValues);
  }

  return values;
};

export const createUniqueValues = (values: any[]) => {
  const createValueObject = (item: any) => ({
    datatype: item.datatype || 'string',
    label: item.label || item.value,
    picklistGroup: item.picklistGroup || '',
    type: item.type || 'variable',
    value: item.value,
  });

  return Array.from(new Map(values.map((item) => [item.value, createValueObject(item)])).values());
};
