import { ColDef, ColGroupDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Checkbox from 'components/Checkbox';
import { HStack } from 'components/layout';
import SearchBox from 'components/SearchBox';
import { selectFetchingInstances, selectOrgName, useFilteredInstancesByOrg } from 'store/user/selectors';
import { getUserInstances } from 'store/user/thunks';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('CustomSynapse');
export interface SelectedInstanceScopeProps {
  selectedInstances: string[];
  setSelectedInstances: React.Dispatch<React.SetStateAction<string[]>>;
}

export function SelectedInstanceScope({ selectedInstances, setSelectedInstances }: SelectedInstanceScopeProps) {
  const [filterString, setFilterString] = useState('');
  const [, instancesByOrg] = useFilteredInstancesByOrg('');
  const [selectAllInstanceInOrg, setSelectAllInstanceInOrg] = useState(false);
  const orgName = useSelector(selectOrgName);
  const dispatch = useDispatch();

  const [gridApi, setGridApi] = useState<GridApi>();
  const fetchingInstances = useSelector(selectFetchingInstances);
  const onGridReady = (params: GridReadyEvent) => {
    setGridApi(params.api);
  };

  useEffect(() => {
    dispatch(getUserInstances());
  }, [dispatch]);

  const instancesInOrg = useMemo(
    () =>
      Object.values(instancesByOrg)
        .flatMap((item) => item.instances)
        .filter((instance) => instance.orgName === orgName),
    [instancesByOrg, orgName]
  );

  const onRowSelected = useCallback(() => {
    gridApi && setSelectedInstances(gridApi?.getSelectedRows().map((instance) => instance.syncariId) || []);
    const selectedInstanceIds = new Set(gridApi?.getSelectedRows().map((obj) => obj.syncariId));
    const isAllInstancesInOrgSelected = instancesInOrg.every((obj) => selectedInstanceIds.has(obj.syncariId));

    if (isAllInstancesInOrgSelected) {
      setSelectAllInstanceInOrg(true);
    } else {
      setSelectAllInstanceInOrg(false);
    }
  }, [gridApi, setSelectedInstances, instancesInOrg]);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: '',
        field: 'select',
        minWidth: 48,
        maxWidth: 48,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        suppressMovable: true,
      },
      {
        headerName: tc('name'),
        field: 'displayName',
        resizable: true,
      },
      {
        headerName: tc('instance_id'),
        field: 'syncariId',
        resizable: true,
      },
      {
        headerName: tc('subscription'),
        field: 'orgName',
        resizable: true,
      },
    ];
  }, []);

  const data = Object.values(instancesByOrg)
    .flatMap((item) => item.instances)
    .filter((instance) => {
      return (
        instance.displayName?.toLowerCase().includes(filterString.toLowerCase()) ||
        instance.orgName?.toLowerCase().includes(filterString.toLowerCase()) ||
        instance.syncariId?.toLowerCase().includes(filterString.toLowerCase())
      );
    });

  return (
    <div className="custom-synapse-share-panel__instances-container">
      <span className="synri-label">{tn('select_instances')}</span>
      <div className="custom-synapse-share-panel__instances-table-container">
        <HStack justify="space-between" className="custom-synapse-share-panel__instances-top-panel">
          <SearchBox
            onChange={(event) => setFilterString(event.target.value)}
            value={filterString}
            placeholder={tc('search')}
            className="custom-synapse-share-panel__instances-search"
          />

          <Checkbox
            checked={selectAllInstanceInOrg}
            onChange={(e) => {
              setSelectAllInstanceInOrg(e.target.checked);
              if (e.target.checked) {
                gridApi?.forEachNode((node) => {
                  if (node.data.orgName === orgName) {
                    node.setSelected(true);
                  } else {
                    node.setSelected(false);
                  }
                });
              }
            }}>
            {tn('all_in_the_subscription')}
          </Checkbox>
        </HStack>
        <AgTable
          className={cx('custom-synapse-share-panel__instances-table', !data?.length && 'empty')}
          columnDefs={columns}
          domLayout="autoHeight"
          loading={fetchingInstances === 'loading'}
          rowData={data}
          onGridReady={onGridReady}
          onFirstDataRendered={() => {
            gridApi?.forEachNode((node) => {
              if (selectedInstances.includes(node.data.syncariId)) {
                node.setSelected(true);
              }
            });
          }}
          onRowSelected={onRowSelected}
          rowSelection="multiple"
          suppressCellSelection
          suppressRowClickSelection
          enableCellTextSelection
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
          getRowNodeId={(data) => data.syncariId}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
      </div>
    </div>
  );
}
