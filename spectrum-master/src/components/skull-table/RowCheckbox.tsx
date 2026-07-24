import { ICellEditorParams } from 'ag-grid-community';
import { Checkbox } from 'antd';
import { forwardRef, useImperativeHandle } from 'react';

import { useSkullTableContext } from './SkullTableContext';

export interface RowCheckboxProps extends Omit<ICellEditorParams, 'value'> {
  value: { label: string; value: string };
}

const RowCheckbox = forwardRef(({ colDef, data }: RowCheckboxProps, ref) => {
  const { inputValue, updateValue, getFieldValue } = useSkullTableContext();

  const fieldName = colDef.field as string;
  const value = getFieldValue({ id: data.id, fieldName });

  useImperativeHandle(ref, () => {
    return {
      getValue: () => {
        return inputValue;
      },
    };
  });

  return (
    <Checkbox
      name={fieldName}
      onChange={(evt) => updateValue({ id: data.id, fieldName, value: evt.target.checked })}
      checked={value as boolean}
    />
  );
});

export default RowCheckbox;
