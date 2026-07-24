import { FieldDataType } from 'components/types';

export interface PipelinePickerEntity {
  id: string;
  apiName: string;
  displayName: string;
  loading?: boolean;
  unavailableMessage?: string;
  fields: PipelinePickerEntityField[];
}

export interface PipelinePickerEntityField {
  id: string;
  apiName: string;
  displayName: string;
  dataType: FieldDataType;
  fieldAlias?: string;
  datasourceAlias?: string;
}

export const ppEntityGuard = (entity?: PipelinePickerEntity): entity is PipelinePickerEntity => {
  return (entity as PipelinePickerEntity)?.apiName !== undefined;
};

export const ppFieldGuard = (field?: PipelinePickerEntityField): field is PipelinePickerEntityField => {
  return (field as PipelinePickerEntityField)?.apiName !== undefined;
};
