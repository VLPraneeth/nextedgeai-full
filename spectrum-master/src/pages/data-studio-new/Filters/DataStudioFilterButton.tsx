import { Button, Dropdown, Icon } from 'antd';
import { ReactComponent as FilterIcon } from 'assets/icons/filter-light.svg';
import './Filters.less';
import { HStack } from 'components/layout';
import { TableFilterButton } from 'components/TableFilters';
import { cx } from '@emotion/css';
import { deleteEntityFilter, EntityFilter } from 'store/data-studio';
import KebabMenu from 'components/KebabMenu';
import { useI18nContext } from 'components/I18nProvider';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import Modal from 'components/Modal';
import { useCallback, useMemo, useState } from 'react';
import { UnreachableCaseError } from 'utils/TypeUtils';
import { TranslatedText } from 'components/typography';
import Menu from 'antd/lib/menu';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { selectFilterDeletingStatus } from 'store/data-studio/selectors';

enum FilterAction {
  DELETE = 'Delete',
  EDIT = 'Edit',
  VIEW = 'View',
}

export interface DataStudioFilterButtonProps {
  activeFilterCount?: number;
  savedFilters?: EntityFilter[];
  onSelectFilter?: (filter: Partial<EntityFilter>) => void;
  onCreateNewFilter?: () => void;
  onClearFilters?: () => void;
  onEditFilter: (filter: EntityFilter, view: boolean) => void;
  isFilterApplied: boolean;
  currentAppliedFilter?: EntityFilter | Partial<EntityFilter> | undefined;
}

// 1. Update the FilterLineItemProps interface
interface FilterLineItemProps {
  filter: EntityFilter;
  onAction: () => void;
  onSelectFilter?: (filter: Partial<EntityFilter>) => void;
  onEditFilter: (filter: EntityFilter, view: boolean) => void;
}

// Extract FilterLineItem as a separate component to avoid hooks violation
const FilterLineItem: React.FC<FilterLineItemProps> = ({ filter, onAction, onSelectFilter, onEditFilter }) => {
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const deletingStatus = useEnhancedSelector((state) => selectFilterDeletingStatus(state, filter.id));

  useToastForFetchStatusChange(deletingStatus, {
    success: tn('filter_deleted_successfully', { name: filter.name }),
    error: tn('filter_deletion_failed', { name: filter.name }),
  });

  const onFilterDelete = useCallback(() => {
    onAction();
    Modal.confirm({
      title: tn('delete_filter_title'),
      content: tn('delete_filter_content', { name: filter.name }),
      onOk: () => dispatch(deleteEntityFilter(filter.id)),
      okText: tn('delete_filter_confirm_button'),
      okType: 'danger',
    });
  }, [dispatch, filter, tn, onAction]);

  const handleAction = useCallback(
    (key: FilterAction) => {
      onAction();
      switch (key) {
        case FilterAction.VIEW:
          onEditFilter?.(filter, true);
          break;
        case FilterAction.EDIT:
          onEditFilter?.(filter, false);
          break;
        case FilterAction.DELETE:
          onFilterDelete();
          break;
        default:
          throw new UnreachableCaseError(key);
      }
    },
    [filter, onAction, onFilterDelete, onEditFilter]
  );

  return (
    <HStack key={filter.id} className="filter-line-item">
      <Button
        type="link"
        onClick={() => {
          onAction();
          onSelectFilter?.(filter);
        }}>
        {filter.name}
      </Button>
      <HStack className="filter-line-item-actions">
        <KebabMenu<FilterAction>
          onClick={({ key }) => handleAction(key as FilterAction)}
          menuItems={[
            <Menu.Item key={FilterAction.VIEW}>
              <TranslatedText text="view_filter" />
            </Menu.Item>,
            <Menu.Item key={FilterAction.EDIT}>
              <TranslatedText text="edit_filter" />
            </Menu.Item>,
            <Menu.Item key={FilterAction.DELETE}>
              <TranslatedText text="delete_filter" />
            </Menu.Item>,
          ]}
        />
      </HStack>
    </HStack>
  );
};

