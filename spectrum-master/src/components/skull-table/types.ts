export interface SkullTableInitialDataField {
  value: unknown;
  label?: string;
  values?: { label: string; value: unknown }[];
}

// Data returned from the server to populate the table
export interface SkullTableInitialRowData {
  id: string;
  fields: Record<string, SkullTableInitialDataField>;
}
