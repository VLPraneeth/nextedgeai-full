import { ICellEditorParams } from 'ag-grid-community';
import { forwardRef, useEffect, useImperativeHandle } from 'react';

import { useFieldOptions } from 'components/inputs/FieldOptions';
import { DisplayMode } from 'components/inputs/types';
import { useSkullTableContext } from 'components/skull-table/SkullTableContext';
import useDimensions from 'hooks/useDimensions';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';

import Select from '../inputs/Select';

export interface MultiSelectFieldProps extends ICellEditorParams {
  displayMode?: DisplayMode;
}

const fullWidth = { width: '100%' };

const MultiSelectField = forwardRef(
  ({ value: rowData, colDef, data, displayMode, node }: MultiSelectFieldProps, ref) => {
    const { inputValue, updateValue, getFieldValue } = useSkullTableContext();

    const fieldName = colDef.field as string;
    const value = getFieldValue({ id: data.id, fieldName }) || EMPTY_ARRAY;

    const selectedFieldSelectOptions = useFieldOptions(rowData.values || EMPTY_ARRAY);

    useImperativeHandle(ref, () => {
      return {
        getValue: () => {
          return inputValue;
        },
      };
    });

    const [measurementRef, , , remeasure] = useDimensions();

    const valuesCount = Array.isArray(value) ? value.length : 0;

    useEffect(() => {
      const newDimensions = remeasure();
      // Only expand if height of Select is greater than default row height
      if (newDimensions.height > 28) {
        node.setRowHeight(newDimensions.height);
      }
    }, [node, remeasure, valuesCount, displayMode]);

    return (
      <div style={fullWidth} ref={measurementRef}>
        <Select
          className="synri-email-input"
          // @ts-expect-error value has no typing
          defaultValue={value}
          dropdownMatchSelectWidth
          mode="tags"
          disabled={displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY}
          onChange={(newValue: string[]) => updateValue({ id: data.id, fieldName, value: newValue })}
          {...selectedFieldSelectOptions}
        />
      </div>
    );
  }
);

export default MultiSelectField;
