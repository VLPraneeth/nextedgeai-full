import { useNavigate } from '@reach/router';
import { Tooltip } from 'antd';
import Dropdown from 'antd/lib/dropdown';
import Icon from 'antd/lib/icon';
import Menu from 'antd/lib/menu';
import cx from 'classnames';
import isEqual from 'fast-deep-equal';
import { cloneDeep } from 'lodash';
import { useEffect, useMemo, useState } from 'react';
import Truncate from 'react-truncate';

import Button from 'components/Button';
import Can from 'components/Can';
import { useI18nContext } from 'components/I18nProvider';
import Filter, { ExternalOnChangeHandler as FilterOnChangeHandler, FilterRef } from 'components/inputs/filter';
import { ConditionValue, FilterValue, isGroupPredicate, LeftValue } from 'components/inputs/types';
import { HStack, Stack } from 'components/layout';
import { TableFiltersContainer } from 'components/TableFilters';
import { Text } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useEntityFiltersList } from 'store/data-studio/hooks';
import { selectFilterCreatingStatus, selectFilterDeletingStatus } from 'store/data-studio/selectors';
import { EntityFilter } from 'store/data-studio/types';
import { usePicklistValues } from 'store/picklists/hooks';
import AppConstants from 'utils/AppConstants';
import { colors } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import './FilterPanel.less';

// recursive predicate to check if our criteria is empty
const isPredicateEmpty = (predicate: FilterValue | ConditionValue): boolean => {
  if (isGroupPredicate(predicate)) {
    return predicate.predicates.every(isPredicateEmpty);
  }

  return !predicate.left;
};

// trigger a fn after the filter has been created
const useFilterWasCreatedForEntityCallback = (entityId: string, fn: () => void) => {
  const creatingStatus = useEnhancedSelector((state) => selectFilterCreatingStatus(state, entityId));
  const previousStatus = usePreviousValue(creatingStatus);

  useEffect(() => {
    if (!previousStatus) {
      return;
    }

    if (previousStatus !== AppConstants.FETCH_STATUS.SUCCESS && creatingStatus === AppConstants.FETCH_STATUS.SUCCESS) {
      fn();
    }
  }, [creatingStatus, fn, previousStatus]);
};

export interface DataStudioFilterPanelProps {
  entityId: string;
  error?: string;
  fieldValues: LeftValue[];
  filter?: EntityFilter | Partial<EntityFilter>;
  initialFilterId?: string;
  onApplyFilter: (filter: Partial<EntityFilter> | undefined) => void;
  onRequestRefreshData: () => void;
  onRequestResetFilter: () => void;
  onRequestShowFiltersList: () => void;
  onRequestSaveFilter: (filter: Partial<EntityFilter>) => void;
  filterControlRef: React.MutableRefObject<FilterRef | null>;
}

