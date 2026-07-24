import { pick } from 'lodash';

export type SchemaColDef = {
  headerName: string;
  field: string;
  cellRenderer: string | undefined;
};

export const generateSchemaCSVData = (data: Record<string, any>[], cols: SchemaColDef[]) => {
  const filteredFields = cols.map((col) => col.field);
  const fieldMap: Record<string, any> = {};

  // Generate the field map
  cols.forEach((col) => {
    fieldMap[col.field] = col.headerName;
  });

  // Transform object keys to display names
  return data
    .map((datum) => pick(datum, filteredFields))
    .map((datum) => {
      const transformedDatum: Record<string, any> = {};

      Object.keys(datum).forEach((key) => {
        transformedDatum[fieldMap[key]] = datum[key];
      });

      return transformedDatum;
    });
};
