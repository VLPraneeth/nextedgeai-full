import { CellClassParams, ICellEditor, ICellEditorParams } from 'ag-grid-community';
import { Input, Menu } from 'antd';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';

import KebabMenu from 'components/KebabMenu';
import { tc } from 'utils/i18nUtil';

import { useCategoriesContext } from './CategoriesTable.context';

import './CategoriesTable.renderer.scss';

export interface NameEditorRef extends Omit<ICellEditor, 'getValue'> {
  getValue: () => string;
}
export interface NameEditorParams extends Omit<ICellEditorParams, 'value'> {
  value: string;
}

export const NameEditor = forwardRef<NameEditorRef, ICellEditorParams>((props, ref) => {
  const { updateCategory, getCategory } = useCategoriesContext();
  const inputRef = useRef<Input>(null);
  const [value, setValue] = useState(props.value);

  useImperativeHandle(ref, () => ({
    getValue: () => value,
    isPopup: () => true,
    afterGuiAttached: () => {
      inputRef.current?.focus();
    },
  }));

  useEffect(() => {
    setValue(props.value);
  }, [props.value]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setValue(newValue);
    updateCategory({
      id: props.data.id,
      name: newValue,
    });
  };

  return (
    <Input
      ref={inputRef}
      value={value}
      onChange={handleChange}
      onPressEnter={() => props.stopEditing()}
      onBlur={() => props.stopEditing()}
    />
  );
});

export const CategoryActions = ({ data, rowIndex, colDef, api }: CellClassParams) => {
  const { deleteCategory } = useCategoriesContext();
  const editable = colDef?.cellRendererParams?.editable;

  const menuItems = [
    <Menu.Item key="edit" disabled={!editable || !data?.name || data.name.toLowerCase() === 'other'}>
      {tc('edit')}
    </Menu.Item>,
    <Menu.Item key="delete" disabled={!editable || !data?.name || data.name.toLowerCase() === 'other'}>
      {tc('delete')}
    </Menu.Item>,
  ];
  const clickHandler = useCallback(
    (action: { key: string }) => {
      switch (action.key) {
        case 'delete':
          deleteCategory(data.id);
          break;
        case 'edit':
          colDef?.cellRendererParams?.editHandler(api, data.id, rowIndex);
          break;
      }
    },
    [api, colDef?.cellRendererParams, data.id, deleteCategory, rowIndex]
  );
  if (data.type === 'system' || data.name === 'other') {
    return null;
  }
  return <KebabMenu onClick={clickHandler} menuItems={menuItems} />;
};
