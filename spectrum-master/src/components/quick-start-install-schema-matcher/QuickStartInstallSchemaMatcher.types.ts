import { FieldOptionProps } from 'components/inputs/FieldOptions/useFieldOptions';

export interface QuickStartSchemaMatcherEntityOptions {
  label: string;
  value: string;
  fieldOptions: FieldOptionProps[];
}

export interface QuickStartSchemaMatcherItem {
  entityName: string;
  entityId: string;
  entityOptions: QuickStartSchemaMatcherEntityOptions[];
  fields: FieldOptionProps[];
}

export type FieldMatchMap = Record<string, string | null>;
export type SchemaMatchMap = Record<string, { matchValue: string | null; fields: FieldMatchMap }>;
