import { Tooltip } from 'antd';
import Button, { ButtonProps } from 'antd/lib/button';
import cx from 'classnames';
import * as React from 'react';

import { HStack, Stack } from 'components/layout';
import SearchBox, { SearchBoxProps } from 'components/SearchBox';
import { TableFilterDisclosureButton, TableFilterProvider, TableFiltersContainer } from 'components/TableFilters';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { tCommon, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import './TableFilters.less';

const tFieldsTable = tNamespaced('SchemaStudio.FieldsTable');

const TableSearchFilter = ({ placeholder = 'Search…', ...props }: SearchBoxProps) => {
  return <SearchBox allowClear className="filter-search-input" placeholder={placeholder} {...props} />;
};

interface TableFilterProps {
  title: string;
  children?: React.ReactNode;
}

const TableFilter = ({ title, children }: TableFilterProps) => {
  return (
    <div className="schema-studio-table-filter">
      <div className="schema-studio-table-filter-name">{title}</div>
      {children}
    </div>
  );
};

interface TableFiltersProps {
  activeFilterCount?: number;
  searchInputValue: string;
  onSearchInputChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onRequestClearFilters?: () => void;
  onRequestShowFilters?: () => void;
  isShowingFilters?: boolean;
  renderActions?: React.ReactElement;
  children?: React.ReactNode;
}

const TableFilters = ({
  searchInputValue,
  onSearchInputChange,
  isShowingFilters = false,
  activeFilterCount = 0,
  onRequestClearFilters,
  onRequestShowFilters,
  renderActions,
  children,
}: TableFiltersProps) => {
  return (
    <TableFilterProvider>
      <Stack>
        <div className="filters-toolbar">
          <HStack className="filters-toolbar-primary-items">
            <TableSearchFilter onChange={onSearchInputChange} value={searchInputValue} />
            <TableFilterDisclosureButton onRequestClear={onRequestClearFilters} activeFilterCount={activeFilterCount} />
          </HStack>
          {renderActions && <HStack>{renderActions}</HStack>}
        </div>
        <TableFiltersContainer>
          <HStack>{children}</HStack>
        </TableFiltersContainer>
      </Stack>
    </TableFilterProvider>
  );
};

export interface TableFilterButtonProps extends ButtonProps {
  permission?: AllPermissions | AllPermissions[];
  isReadonly?: boolean;
}

const TableFilterButton = ({ className, permission, isReadonly, ...props }: TableFilterButtonProps) => {
  const { userHasPermission } = useUserHasPermission();
  let buttonTooltip: string = '';

  if (isReadonly) {
    buttonTooltip = tFieldsTable('cannot_be_edited');
  }

  if (permission && !userHasPermission(permission)) {
    buttonTooltip = tCommon('permission_error');
  }

  return (
    <Tooltip title={buttonTooltip}>
      <Button className={cx('filter-button', className)} disabled={isReadonly} {...props} />
    </Tooltip>
  );
};

export default TableFilters;
export { TableFilter, TableFilterButton, TableFilterDisclosureButton, TableSearchFilter };
