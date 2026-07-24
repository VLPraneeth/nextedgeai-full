import { FieldDataType } from 'components/types';
import { DatasetFields } from 'store/insights-studio/types';

export type CalculatedFieldParam = Omit<DatasetFields, 'datasetId' | 'datasetType' | 'fieldId'> & {
  datasetId?: DatasetFields['datasetId'];
  datasetType?: DatasetFields['datasetType'] | 'LITERAL';
  fieldId?: DatasetFields['fieldId'];
};

export interface CalculatedField {
  aggFunctions: string;
  datasetFields: CalculatedFieldParam[];
  aliasName: string;
  apiName: string;
  dataType: FieldDataType;
}
