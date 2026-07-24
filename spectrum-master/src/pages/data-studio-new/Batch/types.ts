import type { PicklistValue } from 'components/inputs/types';
import type { Option } from 'components/SelectInput';
import type { EntityFilter } from 'store/data-studio/types';
import type { Entity, EntityField } from 'store/entity/types';

export enum BatchOperationMode {
  NONE = 'none',
  PURGE = 'purge',
  DELETE = 'delete',
  UPDATE = 'update',
}

export enum DeleteType {
  LOCAL = 'local_delete',
  GLOBAL = 'global_delete',
}

export type FieldRowData = {
  name: string;
  value: unknown;
};

export type FieldOption = Option<EntityField>;

export type CommonOperationModalProps = {
  commonI18nArgs?: Record<string, number | string>;
  entity: Entity;
  fieldValues: PicklistValue[];
  filter?: Partial<EntityFilter>;
};
