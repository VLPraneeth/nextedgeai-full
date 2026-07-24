//
// Copyright (c) 2019-Present Syncari All rights reserved.
//
import { CellClassParams, ICellEditor, ICellEditorParams } from 'ag-grid-community';
import { Icon, Tooltip } from 'antd';
import Select, { OptionProps } from 'antd/lib/select';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { cloneDeep } from 'lodash/fp';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useState } from 'react';
import * as React from 'react';

import AgTable from 'components/AgTable';
import Button from 'components/Button';
import Checkbox from 'components/Checkbox';
import FieldTypeBadge, { DataTypeIcons } from 'components/FieldTypeBadge';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputContainer from 'components/inputs/InputContainer';
import { Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import AppConstants from 'utils/AppConstants';
import { humanize } from 'utils/StringUtil';

import './ActionVariable.less';
import { ListContextProvider } from './ActionHeader';
import { useListContext } from './ActionSetup.hook';

const { Option } = Select;

const DefaultColDef = { flex: 1 };

const UNSUPPORTED_DATATYPE = ['id', 'list', 'filelink', 'picklist', 'reference', 'polymorphicreference', 'complex'];

export interface ActionVariableProps {
  className?: string;
  defaultValue?: Variable[];
  onChange?: (value: Variable[]) => void;
  readOnly?: boolean;
}

export interface Variable {
  id?: string;
  name?: string;
  displayName?: string;
  dataType?: string;
  multivalued?: string;
  helpText?: string;
  required?: string;
}

const DeleteAction = ({ data }: CellClassParams) => {
  const { onDeleteItem } = useListContext();
  return (
    <div className="synri-mapping-action-container">
      <Icon
        type="delete"
        theme="filled"
        data-testid={`delete-action`}
        onClick={() => {
          onDeleteItem(data.id);
        }}
      />
    </div>
  );
};

export interface DataTypeEditorRef extends Omit<ICellEditor, 'getValue'> {
  getValue: () => string;
}

export interface DataTypeEditorParams extends Omit<ICellEditorParams, 'value'> {
  value: string;
}

export const DataTypeEditor = forwardRef<DataTypeEditorRef, DataTypeEditorParams>(({ value, data, colDef }, ref) => {
  const [selectedValue, setSelectedValue] = useState(value);

  useImperativeHandle(ref, () => ({
    getValue: () => {
      return selectedValue || '';
    },
  }));

  const filterOption = useCallback((input: string, option: React.ReactElement<OptionProps>) => {
    return (option.props.title?.toLowerCase() || '').indexOf(input.toLowerCase()) >= 0;
  }, []);

  return (
    <InputContainer
      defaultValue={value}
      filterOption={filterOption}
      datatype={AppConstants.INPUT_TYPE.PICKLIST}
      onChange={(value: string) => setSelectedValue(value)}
      options={Object.keys(DataTypeIcons)
        .filter((dataType) => !UNSUPPORTED_DATATYPE.includes(dataType.toLowerCase()))
        .map((key: string) => (
          <Option value={key} key={key} title={key}>
            <div className="action-variable-datatype-option">
              <FieldTypeBadge dataType={key as FieldDataType} description={humanize(key)} disableTooltip />
              <span>{humanize(key)}</span>
            </div>
          </Option>
        ))}
    />
  );
});

export interface DataTypeRendererRef extends Omit<ICellEditor, 'getValue'> {
  getValue: () => string;
}
export interface DataTypeRendererParams extends Omit<ICellEditorParams, 'value'> {
  value: string;
}

export const DataTypeRenderer = forwardRef<DataTypeRendererRef, DataTypeRendererParams>(
  ({ value, data, colDef }, ref) => {
    useImperativeHandle(ref, () => ({
      getValue: () => value || '',
    }));
    const { dataType } = data;
    return dataType ? (
      <>
        <FieldTypeBadge dataType={dataType as FieldDataType} description={humanize(dataType)} disableTooltip />
        <span>{humanize(dataType)}</span>
      </>
    ) : (
      <span />
    );
  }
);

export const MultivaluedRenderer = forwardRef<DataTypeEditorRef, DataTypeEditorParams>(
  ({ value, data, colDef }, ref) => {
    const { tn } = useI18nContext();

    useImperativeHandle(ref, () => ({
      getValue: () => {
        return value || '';
      },
    }));

    //
    return (
      <>
        <Checkbox checked={value === AppConstants.TRUE}>{tn('multivalued')}</Checkbox>
        <Tooltip title={tn('multivalued_tooltip')}>
          <Icon theme="filled" type="question-circle" />
        </Tooltip>
      </>
    );
  }
);

export const MultivaluedEditor = forwardRef<DataTypeEditorRef, DataTypeEditorParams>(({ value, data, colDef }, ref) => {
  const [selectedValue, setSelectedValue] = useState(value);

  const { tn } = useI18nContext();

  useImperativeHandle(ref, () => ({
    getValue: () => {
      return selectedValue || '';
    },
  }));

  return (
    <>
      <Checkbox
        className="action-variable-multivalued-option"
        checked={selectedValue === AppConstants.TRUE}
        onChange={(event) => {
          setSelectedValue(event.target.checked ? AppConstants.TRUE : AppConstants.FALSE);
        }}>
        {tn('multivalued')}
      </Checkbox>
      <Tooltip title={tn('multivalued_tooltip')}>
        <Icon theme="filled" type="question-circle" />
      </Tooltip>
    </>
  );
});

export const RequiredEditor = forwardRef<DataTypeEditorRef, DataTypeEditorParams>(({ value, data, colDef }, ref) => {
  const [selectedValue, setSelectedValue] = useState(value);

  const { tn } = useI18nContext();

  useImperativeHandle(ref, () => ({
    getValue: () => {
      return selectedValue || '';
    },
  }));

  return (
    <Checkbox
      className="action-variable-required-option"
      checked={selectedValue === AppConstants.TRUE}
      onChange={(event) => {
        setSelectedValue(event.target.checked ? AppConstants.TRUE : AppConstants.FALSE);
      }}>
      {tn('is_required')}
    </Checkbox>
  );
});

export const RequiredRenderer = forwardRef<DataTypeEditorRef, DataTypeEditorParams>(({ value, data, colDef }, ref) => {
  const { tn } = useI18nContext();

  useImperativeHandle(ref, () => ({
    getValue: () => {
      return value || '';
    },
  }));

  return <Checkbox checked={value === AppConstants.TRUE}>{tn('is_required')}</Checkbox>;
});

export const ActionVariable = ({ className, onChange, defaultValue, readOnly }: ActionVariableProps) => {
  const { tc } = useI18nContext();
  const [variables, setVariables] = useState<Variable[]>(
    defaultValue?.length
      ? defaultValue.map((variable) => ({ ...variable, id: ObjectID.generate() }))
      : [{ id: ObjectID.generate(), multivalued: AppConstants.FALSE, required: AppConstants.FALSE }]
  );

  const onRowEditingStopped = useCallback((evt: any) => {
    const { data } = evt;
    setVariables((prev) => prev.map((variable) => (variable.id === data.id ? data : variable)));
  }, []);

  const addNewRow = useCallback(() => {
    setVariables([
      ...variables,
      { id: ObjectID.generate(), multivalued: AppConstants.FALSE, required: AppConstants.FALSE },
    ]);
  }, [variables]);

  useEffect(() => {
    onChange?.(
      variables?.filter((variable) =>
        Boolean(
          variable.name ||
            variable.dataType ||
            (variable.multivalued && variable.multivalued !== AppConstants.FALSE) ||
            variable.displayName ||
            variable.helpText ||
            (variable.required && variable.required !== AppConstants.FALSE)
        )
      )
    );
  }, [onChange, variables]);

  const columns = useMemo(() => {
    return [
      {
        headerName: 'Name',
        field: 'name',
        editable: !readOnly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: 'Display Name',
        field: 'displayName',
        editable: !readOnly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: 'Data Type',
        field: 'dataType',
        editable: !readOnly,
        cellEditorFramework: DataTypeEditor,
        cellRendererFramework: DataTypeRenderer,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: '',
        field: 'multivalued',
        editable: !readOnly,
        cellEditorFramework: MultivaluedEditor,
        cellRendererFramework: MultivaluedRenderer,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: false,
      },
      {
        headerName: 'Help Text',
        field: 'helpText',
        editable: !readOnly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: 'Required',
        field: 'required',
        editable: !readOnly,
        cellEditorFramework: RequiredEditor,
        cellRendererFramework: RequiredRenderer,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      !readOnly
        ? {
            headerName: '',
            field: 'deleteColumn',
            minWidth: 48,
            maxWidth: 48,
            cellRendererFramework: DeleteAction,
            cellClass: 'synri-cell-action',
            suppressMovable: true,
          }
        : {},
    ];
  }, [readOnly]);

  const onDeleteItem = useCallback((id: string) => {
    setVariables((prev) => {
      const items = prev.filter((item) => item.id !== id);
      if (items.length <= 0) {
        return [{ id: ObjectID.generate() }];
      }
      return items;
    });
  }, []);

  // AgTable mutate the object and states are readOnly hence cloning it here before passing to AgTable
  const rowVariables = useMemo(() => cloneDeep(variables), [variables]);
  return (
    <ListContextProvider value={{ onDeleteItem }}>
      <Stack className={cx('synri-action-variable', className)} spacing="md">
        <AgTable
          defaultColDef={DefaultColDef}
          suppressCellSelection
          columnDefs={columns}
          rowData={rowVariables}
          editType="fullRow"
          stopEditingWhenGridLosesFocus
          singleClickEdit
          onRowEditingStopped={onRowEditingStopped}
        />
        {!readOnly && (
          <Button type="primary" onClick={addNewRow}>
            <Icon type="plus" />
            {tc('add')}
          </Button>
        )}
      </Stack>
    </ListContextProvider>
  );
};

export default withI18n(ActionVariable, 'ActionSetup');
