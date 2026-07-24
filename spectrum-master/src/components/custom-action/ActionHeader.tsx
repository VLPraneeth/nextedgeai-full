//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { CellClassParams, RowEditingStoppedEvent } from 'ag-grid-community';
import { Icon } from 'antd';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { cloneDeep } from 'lodash/fp';
import * as React from 'react';
import { createContext, useCallback, useEffect, useMemo, useState } from 'react';

import AgTable from 'components/AgTable';
import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';

import './ActionHeader.less';
import { useListContext } from './ActionSetup.hook';
const DefaultColDef = { flex: 1 };

export interface ActionHeaderProps {
  className?: string;
  defaultValue?: Header[];
  onChange?: (value: Header[]) => void;
  readOnly?: boolean;
}

export interface Header {
  id?: string;
  key?: string;
  value?: string;
}

export interface ListContext {
  onDeleteItem: (id: string) => void;
}

export const ListCtx = createContext<ListContext>({
  onDeleteItem: (id) => {},
});

export interface ListContextContextProviderProps {
  children: React.ReactNode;
  value: ListContext;
}

export const ListContextProvider = ({ children, value }: ListContextContextProviderProps) => {
  return <ListCtx.Provider value={value}>{children}</ListCtx.Provider>;
};

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

export const ActionHeader = ({ className, onChange, defaultValue, readOnly }: ActionHeaderProps) => {
  const { tc } = useI18nContext();
  const [headers, setHeaders] = useState<Header[]>(
    defaultValue?.length
      ? defaultValue.map((header) => ({ ...header, id: ObjectID.generate() }))
      : [{ id: ObjectID.generate() }]
  );

  const onRowEditingStopped = useCallback((evt: RowEditingStoppedEvent) => {
    const { data } = evt;
    setHeaders((prev) => prev.map((header) => (header.id === data.id ? data : header)));
  }, []);

  useEffect(() => {
    onChange?.(headers?.filter((header) => Boolean(header.key || header.value)));
  }, [headers, onChange]);

  const addNewRow = useCallback(() => {
    setHeaders([...headers, { id: ObjectID.generate() }]);
  }, [headers]);

  const columns = useMemo(() => {
    return [
      {
        headerName: 'Key',
        field: 'key',
        editable: !readOnly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
      {
        headerName: 'Value',
        field: 'value',
        editable: !readOnly,
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
    setHeaders((prev) => {
      const items = prev.filter((item) => item.id !== id);
      if (items.length <= 0) {
        return [{ id: ObjectID.generate() }];
      }
      return items;
    });
  }, []);

  // AgTable mutate the object and states are readonly hence cloning it here before passing to AgTable
  const rowHeaders = useMemo(() => cloneDeep(headers), [headers]);

  return (
    <ListContextProvider value={{ onDeleteItem }}>
      <Stack className={cx('synri-action-header', className)} spacing="md">
        <AgTable
          defaultColDef={DefaultColDef}
          suppressCellSelection
          columnDefs={columns}
          rowData={rowHeaders}
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

export default withI18n(ActionHeader, 'ActionSetup');
