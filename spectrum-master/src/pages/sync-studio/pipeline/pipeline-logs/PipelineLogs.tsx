//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useCallback, useRef, useState } from 'react';

import FilterButton from 'components/filter-components/FilterButton';
import { withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { TableFilterProvider, TableFilterProviderRef } from 'components/TableFilters';
import { useQueryFilterValues } from 'hooks/useFiltersInQueryParams';

import { PipelineLogsContextProvider, usePipelineLogsContext } from './PipelineLogs.context';
import PipelineLogsDetailsPanel from './PipelineLogsDetails';
import PipelineLogsFilterPanel from './PipelineLogsFilterPanel';
import { LogsFilterValues, PipelineLogsTable } from './PipelineLogsTable';

import './PipelineLogs.scss';

export const defaultPipelineLogsFilters = {
  syncariRecordId: '',
};

const PipelineLogs = ({ entityId }: { entityId: string }) => {
  const filterPanelRef = useRef<TableFilterProviderRef | null>(null);

  const [filterPanelVisible, setFilterPanelVisible] = useState(false);

  const { filterValues: activeFilters, filterIsActive } = useQueryFilterValues<LogsFilterValues>(
    defaultPipelineLogsFilters
  );

  const { setJsonData } = usePipelineLogsContext();

  const onDetailsClose = useCallback(() => {
    setJsonData(null);
  }, [setJsonData]);

  return (
    <TableFilterProvider ref={filterPanelRef} initiallyVisible>
      <PipelineLogsContextProvider>
        <Stack className="pipeline-logs" fill spacing="lg">
          <div className="pipeline-logs__filter-container">
            <FilterButton onClick={() => setFilterPanelVisible(!filterPanelVisible)} isFilterActive={filterIsActive} />
          </div>
          <PipelineLogsTable entityId={entityId} filter={activeFilters} />
          <PipelineLogsDetailsPanel onRequestClose={onDetailsClose} />
        </Stack>
        <PipelineLogsFilterPanel visible={filterPanelVisible} onClose={() => setFilterPanelVisible(false)} />
      </PipelineLogsContextProvider>
    </TableFilterProvider>
  );
};

export default withI18n(PipelineLogs, 'PipelineLogs');