const DataStudioFilterPanel = ({
  entityId,
  error,
  fieldValues,
  filter = {} as Partial<EntityFilter>,
  onApplyFilter,
  onRequestRefreshData,
  onRequestResetFilter,
  filterControlRef,
  onRequestShowFiltersList,
  onRequestSaveFilter,
}: DataStudioFilterPanelProps) => {
  const navigate = useNavigate();
  const { tc, tn } = useI18nContext();
  const [draftFilter, setDraftFilter] = useState<Partial<EntityFilter>>(cloneDeep(filter));
  const [picklistValues, fetchPicklistValues] = usePicklistValues();
  const deletingStatus = useEnhancedSelector((state) => selectFilterDeletingStatus(state, filter?.id || ''));
  const filterId = filter?.id || '';

  const { data: filtersData } = useEntityFiltersList({
    entityId,
    count: 100,
    direction: 'next',
  });

  const filterCriteriaIsEmpty = draftFilter?.criteria ? isPredicateEmpty(draftFilter.criteria) : true;

  const draftFilterHasChanges = useMemo(() => {
    if (draftFilter && filter) {
      // check full filter equality
      // criteria may match but no longer have the same ID or other metadata
      // if an ad hoc filter is applied
      return !isEqual(draftFilter, filter);
    }

    return Boolean(draftFilter && !draftFilter.id && draftFilter.criteria && !filterCriteriaIsEmpty);
  }, [draftFilter, filter, filterCriteriaIsEmpty]);

  // updatedBy is only present on saved filters, if it's missing then this
  // is an ad hoc filter
  const isSavedFilterWithUnsavedChanges = Boolean(draftFilterHasChanges && draftFilter?.updatedBy);

  const applyFilter = (evt: React.FormEvent<HTMLFormElement>) => {
    evt.preventDefault();

    if (draftFilterHasChanges) {
      // create a new Ad Hoc filter with criteria
      // but none of the saved filter metadata. This prevents being given the
      // "Save Changes" option
      const adHocFilterToApply = {
        criteria: draftFilter.criteria,
        entityId: draftFilter.syncariEntityId,
      };
      onApplyFilter(adHocFilterToApply);
      setDraftFilter(adHocFilterToApply);
    } else {
      onRequestRefreshData();
    }
  };

  const onFilterChange: FilterOnChangeHandler = (_, __, predicate: FilterValue) => {
    // if we've cleared the predicate and we started with a bookmarked filter, reset
    if ((!predicate || isPredicateEmpty(predicate)) && filter?.id) {
      //user has deleted all items from the Predicate
      onRequestResetFilter();
    } else {
      setDraftFilter((prev) => ({ ...prev, criteria: predicate }));
    }
  };

  useToastForFetchStatusChange(deletingStatus, {
    error: tn('deleting_filter_failed', { name: filter?.name }),
    success: tn('deleting_filter_success', { name: filter?.name }),
  });

  useEffect(() => {
    if (deletingStatus === AppConstants.FETCH_STATUS.SUCCESS) {
      // after the filter has been deleted, make sure we reset and update the URL
      onRequestResetFilter();
    }
  }, [deletingStatus, onRequestResetFilter]);

  // when we create a new entity, it should be the first item
  // in the list, navigate to that filter's url
  // TODO: move applying filter after save into the UpdateFilterDrawer
  // where save api call is being made.
  useFilterWasCreatedForEntityCallback(entityId, () => {
    const firstFilter = filtersData?.filters?.[0];

    if (firstFilter) {
      navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }, { filterId: firstFilter.id }));
    }
  });

  return (
    <TableFiltersContainer arrowClassName="data-studio-disclosure-arrow" className="data-studio-filter-container">
      <Stack>
        <div>
          <form onSubmit={applyFilter}>
            <div className={cx('data-studio-filters-list')}>
              <Filter
                ref={filterControlRef}
                key={`${entityId}:${filterId}`}
                className="data-studio-filter"
                onChange={onFilterChange}
                name={entityId}
                picklistValues={picklistValues}
                fetchPicklistValues={fetchPicklistValues}
                value={filter?.criteria || draftFilter?.criteria}
                fieldValues={fieldValues}
              />
            </div>
            <div className="data-studio-filter-toolbar-content">
              <div>
                {error ? (
                  <HStack spacing="xs">
                    <Icon theme="twoTone" type="exclamation-circle" twoToneColor={colors.red500} />
                    <Tooltip title={error} placement="top">
                      <div className="data-studio-filter-error-text">
                        <Truncate lines={1}>{error}</Truncate>
                      </div>
                    </Tooltip>
                  </HStack>
                ) : (
                  draftFilterHasChanges && (
                    <HStack spacing="xs">
                      <Icon type="info-circle" />
                      <Text color="black">{tn('filters_changed_warning')}</Text>
                    </HStack>
                  )
                )}
              </div>
              <HStack spacing="sm">
                <Button onClick={onRequestShowFiltersList} size="small">
                  {tn('show_filters')}
                </Button>
                {filterId ? (
                  // show this for existing filters
                  <Dropdown
                    trigger={['click']}
                    overlay={
                      <Menu
                        onClick={(evt) => {
                          switch (evt.key) {
                            case 'save':
                              draftFilter && onRequestSaveFilter(draftFilter);
                              break;
                            case 'save_as': {
                              onRequestSaveFilter({ criteria: draftFilter?.criteria });
                              break;
                            }
                          }
                        }}>
                        {isSavedFilterWithUnsavedChanges && <Menu.Item key="save">{tn('save_btn')}</Menu.Item>}
                        <Menu.Item key="save_as">{tn('save_as_btn')}</Menu.Item>
                      </Menu>
                    }>
                    <Button
                      htmlType="button"
                      size="small"
                      type={isSavedFilterWithUnsavedChanges ? 'primary' : 'default'}>
                      {tc('save')}
                      <Icon type="down" />
                    </Button>
                  </Dropdown>
                ) : (
                  // This is for new filters
                  <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                    <Button
                      htmlType="button"
                      type="primary"
                      size="small"
                      disabled={Boolean(filter?.id && !draftFilterHasChanges ? true : filterCriteriaIsEmpty)}
                      onClick={() => {
                        draftFilter && onRequestSaveFilter(draftFilter);
                      }}>
                      {tc('save')}
                    </Button>
                  </Can>
                )}
                {/* Apply Filter */}
                <Button htmlType="submit" size="small" type="primary" icon="caret-right" aria-label={tn('run_filter')}>
                  {tn('run_filter')}
                </Button>
              </HStack>
            </div>
          </form>
        </div>
      </Stack>
    </TableFiltersContainer>
  );
};

export default DataStudioFilterPanel;
