import { VizConfigColumnOption } from '../components/data-card-wizard/configuration-step/DataCardConfigStep';

// Note: special case number. It is not a syncari schema datatype but a datastore datatype only.
const NUMERIC_DATATYPES = ['double', 'integer', 'number'];

export function getNumericColumns(columnOptions: VizConfigColumnOption[]) {
  return columnOptions.filter((option) => {
    return NUMERIC_DATATYPES.includes(option.dataType?.toLowerCase() || '');
  });
}
