import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { IHeaderParams } from 'ag-grid-community';
import Icon from 'antd/lib/icon';
import Input from 'antd/lib/input';
import { ReactComponent as QuickFilterIcon } from 'assets/icons/quick-filter.svg';
import { ReactComponent as ArrowDownIcon } from 'assets/icons/arrow-down.svg';
import { ReactComponent as ArrowUpIcon } from 'assets/icons/arrow-up.svg';
import { ReactComponent as HideEyeIcon } from 'assets/icons/hide-eye-icon.svg';
import Button, { IconButton } from 'components/Button';
import SelectInput from 'components/SelectInput';
import Filter from './Filters';
import { FilterValue, LeftValue } from 'components/inputs/types';
import { EntityFilter, FieldMetadata } from 'store/data-studio/types';
import { getPicklistValues } from 'actions/picklistActions';
import { useEnhancedSelector } from 'hooks/redux';
import { usePicklistValues } from 'store/picklists/hooks';
import { cloneDeep } from 'lodash';
import { FilterRef } from 'components/inputs/filter';
import DataStudioFilter from './Filters';
import { processPredicates } from './utils';

interface FilterOption {
  label: string;
  value: string;
}

interface CustomHeaderGridProps extends IHeaderParams {
  entityId: string;
  showColumnVisibilityToggle?: boolean;
  fieldMetadata?: FieldMetadata;
  fieldValues?: LeftValue[];
  appliedFilter?: EntityFilter | Partial<EntityFilter>;
  onApplyFilter?: (filter: Partial<EntityFilter> | undefined) => void;
  onColumnVisibilityChange?: (columnName: string, isVisible: boolean) => void;
  error?: string;
  onSortChange?: (orderBy: string, sortDirection: 'asc' | 'desc') => void;
}

