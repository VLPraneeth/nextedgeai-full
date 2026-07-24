import { FieldDataType } from 'components/types';

export interface FieldMetadata {
  dataType: FieldDataType;
  label: string;
  value: string;
  isSystem?: boolean;
}

export enum Status {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  PENDING = 'PENDING',
  DELETED = 'DELETED',
  APPROVED = 'APPROVED',
}
