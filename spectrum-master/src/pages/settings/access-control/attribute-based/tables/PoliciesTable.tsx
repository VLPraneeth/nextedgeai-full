import { useMemo, useState, ChangeEvent } from 'react';
import { ColDef, ColGroupDef, CellClassParams } from 'ag-grid-community';
import { Button, Tooltip } from 'antd';
import { cx } from '@emotion/css';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { HStack } from 'components/layout';
import SearchBox from 'components/SearchBox';
import { Text } from 'components/typography';
import InlineMessage from 'components/InlineMessage';

import { useListPoliciesQuery } from 'store/access-control/abac/api';

import { tc, tNamespaced } from 'utils/i18nUtil';
import AddPolicyDrawer from '../forms/AddPolicyDrawer';
import PolicyActions from './PolicyActions';

const tn = tNamespaced('Settings.AccessControl.ABAC.policiesTable');

export default function PoliciesTable() {
  const { data: policyList } = useListPoliciesQuery();
  const [selectedPolicyId, setSelectedPolicyId] = useState<string | undefined>();
  const [searchText, setSearchText] = useState<string>('');

  const filteredPolicies = useMemo(() => {
    if (!policyList || !searchText) return policyList;

    const searchLower = searchText.toLowerCase();
    return policyList.filter((policy) => {
      return (
        policy?.name?.toLowerCase().includes(searchLower) ||
        policy?.resourceTypeName?.toLowerCase().includes(searchLower) ||
        policy?.resourceName?.toLowerCase().includes(searchLower) ||
        policy?.permissions?.some((permission) => permission?.toLowerCase()?.includes(searchLower))
      );
    });
  }, [policyList, searchText]);

  const [isAddDrawerVisible, setIsAddDrawerVisible] = useState(false);

  const policyColumns: (ColDef | ColGroupDef)[] = useMemo(
    () => [
      {
        headerName: tn('headers.name'),
        field: 'name',
        resizable: true,
        cellRendererFramework: ({ data }: CellClassParams) => (
          <span className="ag-cell-value">
            <Text>{data?.name}</Text>
          </span>
        ),
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
        headerName: tn('headers.policy'),
        field: 'userFriendlyCondition',
        resizable: true,
        cellRendererFramework: ({ data }: CellClassParams) => (
          <Tooltip title={data?.userFriendlyCondition}>
            <Text className="policy-condition">{data?.userFriendlyCondition}</Text>
          </Tooltip>
        ),
      },
      {
        headerName: tn('headers.permissions'),
        field: 'permissions',
        resizable: true,
      },
      {
        headerName: tc('actions'),
        field: 'actions',
        cellRendererFramework: ({ data }: CellClassParams) => (
          <PolicyActions
            data={data}
            setIsAddDrawerVisible={(visible: boolean) => {
              setSelectedPolicyId(visible ? data?.id : undefined);
              setIsAddDrawerVisible(visible);
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
      <div>
        <InlineMessage type="info">{tn('info_message')}</InlineMessage>
      </div>
      <HStack justify="space-between" align="center">
        <SearchBox
          onChange={(e: ChangeEvent<HTMLInputElement>) => setSearchText(e.target.value)}
          value={searchText}
          placeholder={tc('search')}
          className="search"
        />
        <Button type="primary" icon="plus" onClick={() => setIsAddDrawerVisible(true)}>
          {tn('buttons.add_policy')}
        </Button>
      </HStack>

      <br />

      <div className="table-container">
        <AgTable
          className={cx('table', !filteredPolicies?.length && 'empty')}
          domLayout="autoHeight"
          columnDefs={policyColumns}
          rowData={filteredPolicies}
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
          getRowNodeId={(data) => data?.id}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
      </div>

      <AddPolicyDrawer
        visible={isAddDrawerVisible}
        onClose={() => {
          setIsAddDrawerVisible(false);
          setSelectedPolicyId(undefined);
        }}
        policyId={selectedPolicyId}
      />
    </>
  );
}
