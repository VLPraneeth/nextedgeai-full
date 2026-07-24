import { Icon } from 'antd';
import { ChangeEventHandler, useState } from 'react';

import Button from 'components/Button';
import Checkbox, { CheckboxChangeEvent } from 'components/Checkbox';
import InputFilter from 'components/InputFilter';
import { Stack } from 'components/layout';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import usePersistedState from 'hooks/usePersistedState';
import useQueryParams from 'hooks/useQueryParams';
import { PermissionsComparisonOperator, useUserHasPermission } from 'hooks/useUserHasPermission';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { DataCard, Dataset } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { EmptyPanelContent } from '../empty-panel-content/EmptyPanelContent';
import { AuthoringSidebarListItem } from './AuthoringSidebarListItem';
import './AuthoringSidebarList.scss';

const defaultFilterState = {
  custom: false,
  system: false,
};

const tn = tNamespaced('InsightsStudio');

interface AuthoringSidebarListProps {
  list: (DataCard | Dataset)[];
  listType: 'dataset' | 'datacard';
}

export const AuthoringSidebarList = ({ list, listType }: AuthoringSidebarListProps) => {
  const isDataset = listType === 'dataset';
  const { navigateTo } = useUnifiedDataCardNavigate();
  const { userHasPermission } = useUserHasPermission(PermissionsComparisonOperator.AND);

  const [queryParams] = useQueryParams<{ datasetName?: string; datacardName?: string }>();

  const [searchTerm, setSearchTerm] = useState(queryParams?.datasetName || queryParams?.datacardName || '');
  const [libraryFilters, setLibraryFilters] = usePersistedState<typeof defaultFilterState>(
    listType + '-library-filter',
    defaultFilterState
  );

  const openCreate = () => navigateTo(isDataset ? 'DATASET' : 'DATACARD', 'new');

  const clearAllFilters = () => {
    setSearchTerm('');
    setLibraryFilters(defaultFilterState);
  };

  const handleSearchChange: ChangeEventHandler<HTMLInputElement> = (e) => {
    setSearchTerm(e.target.value);
  };

  const filteredList =
    list
      ?.slice() // Create new array to allow sorting (rtk-q array is frozen)
      ?.sort((a, b) => a.displayName.localeCompare(b.displayName))
      ?.filter((item) => {
        if (
          // Remove card that doesn't match current search
          !item.displayName.toLowerCase().includes(searchTerm.toLowerCase()) ||
          // Remove seeded cards if custom filter is on
          (libraryFilters.custom && item.seeded) ||
          // Remove custom cards if system filter is on
          (libraryFilters.system && !item.seeded)
        ) {
          return false;
        }

        return true;
      }) ?? [];

  const handleFilterChange = (e: CheckboxChangeEvent) => {
    setLibraryFilters({ ...libraryFilters, [e.target.name!]: e.target.checked });
  };

  return (
    <div className="authoring-sidebar-list">
      <div className="authoring-sidebar-list__section">
        <div>
          <InputFilter
            onChange={handleSearchChange}
            containerClassName="authoring-sidebar-list__filter"
            clearFilters={clearAllFilters}
            filterCount={Object.values(libraryFilters).filter(Boolean).length}
            value={searchTerm}
            placeholder={tc('search')}
            filterChildren={
              <Stack spacing="sm">
                <Checkbox checked={libraryFilters.custom} name="custom" onChange={handleFilterChange}>
                  {isDataset ? tn('data_set_custom') : tn('data_cards_custom')}
                </Checkbox>
                <Checkbox checked={libraryFilters.system} name="system" onChange={handleFilterChange}>
                  {isDataset ? tn('data_set_system') : tn('data_cards_system')}
                </Checkbox>
              </Stack>
            }
          />
        </div>
        {userHasPermission([AllPermissions.CREATE_DATACARD, AllPermissions.UPDATE_DATACARD]) && (
          <Button onClick={openCreate} type="primary" className="authoring-sidebar-list__new-button">
            <Icon type="plus" />
            {tc('new')}
          </Button>
        )}
      </div>

      {!filteredList?.length ? (
        // There are data sets, but none match current filters
        <EmptyPanelContent title={tn('no_matching_cards_title')} body={tn('no_matching_cards_body')} />
      ) : (
        <ScrollableArea className="authoring-sidebar-list__section">
          <div className="authoring-sidebar-list__list-container">
            {filteredList.map((item) => {
              return <AuthoringSidebarListItem key={item.id} item={item} itemType={listType} />;
            })}
          </div>
        </ScrollableArea>
      )}
    </div>
  );
};
