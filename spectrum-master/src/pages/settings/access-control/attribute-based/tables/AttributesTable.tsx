import { useMemo, useState, ChangeEvent } from 'react';
import { ColDef, ColGroupDef, CellClassParams } from 'ag-grid-community';
import { Button } from 'antd';
import { cx } from '@emotion/css';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { HStack } from 'components/layout';
import SearchBox from 'components/SearchBox';
import { Text } from 'components/typography';

import { useListAttributesQuery } from 'store/access-control/abac/api';

import { tc, tNamespaced } from 'utils/i18nUtil';
import AddAttributeDrawer from '../forms/AddAttributeDrawer';
import AttributeActions from './AttributeActions';

const tn = tNamespaced('Settings.AccessControl.ABAC.attributeTable');

export default function AttributesTable() {
  const { data: attributesList } = useListAttributesQuery();
  const [selectedAttributeId, setSelectedAttributeId] = useState<string | undefined>();
  const [isAddAttributeDrawerVisible, setIsAddAttributeDrawerVisible] = useState(false);
  const [searchText, setSearchText] = useState<string>('');

  const filteredAttributes = useMemo(() => {
    if (!attributesList || !searchText) return attributesList;

    const searchLower = searchText.toLowerCase();
    return attributesList.filter((attribute) => {
      return (
        attribute?.name?.toLowerCase().includes(searchLower) ||
        attribute?.resourceTypeName?.toLowerCase().includes(searchLower) ||
        attribute?.resourceName?.toLowerCase().includes(searchLower) ||
        attribute?.dataType?.toLowerCase().includes(searchLower)
      );
    });
  }, [attributesList, searchText]);

  const attributeColumns: (ColDef | ColGroupDef)[] = useMemo(
    () => [
      {
        headerName: tn('headers.attribute_name'),
        field: 'name',
        resizable: true,
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
        headerName: tn('headers.datatype'),
        field: 'dataType',
        resizable: true,
      },
      {
        headerName: tn('headers.multivalued'),
        field: 'multiValued',
        resizable: true,
      },
      {
        headerName: tn('headers.used_in'),
        field: 'policies',
        resizable: true,
        cellRendererFramework: ({ data }: CellClassParams) => <Text className="used-in-link">{data?.policies}</Text>,
      },
      {
        headerName: tc('actions'),
        field: 'actions',
        cellRendererFramework: ({ data }: CellClassParams) => (
          <AttributeActions
            data={data}
            setIsAddAttributeDrawerVisible={(visible: boolean) => {
              setSelectedAttributeId(visible ? data?.id : undefined);
              setIsAddAttributeDrawerVisible(visible);
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

  return (
    <>
      <HStack justify="space-between" align="center">
        <SearchBox
          onChange={(e: ChangeEvent<HTMLInputElement>) => setSearchText(e.target.value)}
          value={searchText}
          placeholder={tc('search')}
          className="search"
        />
        <Button type="primary" icon="plus" onClick={() => setIsAddAttributeDrawerVisible(true)}>
          {tn('buttons.add_attribute')}
        </Button>
      </HStack>

      <br />

      <div className="table-container">
        <AgTable
          className={cx('table', !filteredAttributes?.length && 'empty')}
          domLayout="autoHeight"
          columnDefs={attributeColumns}
          rowData={filteredAttributes}
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
          getRowNodeId={(data) => data?.id}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
      </div>

      <AddAttributeDrawer
        visible={isAddAttributeDrawerVisible}
        onClose={() => {
          setIsAddAttributeDrawerVisible(false);
          setSelectedAttributeId(undefined);
        }}
        attributeId={selectedAttributeId}
      />
    </>
  );
}
