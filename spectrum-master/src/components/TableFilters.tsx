import Button, { ButtonProps } from 'antd/lib/button';
import cx from 'classnames';
import * as React from 'react';
import { useContext, useEffect, useImperativeHandle, useMemo, useState } from 'react';
import { animated, useSpring } from 'react-spring';

import { ReactComponent as FilterIcon } from 'assets/icons/filter.svg';
import { HStack } from 'components/layout';
import SearchBox, { SearchBoxProps } from 'components/SearchBox';
import useDimensions, { ElementDimensions, EmptyElementDimensions } from 'hooks/useDimensions';
import { tNamespaced } from 'utils/i18nUtil';

import './TableFilters.less';

export interface TableFilterContextValue {
  filtersVisible: boolean;
  showFilters: () => void;
  hideFilters: () => void;
  toggleFilters: () => void;
  disclosureButtonDimensions: ElementDimensions;
  setDisclosureButtonDimensions: (dimensions: ElementDimensions) => void;
}

const TableFilterContext = React.createContext<TableFilterContextValue>({
  filtersVisible: false,
  showFilters: () => {},
  hideFilters: () => {},
  toggleFilters: () => {},
  disclosureButtonDimensions: EmptyElementDimensions,
  setDisclosureButtonDimensions: () => EmptyElementDimensions,
});

export const useTableFiltersContext = () => useContext(TableFilterContext);

export interface TableFilterProviderProps {
  children: React.ReactNode;
  initiallyVisible?: boolean;
}

export type TableFilterProviderRef = {
  setFiltersVisible: (visible: boolean) => void;
};

export const TableFilterProvider = React.forwardRef<TableFilterProviderRef, TableFilterProviderProps>(
  ({ children, initiallyVisible = false }, ref) => {
    const [filtersVisible, setFiltersVisible] = useState(initiallyVisible);
    const [disclosureButtonDimensions, setDisclosureButtonDimensions] = useState<ElementDimensions>(
      () => EmptyElementDimensions
    );

    useImperativeHandle(ref, () => ({
      setFiltersVisible,
    }));

    const value = useMemo(
      () => ({
        filtersVisible,
        showFilters: () => setFiltersVisible(true),
        hideFilters: () => setFiltersVisible(false),
        toggleFilters: () => setFiltersVisible((prev) => !prev),
        disclosureButtonDimensions,
        setDisclosureButtonDimensions,
      }),
      [disclosureButtonDimensions, filtersVisible]
    );

    return <TableFilterContext.Provider value={value}>{children}</TableFilterContext.Provider>;
  }
);

const tn = tNamespaced('TableFilters');

const TableSearchFilter = ({ placeholder = tn('search_placeholder'), className, ...props }: SearchBoxProps) => {
  return (
    <SearchBox
      allowClear
      className={cx('synri-table-filters-search', className)}
      placeholder={placeholder}
      {...props}
    />
  );
};

const TableFilterButton = ({ className, size, ...props }: ButtonProps) => {
  return <Button className={cx('filter-button', !size && 'match-search-size')} size={size} {...props} />;
};

interface TableFilterDisclosureButtonProps extends ButtonProps {
  activeFilterCount?: number;
  onRequestClear?: () => void;
  buttonGroupClassName?: string;
  className?: string;
  clearFiltersClassName?: string;
}

export type TableFilterDisclosureButtonRef = {
  node: ReturnType<typeof useDimensions>[2];
  remeasure: ReturnType<typeof useDimensions>[3];
  setDisclosureButtonDimensions: (dimensions: ElementDimensions) => void;
};

const TableFilterDisclosureButton = React.forwardRef<TableFilterDisclosureButtonRef, TableFilterDisclosureButtonProps>(
  (
    {
      activeFilterCount = 0,
      onRequestClear,
      buttonGroupClassName,
      className,
      clearFiltersClassName,
      size: buttonSize,
      ...buttonProps
    },
    ref
  ) => {
    const [measurementRef, dimensions, node, remeasure] = useDimensions({ liveMeasure: true });
    const { toggleFilters, hideFilters, setDisclosureButtonDimensions } = useTableFiltersContext();

    useEffect(() => {
      setDisclosureButtonDimensions(dimensions);
    }, [dimensions, setDisclosureButtonDimensions]);

    useImperativeHandle(ref, () => ({
      node,
      remeasure,
      setDisclosureButtonDimensions,
    }));

    const canClearFilters = activeFilterCount > 0;
    const buttonVariant = canClearFilters ? 'primary' : 'default';

    return (
      <div ref={measurementRef}>
        <Button.Group className={cx('filter-button-group', buttonGroupClassName)}>
          <TableFilterButton
            key="filter-btn"
            type={buttonVariant}
            onClick={toggleFilters}
            className={className}
            size={buttonSize}
            {...buttonProps}>
            <HStack spacing="xxs">
              <FilterIcon className="filter-button-icon" />
              <span>{activeFilterCount > 0 ? tn('filters', { count: activeFilterCount }) : tn('filter')}</span>
            </HStack>
          </TableFilterButton>
          {canClearFilters && (
            <TableFilterButton
              key="clear-filter-btn"
              type={buttonVariant}
              icon="close"
              className={cx('filter-clear-button', clearFiltersClassName)}
              onClick={() => {
                hideFilters();
                onRequestClear?.();
              }}
              size={buttonSize}
            />
          )}
        </Button.Group>
      </div>
    );
  }
);

const filtersHiddenStyle = {
  maxHeight: 0,
  opacity: 0,
};
const filtersShowingStyle = {
  maxHeight: 600,
  opacity: 1,
};

interface TableFiltersContainerProps {
  arrowClassName?: string;
  className?: string;
  children: React.ReactNode;
  hideArrow?: boolean;
  wrapperClassName?: string;
}

const TableFiltersContainer = ({
  arrowClassName,
  className,
  wrapperClassName,
  children,
  hideArrow,
}: TableFiltersContainerProps) => {
  const { filtersVisible, disclosureButtonDimensions } = useTableFiltersContext();

  // track the size of our filters container so we can calculate our offset for the btn arrow
  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });
  const arrowStyle = useMemo(() => {
    if (dimensions?.left && disclosureButtonDimensions?.left && disclosureButtonDimensions?.width && !hideArrow) {
      // try to center on the Filter Button
      return {
        left: disclosureButtonDimensions.left - dimensions.left + disclosureButtonDimensions.width / 2,
      };
    }

    return { display: 'none' };
  }, [dimensions?.left, disclosureButtonDimensions?.left, disclosureButtonDimensions?.width, hideArrow]);

  const spring = useSpring(filtersVisible ? filtersShowingStyle : filtersHiddenStyle);

  // -1rem margin on top because we've put this on our animated.div wrapper.
  // we need the padding on the animated container because of it's overflowY: hidden
  // setting
  return (
    <div ref={measurementRef} className={wrapperClassName}>
      <animated.div style={{ overflow: 'hidden', marginTop: '-1rem', paddingTop: '1rem', ...spring }}>
        <div className={cx('filters-container', className)}>
          {!hideArrow && <div className={cx('filters-disclosure-arrow', arrowClassName)} style={arrowStyle} />}
          {children}
        </div>
      </animated.div>
    </div>
  );
};

export { TableFilterButton, TableFilterDisclosureButton, TableFiltersContainer, TableSearchFilter };
