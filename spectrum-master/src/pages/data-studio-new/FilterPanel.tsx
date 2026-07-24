import { useNavigate } from '@reach/router';
import { Tooltip } from 'antd';
import Dropdown from 'antd/lib/dropdown';
import Icon from 'antd/lib/icon';
import Menu from 'antd/lib/menu';
import cx from 'classnames';
import isEqual from 'fast-deep-equal';
import { cloneDeep } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';
import Truncate from 'react-truncate';

import Button from 'components/Button';
import Can from 'components/Can';
import { useI18nContext } from 'components/I18nProvider';
import { ExternalOnChangeHandler as FilterOnChangeHandler, FilterRef } from 'components/inputs/filter';
import Filter from './Filters';
import { ConditionValue, FilterValue, isGroupPredicate, LeftValue } from 'components/inputs/types';
import { HStack, Stack } from 'components/layout';
import { TableFiltersContainer } from 'components/TableFilters';
import { Text } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useEntityFiltersList } from 'store/data-studio/hooks';
import {
  selectFilterCreatingStatus,
  selectFilterDeletingStatus,
  selectFilterUpdatingStatus,
} from 'store/data-studio/selectors';
import { EntityFilter } from 'store/data-studio/types';
import { usePicklistValues } from 'store/picklists/hooks';
import AppConstants from 'utils/AppConstants';
import { colors } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import { createEntityFilter, updateEntityFilter } from 'store/data-studio/thunks';
import { useEnhancedDispatch } from 'hooks/redux';

import './FilterPanel.less';
import DataStudioSaveFilter from './Filters/DataStudioSaveFilter';
import Checkbox from 'components/Checkbox';
import { processPredicates } from './utils';

export type FilterFormValues = Pick<EntityFilter, 'name' | 'description' | 'bookmarked' | 'tags'>;

// recursive predicate to check if our criteria is empty
const isPredicateEmpty = (predicate: FilterValue | ConditionValue): boolean => {
  if (isGroupPredicate(predicate)) {
    return predicate.predicates.every(isPredicateEmpty);
  }

  return !predicate.left || !predicate.operator;
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
  filterInEditor?: EntityFilter | Partial<EntityFilter> | null;
  isSaveFilterChecked?: boolean;
  onSaveFilterCheckChange?: (checked: boolean) => void;
  onApplyFilter: (filter: Partial<EntityFilter> | undefined) => void;
  onRequestRefreshData: () => void;
  onRequestResetFilter: () => void;
  onRequestShowFilterPanel: () => void;
  onRequestSaveFilter: (filter: Partial<EntityFilter>) => void;
  filterControlRef: React.MutableRefObject<FilterRef | null>;
  currentMode: string;
}

