import { ColDef } from 'ag-grid-community';
import { find, map } from 'lodash';
import { useEffect, useMemo } from 'react';

import { DisplayMode } from 'components/inputs/types';
import { MetadataDependsOn } from 'components/skull';
import { SkullTableContextProvider, tranformSourceDataToFormData } from 'components/skull-table/SkullTableContext';
import { useDependsOnData } from 'store/quick-start-legacy/hooks';
import AppConstants from 'utils/AppConstants';

import AgTable, { AgTableProps, ResizeColumnsCondition } from '../AgTable/AgTable';
import { Stack } from '../layout';
import MultiSelectField from './MultiSelectField';
import RowCheckbox from './RowCheckbox';
import TextLabel from './TextLabel';
import { SkullTableInitialRowData } from './types';

const containerHeight = { height: 250 };

export interface SkullTableProps extends Omit<AgTableProps, 'columnDefs'> {
  columnDefs: (ColDef & { rowSelectionField?: boolean; field: string })[];
  value: AgTableProps['rowData'];
  defaultValue: Record<string, unknown>[];
  dependsOn?: MetadataDependsOn;
  dependantValues?: Record<string, unknown>;
  onChange: (value: unknown) => void;
  displayMode?: DisplayMode;
}

const components = {
  checkbox: RowCheckbox,
  multiSelectField: MultiSelectField,
  textLabel: TextLabel,
};

const SkullTable = ({ onChange, dependsOn, defaultValue: value, dependantValues, ...props }: SkullTableProps) => {
  const previewMode = props.displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
  const initialData = useDependsOnData<SkullTableInitialRowData[]>(dependsOn, dependantValues);

  const columnDefs = useMemo(
    () =>
      props.columnDefs
        ?.filter(
          (column) => props.displayMode !== AppConstants.INPUT_DISPLAY_MODE.READONLY || !column.rowSelectionField
        )
        .map((column) => ({ ...column, cellRendererParams: { displayMode: props.displayMode } })),
    [props.columnDefs, props.displayMode]
  );

  // Filter unselected rows when previewing
  const rowData = useMemo(() => {
    const data = map(initialData, (row) => ({ id: row.id, ...row.fields }));
    if (previewMode) {
      const selectionField = props.columnDefs.find(({ rowSelectionField }) => rowSelectionField);
      if (selectionField) {
        return data.filter((row) => {
          const rowData = find(value, { id: row.id });
          return rowData?.[selectionField.field];
        });
      }
    }
    return data;
  }, [initialData, previewMode, props.columnDefs, value]);

  const initialFormData = useMemo(() => tranformSourceDataToFormData(initialData), [initialData]);

  useEffect(() => {
    onChange(initialFormData);
  }, [initialFormData, onChange]);

  return (
    <SkullTableContextProvider initialData={value || initialFormData} onChange={onChange}>
      <Stack fill>
        <AgTable
          {...props}
          columnDefs={columnDefs}
          frameworkComponents={components}
          sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
          onGridColumnsChanged={({ api }) => api.refreshCells({ force: true })}
          rowData={rowData}
          containerStyle={containerHeight}
          suppressCellSelection
        />
      </Stack>
    </SkullTableContextProvider>
  );
};

export default SkullTable;
