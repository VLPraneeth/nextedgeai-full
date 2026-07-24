import { ChangeEvent } from 'react';

import { SearchInput } from 'components/SearchInput';

import { usePipelineDetailsSearchParameters } from '../PipelineDetails/PipelineDetails.hooks';

export const PipelineDetailsSearchInput = () => {
  const { values: searchParams, updateSearchParams } = usePipelineDetailsSearchParameters();

  const handleSearch = (e: ChangeEvent<HTMLInputElement>) => {
    const params = [{ name: 'search', value: e.target.value }];
    updateSearchParams(params);
  };

  return <SearchInput defaultValue={searchParams.search} onChange={handleSearch} />;
};