const DataStudioFilterButton: React.FC<DataStudioFilterButtonProps> = ({
  activeFilterCount = 0,
  savedFilters = [],
  onSelectFilter,
  onCreateNewFilter,
  onEditFilter,
  onClearFilters,
  isFilterApplied,
  currentAppliedFilter,
}) => {
  const [dropdownVisible, setDropdownVisible] = useState(false);
  const hasActiveFilters = activeFilterCount > 0;
  const hasSavedFilters = savedFilters.length > 0;
  const isEditFilterMode = useMemo(() => {
    if (!currentAppliedFilter || !currentAppliedFilter.id) {
      return false;
    }
    return savedFilters.some((savedFilter) => savedFilter.id === currentAppliedFilter.id);
  }, [currentAppliedFilter, savedFilters]);

  const handleClearFilters = useCallback(() => {
    onClearFilters?.();
    setDropdownVisible(false);
  }, [onClearFilters]);

  const menu = (
    <Menu className="filter-dropdown-menu">
      {hasSavedFilters && (
        <Menu.ItemGroup title="Saved Filters" className="filter-dropdown-group">
          {savedFilters.length > 0 ? (
            savedFilters.map((filter) => (
              <FilterLineItem
                key={filter.id}
                filter={filter}
                onAction={() => setDropdownVisible(false)}
                onSelectFilter={onSelectFilter}
                onEditFilter={onEditFilter}
              />
            ))
          ) : (
            <HStack justify="center">
              <TranslatedText text="no_filters_found" />
            </HStack>
          )}
        </Menu.ItemGroup>
      )}

      <Menu.Divider className="filter-dropdown-divider" />

      <Menu.Item
        key="create-new"
        onClick={() => {
          setDropdownVisible(false);
          onCreateNewFilter?.();
        }}
        disabled={isFilterApplied}
        className="filter-dropdown-new">
        Create a New Filter
        <Icon type="caret-right" />
      </Menu.Item>
    </Menu>
  );

  const getEditableFilter = useCallback(
    (
      currentAppliedFilter?: EntityFilter | Partial<EntityFilter>,
      savedFilters: EntityFilter[] = []
    ): EntityFilter | undefined => {
      if (!currentAppliedFilter) return undefined;

      const matchedSavedFilter = savedFilters.find((f) => f.id === (currentAppliedFilter as EntityFilter)?.id);

      return matchedSavedFilter || (currentAppliedFilter as EntityFilter);
    },
    [savedFilters]
  );

  return (
    <>
      <Dropdown
        overlay={menu}
        trigger={['click']}
        overlayClassName="filter-dropdown-overlay"
        placement="bottomLeft"
        visible={dropdownVisible}
        onVisibleChange={() => {
          if (!savedFilters.length) {
            onCreateNewFilter?.();
          } else {
            if (isFilterApplied) return;
            setDropdownVisible(!dropdownVisible);
          }
        }}>
        <TableFilterButton
          key="filter-btn"
          type="default"
          className="filter-dropdown-button"
          size="default"
          onClick={(e) => {
            if (isFilterApplied) {
              e.stopPropagation();
              const editableFilter = getEditableFilter(currentAppliedFilter, savedFilters);
              if (editableFilter && editableFilter.name) {
                onEditFilter?.(editableFilter, false);
              } else {
                onCreateNewFilter?.();
              }
            }
          }}>
          <HStack spacing="xs">
            <FilterIcon className="filter-btn-icon" />
            <span className="filter-button-text">
              {isEditFilterMode ? `Edit Filter${activeFilterCount > 1 ? 's' : ''} (${activeFilterCount})` : 'Filter'}
            </span>
            {hasActiveFilters && (
              <Button
                shape="circle"
                key="clear-filter-btn"
                type="default"
                icon="close"
                className={cx('ds-filter-clear-button')}
                onClick={handleClearFilters}
                size="small"
              />
            )}
          </HStack>
        </TableFilterButton>
      </Dropdown>
    </>
  );
};

export default DataStudioFilterButton;