const DataStudioFilterPanel = ({
  entityId,
  fieldValues,
  filter = {} as Partial<EntityFilter>,
  onApplyFilter,
  onRequestRefreshData,
  onRequestSaveFilter,
  onRequestResetFilter,
  filterControlRef,
  filterInEditor,
  isSaveFilterChecked,
  onSaveFilterCheckChange,
  onRequestShowFilterPanel,
  error,
  currentMode,
}: DataStudioFilterPanelProps) => {
  const navigate = useNavigate();
  const { tc, tn } = useI18nContext();

  const [draftFilter, setDraftFilter] = useState<Partial<EntityFilter>>(cloneDeep(filter));
  const [picklistValues, fetchPicklistValues] = usePicklistValues();
  const deletingStatus = useEnhancedSelector((state) => selectFilterDeletingStatus(state, filter?.id || ''));
  const creatingStatus = useEnhancedSelector((state) => selectFilterCreatingStatus(state, entityId));
  const updatingStatus = useEnhancedSelector((state) =>
    selectFilterUpdatingStatus(state, filter?.id || filterInEditor?.id || '')
  );
  const filterId = filter?.id || '';
  const dispatch = useEnhancedDispatch();
  const [formValues, setFormValues] = useState<Partial<EntityFilter> | FilterFormValues>(() => ({
    name: filterInEditor?.name || '',
    description: filterInEditor?.description || '',
    ...filterInEditor,
  }));
  const [errors, setErrors] = useState<Partial<EntityFilter>>({});

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
  // const isSavedFilterWithUnsavedChanges = Boolean(draftFilterHasChanges && draftFilter?.updatedBy);

  const handleSave = useCallback(async () => {
    const errors: Record<string, string> = {};
    // lightweight validation for form
    if (!formValues.name) {
      errors.name = tn('required');
    } else if (formValues.name.length < 3) {
      errors.name = tn('name_min_length', { min_length: 3 });
    }

    if (Object.keys(errors).length) {
      setErrors(errors);
    } else {
      if ((filter.id || filterInEditor?.id) && (filter?.name || filterInEditor?.name)) {
        let updatedFilter = { ...(filter as EntityFilter), ...formValues };
        const result = await dispatch(updateEntityFilter(filter?.id ?? String(filterInEditor?.id), updatedFilter));
        if (result.success) {
          onRequestSaveFilter(updatedFilter);
          onRequestShowFilterPanel();
          onApplyFilter(updatedFilter);
        }
      } else {
        // satisfy TS
        const filterData = formValues as EntityFilter;
        const filterCriteria = filter?.criteria ?? (draftFilter?.criteria as EntityFilter['criteria']);

        dispatch(
          createEntityFilter(
            entityId,
            filterCriteria,
            filterData.name,
            filterData.description || '',
            filterData.tags || [],
            filterData.bookmarked
          )
        );
      }
    }
  }, [filterInEditor, draftFilter, formValues]);

  const applyFilter = (evt: React.FormEvent<HTMLFormElement>) => {
    evt.preventDefault();

    // Filter out invalid predicates (those without both left and operator)
    const filteredCriteria = draftFilter.criteria
      ? {
          ...draftFilter.criteria,
          predicates: processPredicates.filterValid(draftFilter.criteria.predicates),
        }
      : draftFilter.criteria;

    if (isSaveFilterChecked) {
      handleSave();
      // After saving, apply the filter with the retained ID
      const filterToApply = {
        ...draftFilter,
        criteria: filteredCriteria,
        entityId: draftFilter.syncariEntityId || entityId,
        id: filterInEditor?.id,
      };
      onApplyFilter(filterToApply);
    } else if (draftFilterHasChanges && !isSaveFilterChecked) {
      // create a new Ad Hoc filter with criteria
      // but none of the saved filter metadata. This prevents being given the
      // "Save Changes" option
      const adHocFilterToApply = {
        criteria: filteredCriteria,
        entityId: draftFilter.syncariEntityId,
      };
      onApplyFilter(adHocFilterToApply);
      setDraftFilter(adHocFilterToApply);
    } else {
      onRequestRefreshData();
      onRequestShowFilterPanel();
    }
  };

  const onFilterChange: FilterOnChangeHandler = (_, __, predicate: FilterValue) => {
    // if we've cleared the predicate and we started with a bookmarked filter, reset
    if ((!predicate || isPredicateEmpty(predicate)) && filter?.id) {
      //user has deleted all items from the Predicate
      onRequestResetFilter();
    } else {
      setDraftFilter((prev) => ({ ...prev, criteria: predicate }));
      setFormValues((prev) => ({
        ...prev,
        criteria: predicate,
        name: formValues.name,
        description: formValues.description,
      }));
    }
  };

  useToastForFetchStatusChange(deletingStatus, {
    error: tn('deleting_filter_failed', { name: filter?.name }),
    success: tn('deleting_filter_success', { name: filter?.name }),
  });

  useToastForFetchStatusChange(creatingStatus, {
    success: tn('filter_created_successfully', { name: formValues.name }),
    error: tn('filter_creation_failed', { name: formValues.name }),
  });

  useToastForFetchStatusChange(updatingStatus, {
    success: tn('filter_updated_successfully', { name: formValues.name }),
    error: tn('filter_update_failed', { name: formValues.name }),
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
    <div className="data-studio-filter-container">
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
                value={filterInEditor?.criteria || filter?.criteria || draftFilter?.criteria}
                fieldValues={fieldValues}
                isAllDisabled={currentMode === 'View'}
                singleCondition={currentMode === 'View'}
                placeHolder="Select field"
              />
            </div>
            <div>
              {error && (
                <HStack spacing="xs">
                  <Icon theme="twoTone" type="exclamation-circle" twoToneColor={colors.red500} />
                  <Tooltip title={error} placement="top">
                    <div className="data-studio-filter-error-text">
                      <Truncate lines={1}>{error}</Truncate>
                    </div>
                  </Tooltip>
                </HStack>
              )}
            </div>
            <div className="data-studio-filter-checkbox-wrapper">
              <Checkbox
                checked={isSaveFilterChecked}
                onChange={(e) => {
                  onSaveFilterCheckChange && onSaveFilterCheckChange(e.target.checked);
                }}
                disabled={currentMode === 'View' || filterCriteriaIsEmpty}>
                Save this filter for future
              </Checkbox>
              {isSaveFilterChecked && (
                <DataStudioSaveFilter
                  key={filterInEditor?.id || filter?.id || 'new'}
                  entityId={entityId}
                  filter={filterInEditor}
                  formValues={formValues}
                  errors={errors}
                  onChange={(values) => {
                    setFormValues((prev) => ({ ...prev, ...values }));
                    // Clear errors when user types
                    if (Object.keys(errors).length > 0) {
                      setErrors({});
                    }
                  }}
                  onSave={handleSave}
                  isDisabled={currentMode === 'View'}
                />
              )}
            </div>
            {currentMode !== 'View' && (
              <div className="data-studio-filter-toolbar-content">
                <HStack spacing="sm">
                  {isSaveFilterChecked ? (
                    <Button
                      disabled={!formValues.name?.length || formValues.name?.length < 3}
                      size="default"
                      type="primary"
                      onClick={handleSave}
                      aria-label={tn('run_filter')}>
                      Save and Apply filter
                    </Button>
                  ) : (
                    <Button
                      disabled={filterCriteriaIsEmpty}
                      htmlType="submit"
                      size="default"
                      type="primary"
                      aria-label={tn('run_filter')}>
                      {tn('run_filter')}
                    </Button>
                  )}
                </HStack>
              </div>
            )}
          </form>
        </div>
      </Stack>
    </div>
  );
};

export default DataStudioFilterPanel;
