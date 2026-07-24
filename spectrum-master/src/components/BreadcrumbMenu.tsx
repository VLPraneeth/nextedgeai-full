import { Popover } from 'antd';
import { useState } from 'react';

import './BreadcrumbMenu.less';
import FilteredDropdownItems from './FilteredDropdown/FilteredDropdown';
import { FilteredDropdownItemProps } from './FilteredDropdown/FilteredDropdownItem';

export interface Props {
  items?: FilteredDropdownItemProps[];
  children: JSX.Element;
  className?: string;
}

// BreadcrumbMenu supports showing a list of sibling navigation items displayed
// in a dropdown menu on hover.
const BreadcrumbMenu = ({ children, items, className }: Props) => {
  const [popoverOpen, setPopoverOpen] = useState(false);
  const closePopover = () => setPopoverOpen(false);

  if (!items) {
    return children;
  }

  return (
    <Popover
      visible={popoverOpen}
      className={className}
      placement="bottomLeft"
      onVisibleChange={setPopoverOpen}
      overlayClassName="synri-popover-breadcrumb-container"
      content={<FilteredDropdownItems open={popoverOpen} items={items} closePopover={closePopover} />}>
      {children}
    </Popover>
  );
};

export default BreadcrumbMenu;
