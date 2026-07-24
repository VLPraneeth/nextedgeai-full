//
// Copyright (c) 2019-Present Syncari All rights reserved.
//
import { ColDef, ColGroupDef, ICellEditor, ICellEditorParams } from 'ag-grid-community';
import { Icon } from 'antd';
import Select, { OptionProps } from 'antd/lib/select';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { cloneDeep } from 'lodash/fp';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useState } from 'react';
import * as React from 'react';

import AgTable from 'components/AgTable';
import Button from 'components/Button';
import FieldTypeBadge from 'components/FieldTypeBadge';
import InputContainer from 'components/inputs/InputContainer';
import { HStack, Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { useGetHttpCustomSypapseDatatypesQuery } from 'store/custom-synapse/http/api';
import AppConstants from 'utils/AppConstants';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { humanize } from 'utils/StringUtil';

import { HttpSynapseMetadata } from '../types';

import './AdditionalMetadata.scss';

const { Option } = Select;

export interface AdditionalMetaDataProps {
  className?: string;
  defaultValue?: HttpSynapseMetadata[];
  onChange?: (value: HttpSynapseMetadata[]) => void;
  readonly?: boolean;
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

export const AdditionalMetadata = ({
  className,
  onChange,
  defaultValue,
  readonly = false,
}: AdditionalMetaDataProps) => {
  const [additionalMetadata, setAdditionalMetadata] = useState<HttpSynapseMetadata[]>(
    defaultValue?.length
      ? defaultValue.map((value) => ({ ...value, id: ObjectID.generate() }))
      : [{ id: ObjectID.generate() }]
  );

  const onRowEditingStopped = useCallback((evt: any) => {
    const { data } = evt;
    setAdditionalMetadata((prev) =>
      prev.map((additionalMetadata) => (additionalMetadata.id === data.id ? data : additionalMetadata))
    );
  }, []);

  const addNewRow = useCallback(() => {
    setAdditionalMetadata([...additionalMetadata, { id: ObjectID.generate() }]);
  }, [additionalMetadata]);

  useEffect(() => {
    onChange?.(additionalMetadata?.filter((metadata) => Boolean(metadata.name || metadata.dataType || metadata.value)));
  }, [onChange, additionalMetadata]);

  const onDeleteItem = useCallback((id: string) => {
    setAdditionalMetadata((prev) => {
      const items = prev.filter((item) => item.id !== id);
      if (items.length <= 0) {
        return [{ id: ObjectID.generate() }];
      }
      return items;
    });
  }, []);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    const headers = [
      {
        headerName: tc('name'),
        field: 'name',
        editable: !readonly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: tc('value'),
        field: 'value',
        editable: !readonly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: tc('data_type'),
        field: 'dataType',
        editable: !readonly,
        cellEditorFramework: DataTypeEditor,
        cellRendererFramework: DataTypeRenderer,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      !readonly
        ? {
            headerName: '',
            field: 'deleteColumn',
            minWidth: 48,
            maxWidth: 48,
            cellRendererFramework: ({ data }: { data: any }) => (
              <DeleteAction data={data} onDeleteItem={onDeleteItem} />
            ),

            cellClass: 'synri-cell-action',
            suppressMovable: true,
          }
        : {},
    ];

    return headers;
  }, [onDeleteItem, readonly]);

  // AgTable mutate the object and states are readonly hence cloning it here before passing to AgTable
  const rowAdditionalMetadata = useMemo(() => cloneDeep(additionalMetadata), [additionalMetadata]);

  return (
    <Stack className={cx('synri-additional-metadata', className)} spacing="sm">
      <AgTable
        defaultColDef={{ flex: 1 }}
        suppressCellSelection
        columnDefs={columns}
        rowData={rowAdditionalMetadata}
        domLayout="normal"
        editType="fullRow"
        stopEditingWhenGridLosesFocus
        singleClickEdit
        onRowEditingStopped={onRowEditingStopped}
      />
      {!readonly && (
        <Button type="link" onClick={addNewRow} className="add-button">
          <Icon type="plus" />
          {tn('add_metadata')}
        </Button>
      )}
    </Stack>
  );
};

const DeleteAction = ({ data, onDeleteItem }: { data: HttpSynapseMetadata; onDeleteItem: (id: string) => void }) => {
  return (
    <div className="synri-mapping-action-container">
      <Icon
        type="delete"
        theme="filled"
        onClick={() => {
          if (data.id) {
            onDeleteItem(data.id);
          }
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

  const { data: dataTypes } = useGetHttpCustomSypapseDatatypesQuery();

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
      options={dataTypes?.map((type) => (
        <Option value={type.value} key={type.value} title={type.label}>
          <HStack spacing="xxxs">
            <FieldTypeBadge dataType={type.value as FieldDataType} disableTooltip />
            <span>{type.label}</span>
          </HStack>
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
