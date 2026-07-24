import { CellClassParams } from 'ag-grid-community';
import { map, orderBy } from 'lodash';
import { createContext, useContext, useEffect, useState } from 'react';

import { ReactComponent as OpenInNewIcon } from 'assets/icons/open-in-new.svg';
import { ReactComponent as RestoreIcon } from 'assets/icons/restore-version.svg';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Button from 'components/Button';
import Checkbox from 'components/Checkbox';
import FilterButton from 'components/filter-components/FilterButton';
import { withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import Tooltip from 'components/tooltip/Tooltip';
import { Text } from 'components/typography';
import useUserLocalMoment from 'hooks/moment';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetPipelineVersionListQuery } from 'store/pipeline/api';
import { showRestoreVersionModal } from 'store/pipeline/slice';
import { PipelineVersion } from 'store/pipeline/types';
import { navigateTo } from 'utils/AppUtil';
import { SHORT_DATE_TIME_TZ_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import useSetState from 'utils/useSetState';

import { useFilteredVersions, useVersionFilterOptionsData, VersionFilterOptionsData } from './PipelineVersions.hooks';
import PipelineVersionsFilterPanel from './PipelineVersionsFilterPanel';

import './PipelineVersions.scss';

const tn = tNamespaced('PipelineVersions');

const VersionsTableContext = createContext<{
  entityId: string;
  versions: PipelineVersion[];
  selectedRows: string[];
  setSelectedRows: any;
} | null>(null);

const FormattedDate = ({ data }: { data: PipelineVersion }) => {
  const moment = useUserLocalMoment();
  const formattedDate = moment(data.createdAt).format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT);
  return <Text>{formattedDate}</Text>;
};

interface VersionActionsProps {
  data: PipelineVersion;
}

const VersionActions = ({ data }: VersionActionsProps) => {
  const dispatch = useEnhancedDispatch();
  const context = useContext(VersionsTableContext);

  return (
    <div className="pipeline-versions__actions">
      <Tooltip mouseEnterDelay={0.5} mouseLeaveDelay={0} title={tn('open_version_detail')}>
        <OpenInNewIcon
          onClick={() => {
            const sortedVersions = orderBy(context?.versions, 'versionNumber', 'desc');
            const previousVersion = sortedVersions.find(({ versionNumber }) => versionNumber < data.versionNumber);

            navigateTo(
              makeUrl(RouteConstants.ENTITY_VERSIONS_COMPARE, {
                entityId: context?.entityId,
                versionOneId: previousVersion ? previousVersion.versionId : data.versionId,
                versionTwoId: previousVersion ? data.versionId : undefined,
              })
            );
          }}
          className="field-data-type-icon"
          role="img"
          aria-label={tn('open_version_detail')}
        />
      </Tooltip>
      <Tooltip mouseEnterDelay={0.5} mouseLeaveDelay={0} title={tn('restore_version')}>
        <RestoreIcon
          onClick={() => {
            dispatch(showRestoreVersionModal({ visible: true, versionId: data.versionId, name: data.name }));
          }}
          className="field-data-type-icon"
          role="img"
          aria-label={tn('restore_version')}
        />
      </Tooltip>
    </div>
  );
};

export const SelectRowRenderer = ({ data }: CellClassParams) => {
  const context = useContext(VersionsTableContext);
  const checked = context?.selectedRows.includes(data.versionId);

  return (
    <Checkbox
      checked={checked}
      disabled={context?.selectedRows.length === 2 && !context?.selectedRows.includes(data.versionId)}
      onChange={(evt) => {
        context?.setSelectedRows((currentSelection: string[]) => {
          if (evt.target.checked && !currentSelection.includes(data.versionId)) {
            return [...currentSelection, data.versionId];
          } else if (!evt.target.checked && currentSelection.includes(data.versionId)) {
            return currentSelection.filter((id) => id !== data.versionId);
          }
          return currentSelection;
        });
      }}
    />
  );
};

