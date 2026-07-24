import { useCallback, useMemo, useState, ChangeEvent } from 'react';
import { ColDef, ColGroupDef, GridReadyEvent, GridApi, CellClassParams } from 'ag-grid-community';
import { Button, Modal, message } from 'antd';
import { cx } from '@emotion/css';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { HStack } from 'components/layout';
import SearchBox from 'components/SearchBox';

import {
  useListAttributeValuesQuery,
  useDeleteAttributeValuesMutation,
  useDeleteAttributeValueMutation,
} from 'store/access-control/abac/api';

import { tc, tNamespaced } from 'utils/i18nUtil';
import EditValuesDrawer from '../forms/AddValuesDrawer';
import ValueActions from './ValueActions';

const tn = tNamespaced('Settings.AccessControl.ABAC.valuesTable');

export type VisiblityTypes = 'add' | 'edit' | 'hidden';

export default function ValuesTable() {
  const [gridApi, setGridApi] = useState<GridApi | null>();
  const [valuesDrawerState, setValuesDrawerState] = useState<VisiblityTypes>('hidden');
  const [selectedRows, setSelectedRows] = useState<any[]>([]);
  const [searchText, setSearchText] = useState<string>('');

  const valueColumns: (ColDef | ColGroupDef)[] = useMemo(
    () => [
      {
        headerName: '',
        field: 'selection',
        width: 40,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        resizable: false,
      },
      {
        headerName: tn('headers.resource_type'),
        field: 'resourceTypeName',
        resizable: true,
      },
      {
        headerName: tn('headers.resource'),
        field: 'resourceName',
        resizable: true,
      },
      {
        headerName: tn('headers.attribute_name'),
        field: 'attributeName',
        resizable: true,
      },
      {
        headerName: tn('headers.value'),
        field: 'value',
        resizable: true,
      },
      {
        headerName: tc('actions'),
        field: 'actions',
        cellRendererFramework: ({ data }: CellClassParams) => (
          <ValueActions
            data={data}
            onEditActionClick={() => {
              setSelectedRows([data]);
              setValuesDrawerState('edit');
            }}
            onDeleteActionClick={() => {
              handleDeleteValue(data);
            }}
          />
        ),
        headerClass: 'actions',
        pinned: 'right',
        width: 80,
        resizable: false,
      },
    ],
    []
  );

  const { data: valuesData, refetch } = useListAttributeValuesQuery();
  const [deleteValue] = useDeleteAttributeValueMutation();
  const [deleteValues] = useDeleteAttributeValuesMutation();

  const onGridReady = (event: GridReadyEvent) => {
    setGridApi(event.api);
  };

  const handleDeleteValue = async (data: any) => {
    try {
      await deleteValue(data.id).unwrap();
      message.success('Value deleted.');
    } catch (error: any) {
      console.error('Error deleting value:', error);
      message.error(`Error deleting value: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  };

  const filteredValues = useMemo(() => {
    if (!valuesData || !searchText) return valuesData;

    const searchLower = searchText.toLowerCase();
    return valuesData.filter((value) => {
      return (
        value.resourceTypeName?.toLowerCase().includes(searchLower) ||
        value.resourceName?.toLowerCase().includes(searchLower) ||
        value.attributeName?.toLowerCase().includes(searchLower)
      );
    });
  }, [valuesData, searchText]);

  const handleRowSelected = useCallback(
    (event: any) => {
      const selectedNodes = event.api.getSelectedNodes();
      const selectedKeys = selectedNodes?.map((node: any) => node.data) || [];
      setSelectedRows(selectedKeys);
    },
    [setSelectedRows]
  );

  const disableAdd: boolean = selectedRows?.length > 0;

  const disableBulkEdit: boolean =
    selectedRows?.length === 0 ||
    !selectedRows?.every(
      (row: any) =>
        row.resourceTypeId === selectedRows?.[0]?.resourceTypeId && row.resourceId === selectedRows?.[0]?.resourceId
    );

  const disableBulkDelete: boolean = selectedRows?.length === 0;

  const bulkDelete = async () => {
    try {
      await deleteValues(selectedRows?.map((valueItem: any) => valueItem?.id));
      message.success('Values deleted.');
    } catch (error: any) {
      console.error('Error deleting value:', error);
      message.error(`Error deleting values: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  };

  return (
    <>
      <HStack justify="space-between" align="center">
        <SearchBox
          onChange={(e: ChangeEvent<HTMLInputElement>) => setSearchText(e.target.value)}
          value={searchText}
          placeholder={tc('search')}
          className="search"
        />

        <HStack spacing="sm">
          <Button disabled={disableAdd} type="primary" icon="plus" onClick={() => setValuesDrawerState('add')}>
            {tn('buttons.add_value')}
          </Button>
          <Button disabled={disableBulkEdit} onClick={() => setValuesDrawerState('edit')}>
            {tn('buttons.edit_selected')}
          </Button>
          <Button
            type="danger"
            disabled={disableBulkDelete}
            onClick={() => {
              Modal.confirm({
                title: tn('bulkDeleteModal.title'),
                content: tn('bulkDeleteModal.content'),
                okText: tc('delete'),
                okType: 'danger',
                okButtonProps: { type: 'danger' },
                onOk: bulkDelete,
              });
            }}>
            {tn('buttons.delete_selected')}
          </Button>
        </HStack>
      </HStack>

      <br />

      <div className="table-container">
        <AgTable
          className={cx('table', !filteredValues?.length && 'empty')}
          domLayout="autoHeight"
          onGridReady={onGridReady}
          columnDefs={valueColumns}
          rowData={filteredValues}
          rowSelection="multiple"
          onSelectionChanged={handleRowSelected}
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
          getRowNodeId={(data) => data?.id}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
      </div>

      <EditValuesDrawer
        visible={valuesDrawerState}
        onClose={() => {
          setValuesDrawerState('hidden');
          gridApi?.deselectAll();
        }}
        selectedValues={selectedRows}
      />
    </>
  );
}
