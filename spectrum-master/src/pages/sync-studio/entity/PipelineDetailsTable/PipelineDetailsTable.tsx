import cx from 'classnames';
import { useEffect } from 'react';

import { PageBasedPagination } from 'components/AgTable';
import AgTable from 'components/AgTable/AgTable';
import FilterButton from 'components/filter-components/FilterButton';
import Spinner from 'components/Spinner';
import { tNamespaced } from 'utils/i18nUtil';

import { PipelineDetailsPanelNames, usePipelineDetailsSearchParameters } from '../PipelineDetails';
import { PipelineDetailsSearchInput } from '../PipelineDetailsSearchInput';
import { usePipelineDetailsTable } from './PipelineDetailsTable.hooks';
import { getIsFilterActive } from './PipelineDetailsTable.utils';

import './PipelineDetailsTable.scss';

const tn = tNamespaced('PipelineDetailsTable');

export interface PipelineDetailsTableProps {
  className?: string;
}

export const PipelineDetailsTable = ({ className }: PipelineDetailsTableProps) => {
  const { values: searchParams, updateSearchParams } = usePipelineDetailsSearchParameters();

  const {
    columnDefs,
    frameworkComponents,
    getNextPage,
    getPreviousPage,
    goToPage,
    pageInfo,
    pageSize,
    paginatedRowData,
    setPageSize,
    showLoadingState,
  } = usePipelineDetailsTable();

  const isFilterActive = getIsFilterActive(searchParams);

  const handleFilterButtonClick = () => {
    const params = [{ name: 'panel', value: PipelineDetailsPanelNames.FILTER }];

    updateSearchParams(params);
  };

  useEffect(() => {
    // Automtically go to the first page when there's a new search / filter
    // and the filter panel is not open
    if (searchParams && searchParams.panel === '') {
      goToPage(0);
    }
  }, [goToPage, searchParams]);

  if (showLoadingState) {
    return (
      <div className="pipeline-details-table--loading">
        <div>
          <Spinner />
          <span>{tn('loading')}</span>
        </div>
      </div>
    );
  }

  return (
    <div className={cx(className, 'pipeline-details-table')}>
      <div className="pipeline-details-table__filters">
        <PipelineDetailsSearchInput />
        <FilterButton onClick={handleFilterButtonClick} isFilterActive={isFilterActive} />
      </div>
      <div className="pipeline-details-table__main">
        <AgTable
          columnDefs={columnDefs}
          rowData={paginatedRowData}
          frameworkComponents={frameworkComponents}
          suppressColumnVirtualisation
          suppressCellSelection
          pagerComponent={
            <PageBasedPagination
              allowPageSizeChange
              pageInfo={pageInfo}
              pageSize={pageSize}
              onPageSizeChange={(pageSize) => setPageSize(pageSize)}
              onRequestNextPage={getNextPage}
              onRequestPreviousPage={getPreviousPage}
            />
          }
        />
      </div>
    </div>
  );
};