const columns = [
  {
    headerName: '',
    colId: 'selectedRow',
    field: 'selectedRow',
    minWidth: 44,
    maxWidth: 44,
    cellRendererFramework: SelectRowRenderer,
  },
  {
    headerName: tn('version_pound'),
    colId: 'versionNumber',
    field: 'versionNumber',
    minWidth: 82,
    maxWidth: 82,
    type: 'rightAligned',
  },
  {
    headerName: tn('version_name'),
    colId: 'name',
    field: 'name',
    cellRendererFramework: ({ data }: { data: PipelineVersion }) => {
      return (
        <span className="ag-cell-value" title={data.name}>
          {data.name}
        </span>
      );
    },
  },
  {
    headerName: tn('saved_by'),
    colId: 'createdBy',
    field: 'createdBy',
  },
  {
    headerName: tn('saved_on'),
    colId: 'createdAt',
    field: 'createdAt',
    cellRendererFramework: FormattedDate,
  },
  {
    headerName: tn('number_of_changes'),
    colId: 'numberOfChanges',
    field: 'numberOfChanges',
  },
  {
    headerName: tn('summary'),
    colId: 'summary',
    field: 'summary',
    cellRendererFramework: ({ data }: { data: PipelineVersion }) => {
      return (
        <span className="ag-cell-value" title={data.summary}>
          {data.summary}
        </span>
      );
    },
  },
  {
    headerName: tn('action_type'),
    colId: 'actionType',
    field: 'actionType',
  },
  {
    headerName: '',
    colId: 'actions',
    field: 'actions',
    cellRendererFramework: VersionActions,
    flex: 0,
    minWidth: 70,
    maxWidth: 70,
  },
];

export interface PipelineVersionsProps {
  entityId: string;
}

export const pipelineVersionsFiltersInitialState: VersionFilterOptionsData = {
  versionNumber: [],
  name: [],
  createdBy: [],
  actionType: [],
  startDate: '',
  endDate: '',
};

const PipelineVersions = ({ entityId }: PipelineVersionsProps) => {
  const { data: allVersions, isLoading } = useGetPipelineVersionListQuery(entityId);

  const versionFilterData = useVersionFilterOptionsData(allVersions);

  const [selectedRows, setSelectedRows] = useState<string[]>([]);

  const [filterOpen, setFilterOpen] = useState(false);

  const [activeFilters, setActiveFilters] = useSetState(pipelineVersionsFiltersInitialState);

  const filteredVersions = useFilteredVersions(activeFilters, allVersions);

  let compareText = tn('choose_two_versions');
  const [versionOneId, versionTwoId] = selectedRows || [];
  let versionOne: PipelineVersion | undefined;
  let versionTwo: PipelineVersion | undefined;

  if (selectedRows.length > 0) {
    versionOne = allVersions?.find((version) => version.versionId === versionOneId);

    if (versionTwoId) {
      versionTwo = allVersions?.find((version) => version.versionId === versionTwoId);
      if (versionOne && versionTwo) {
        compareText = tn('compare_versions', {
          versionNumber: versionOne?.versionNumber,
          versionName: versionOne?.name,
          secondVersionNumber: versionTwo?.versionNumber,
        });
      }
    } else {
      compareText = tn('choose_another_version', {
        versionNumber: versionOne?.versionNumber,
        versionName: versionOne?.name,
      });
    }
  }

  // We always compare the earlier version on the left-hand side so if the user
  // selects a later version first we need to swap the order.
  useEffect(() => {
    if (versionOne && versionTwo && versionTwo.versionNumber < versionOne.versionNumber) {
      setSelectedRows([versionTwo.versionId, versionOne.versionId]);
    }
  }, [versionOne, versionTwo]);

  const activeFilterCount = map(activeFilters, (filter) => filter && filter.length > 0).filter(Boolean).length;

  return (
    <>
      <Stack className="pipeline-versions" fill>
        <HStack justify="end">
          <Text>{compareText}</Text>
          <Button
            disabled={selectedRows.length !== 2}
            type="default"
            onClick={() => {
              navigateTo(makeUrl(RouteConstants.ENTITY_VERSIONS_COMPARE, { entityId, versionOneId, versionTwoId }));
            }}>
            {tn('compare')}
          </Button>

          <FilterButton onClick={() => setFilterOpen(!filterOpen)} isFilterActive={Boolean(activeFilterCount)} />
        </HStack>
        <VersionsTableContext.Provider value={{ entityId, versions: allVersions || [], selectedRows, setSelectedRows }}>
          <AgTable
            columnDefs={columns}
            loading={isLoading}
            rowData={filteredVersions}
            getRowNodeId={(transaction) => [transaction.syncariId, transaction.createdAt].join(':')}
            suppressColumnVirtualisation
            sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
            enableCellTextSelection
            suppressCellSelection
            suppressRowClickSelection
            noRowsOverlayComponentParams={{
              description: tn(activeFilterCount ? 'no_versions_match_filters' : 'no_versions_found'),
            }}
          />
        </VersionsTableContext.Provider>
      </Stack>
      <PipelineVersionsFilterPanel
        visible={filterOpen}
        onClose={() => setFilterOpen(false)}
        versionFilterData={versionFilterData}
        activeFilters={activeFilters}
        setActiveFilters={setActiveFilters}
      />
    </>
  );
};

export default withI18n(PipelineVersions, 'PipelineVersions');