const CustomHeaderGrid = (props: CustomHeaderGridProps) => {
  const {
    showColumnVisibilityToggle = true,
    entityId,
    fieldValues = [],
    appliedFilter,
    onApplyFilter,
    onColumnVisibilityChange,
    error,
    onSortChange,
  } = props;

  const [isOpen, setIsOpen] = useState(false);
  const [filterOptions, setFilterOptions] = useState<FilterOption[]>([]);
  const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
  const [isPositioned, setIsPositioned] = useState(false);
  const [isColumnVisible, setIsColumnVisible] = useState(true);
  const [draftFilter, setDraftFilter] = useState<Partial<EntityFilter>>(cloneDeep(appliedFilter || {}));
  const [clipPath, setClipPath] = useState<string>('none');
  const headerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const filterControlRef = useRef<FilterRef | null>(null);
  const isApplyFilterButtonDisabled = !draftFilter?.criteria?.predicates?.length;

  // Get picklist values from Redux state
  const colId = props.column.getColId();
  const reduxPicklistValues = useEnhancedSelector((state) => state.picklist[colId]);
  const [picklistValues, fetchPicklistValues] = usePicklistValues();

  // Update filter options when picklist values are received from Redux
  useEffect(() => {
    if (reduxPicklistValues && Array.isArray(reduxPicklistValues)) {
      const newFilterOptions: FilterOption[] = reduxPicklistValues.map(
        (option: { label: any; value: any; id: any }) => ({
          label: option.label,
          value: option.value || option.id || '',
        })
      );
      setFilterOptions(newFilterOptions);
    }
  }, [reduxPicklistValues]);

  // Pre-populate filter with current column field
  const currentField = useMemo(() => {
    return fieldValues.find((field) => field.value === props.fieldMetadata?.fieldId);
  }, [fieldValues, props.fieldMetadata?.fieldId]);

  // Check if the current field's filter should be disabled
  const isFilterDisabled = useMemo(() => {
    if (!currentField || !currentField.value) return false;
    const disabledFieldIds = processPredicates.getDuplicates(appliedFilter?.criteria?.predicates || []);
    return disabledFieldIds.includes(currentField.value);
  }, [currentField, appliedFilter, colId]);

  // Extract only the predicates for the current column from the applied filter
  const currentColumnFilter = useMemo(() => {
    if (!appliedFilter?.criteria?.predicates || !currentField?.value) {
      return null;
    }

    // Recursively extract all predicates that match the current column's field
    const columnPredicates = processPredicates.extract(appliedFilter.criteria.predicates, currentField.value);

    if (columnPredicates.length === 0) {
      return null;
    }

    return {
      predicates: columnPredicates,
      operator: appliedFilter.criteria.operator || 'AND',
      groupPredicateId: appliedFilter.criteria.groupPredicateId || `${colId}-group`,
    };
  }, [appliedFilter, currentField, colId]);

  // Reset draft filter when the field becomes disabled (has duplicate conditions)
  useEffect(() => {
    if (isFilterDisabled && currentField) {
      // Reset to empty state when disabled
      setDraftFilter({
        criteria: {
          predicates: [
            {
              left: currentField,
              operator: undefined,
              right: undefined,
              predicateId: colId,
            },
          ],
          operator: 'AND',
          groupPredicateId: `${colId}-group`,
        },
      });
    }
  }, [isFilterDisabled, currentField, colId]);

  // Update draft filter when applied filter changes or when opening with current field
  useEffect(() => {
    if (isOpen && currentField && !isFilterDisabled) {
      if (currentColumnFilter) {
        // Show only the filters for this column
        setDraftFilter({
          criteria: currentColumnFilter,
        });
      } else {
        // No filter for this column yet, pre-populate with empty filter for current field
        setDraftFilter({
          criteria: {
            predicates: [
              {
                left: currentField,
                operator: undefined,
                right: undefined,
                predicateId: colId,
              },
            ],
            operator: 'AND',
            groupPredicateId: `${colId}-group`,
          },
        });
      }
    }
  }, [isOpen, currentColumnFilter, currentField, colId, isFilterDisabled]);

  // Update dropdown position when opened or when scrolling
  const updateDropdownPosition = useCallback(() => {
    if (headerRef.current) {
      const rect = headerRef.current.getBoundingClientRect();
      const agGridContainer = document.querySelector('.ag-root-wrapper');

      // Calculate default left position
      let leftPosition = rect.left + window.scrollX;

      // Check if dropdown would overflow on the right
      if (agGridContainer && dropdownRef.current) {
        const containerRect = agGridContainer.getBoundingClientRect();
        const dropdownTop = rect.bottom;
        const dropdownLeft = rect.left;

        const dropdownWidth = dropdownRef.current.offsetWidth || 272; // Fallback to min-width from CSS
        const dropdownHeight = dropdownRef.current.offsetHeight || 500;
        const dropdownRight = rect.left + dropdownWidth;

        const clipTop = Math.max(0, containerRect.top - dropdownTop);
        let clipLeft = Math.max(0, containerRect.left - dropdownLeft);
        let clipRight = Math.max(0, dropdownLeft + dropdownWidth - containerRect.right);
        const clipBottom = Math.max(0, dropdownTop + dropdownHeight - containerRect.bottom);

        // If dropdown would overflow the container's right edge, shift it 100px to the left
        if (dropdownRight > containerRect.right) {
          leftPosition = rect.left + window.scrollX - 80;

          // Ensure dropdown doesn't go off-screen to the left
          const minLeft = containerRect.left + window.scrollX;
          if (leftPosition < minLeft) {
            leftPosition = minLeft;
          }

          // Recalculate clip values based on the new shifted position
          const shiftedDropdownLeft = leftPosition - window.scrollX; // Convert back to viewport coords
          const shiftedDropdownRight = shiftedDropdownLeft + dropdownWidth;

          // Calculate clipping based on shifted position
          clipLeft = Math.max(0, containerRect.left - shiftedDropdownLeft);
          clipRight = Math.max(0, shiftedDropdownRight - containerRect.right);
        }

        // Apply clip-path if dropdown would overflow the container
        if (clipLeft > 0 || clipRight > 0 || clipTop > 0 || clipBottom > 0) {
          // Create inset clip-path: inset(top right bottom left)
          setClipPath(`inset(${clipTop}px ${clipRight}px ${clipBottom}px ${clipLeft}px)`);
        } else {
          setClipPath('none');
        }
      }

      setDropdownPosition({
        top: rect.bottom + window.scrollY,
        left: leftPosition,
      });

      setIsPositioned(true);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      updateDropdownPosition();

      // Update position on scroll - dropdown will be clipped by viewport
      const handleScroll = () => {
        updateDropdownPosition();
      };

      // Listen to window scroll
      window.addEventListener('scroll', handleScroll, true);

      // Find and listen to ag-grid container scroll
      const agGridViewport = document.querySelector('.ag-body-horizontal-scroll-viewport');
      const agGridBody = document.querySelector('.ag-body-viewport');

      if (agGridViewport) {
        agGridViewport.addEventListener('scroll', handleScroll);
      }
      if (agGridBody) {
        agGridBody.addEventListener('scroll', handleScroll);
      }

      return () => {
        window.removeEventListener('scroll', handleScroll, true);
        if (agGridViewport) {
          agGridViewport.removeEventListener('scroll', handleScroll);
        }
        if (agGridBody) {
          agGridBody.removeEventListener('scroll', handleScroll);
        }
      };
    } else {
      setIsPositioned(false);
      setClipPath('none');
    }
  }, [isOpen, updateDropdownPosition]);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: any) => {
      // Check if click is on SelectInput dropdown menu (which renders in a portal)
      const isSelectDropdown = event.target.closest('.ant-select-dropdown');

      if (
        headerRef.current &&
        !headerRef.current.contains(event.target) &&
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target) &&
        !isSelectDropdown
      ) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleSort = (order: 'asc' | 'desc') => {
    const colId = props.column.getColId();
    if (onSortChange) {
      onSortChange(colId, order);
    }
  };

  const toggleColumnVisibility = () => {
    const newVisibility = !isColumnVisible;
    const colId = props.column.getColId();

    // Hide/show in ag-grid
    props.columnApi?.setColumnVisible(colId, newVisibility);
    setIsColumnVisible(newVisibility);

    // Update column configuration so it persists
    if (onColumnVisibilityChange) {
      onColumnVisibilityChange(colId, newVisibility);
    }

    // Close the dropdown after hiding the column
    setIsOpen(false);
  };

  const handleHeaderClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsOpen(!isOpen);
  };

  const onFilterChange = useCallback((_name: string, _key: string, predicate: FilterValue) => {
    setDraftFilter((prev) => ({ ...prev, criteria: predicate }));
  }, []);

  const handleApplyFilter = useCallback(
    (e: React.MouseEvent<HTMLButtonElement>) => {
      e.stopPropagation();
      if (!onApplyFilter || !draftFilter?.criteria || !currentField?.value) return;

      const validPredicates = processPredicates.filterValid(draftFilter.criteria.predicates);
      const updatedPredicates = processPredicates.updateInPlace(
        appliedFilter?.criteria?.predicates || [],
        currentField.value,
        validPredicates
      );

      if (updatedPredicates.length === 0) {
        onApplyFilter(undefined);
      } else {
        onApplyFilter({
          id: appliedFilter?.id,
          name: appliedFilter?.name,
          criteria: {
            predicates: updatedPredicates,
            operator: appliedFilter?.criteria?.operator || 'AND',
            groupPredicateId: appliedFilter?.criteria?.groupPredicateId || 'root-filter',
          },
        });
      }
      setIsOpen(false);
    },
    [onApplyFilter, draftFilter, currentField, appliedFilter]
  );

  return (
    <>
      <div className="custom-header" ref={headerRef}>
        <div className="custom-header-label" onClick={handleHeaderClick}>
          <span>{props.displayName}</span>
          <QuickFilterIcon style={currentColumnFilter ? { color: '#1890ff' } : {}} />
        </div>
      </div>

      {isOpen &&
        createPortal(
          <div
            ref={dropdownRef}
            className="data-studio-grid-custom-sort-container custom-header-dropdown"
            style={{
              position: 'absolute',
              top: `${dropdownPosition.top}px`,
              left: `${dropdownPosition.left}px`,
              opacity: isPositioned ? 1 : 0,
              visibility: isPositioned ? 'visible' : 'hidden',
              maxHeight: '500px',
              overflowY: 'auto',
              clipPath: clipPath,
            }}>
            <div className="sort-buttons">
              <IconButton
                className="sort-btn sort-icon-btn"
                icon={ArrowUpIcon}
                onClick={(e) => {
                  e.stopPropagation();
                  handleSort('asc');
                }}
                title="Sort Ascending"
              />
              <IconButton
                className="sort-btn sort-icon-btn"
                icon={ArrowDownIcon}
                onClick={(e) => {
                  e.stopPropagation();
                  handleSort('desc');
                }}
                title="Sort Descending"
              />
              {showColumnVisibilityToggle && (
                <IconButton
                  className="sort-btn sort-icon-btn eye-btn"
                  icon={HideEyeIcon}
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleColumnVisibility();
                  }}
                  title="Hide column"
                />
              )}
            </div>
            {fieldValues.length > 0 && (
              <div className="filter-component-container" onClick={(e) => e.stopPropagation()}>
                <DataStudioFilter
                  ref={filterControlRef}
                  key={`${entityId}:${colId}`}
                  className="column-filter"
                  onChange={onFilterChange}
                  name={colId}
                  picklistValues={picklistValues}
                  fetchPicklistValues={fetchPicklistValues}
                  value={draftFilter.criteria}
                  fieldValues={fieldValues}
                  hideLeftInput={true}
                  verticalLayout={true}
                  singleCondition={true}
                  isAllDisabled={isFilterDisabled}
                  placeHolder="Select"
                />
                {isFilterDisabled && (
                  <div className="quick-filter-disabled-message">
                    <Icon type="exclamation-circle" style={{ marginRight: '8px' }} />
                    Multiple conditions already applied
                  </div>
                )}
              </div>
            )}
            {error && (
              <div className="filter-error" style={{ color: 'red', padding: '8px' }}>
                {error}
              </div>
            )}
            {!isFilterDisabled && (
              <div className="apply-filter-container">
                <Button
                  type="primary"
                  className="apply-filter-btn"
                  onClick={handleApplyFilter}
                  disabled={isApplyFilterButtonDisabled}>
                  Apply Filter
                </Button>
              </div>
            )}
          </div>,
          document.body
        )}
    </>
  );
};

export default CustomHeaderGrid;
