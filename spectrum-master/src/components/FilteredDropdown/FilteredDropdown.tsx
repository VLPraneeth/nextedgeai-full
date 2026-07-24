import { Empty } from 'antd';
import Search from 'antd/lib/input/Search';
import { map } from 'lodash';
import { useEffect, useRef, useState } from 'react';

import { useFocusRef } from 'hooks/useFocusRef';
import AppConstants from 'utils/AppConstants';

import { filterDropdownItems } from './FilteredDropdown.utils';
import FilteredDropdownItem, { FilteredDropdownItemProps } from './FilteredDropdownItem';

const FILTER_HEIGHT_SCROLL_OFFSET = 40;
interface Props {
  items: FilteredDropdownItemProps[];
  open: boolean;
  closePopover?: () => void;
}

const FilteredDropdown = ({ items, open, closePopover }: Props) => {
  const [filterText, setFilter] = useState('');
  const filteredItems = filterDropdownItems(items, filterText);

  const searchRef = useFocusRef();

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const activeElementRef = useRef<HTMLAnchorElement>(null);

  // Scroll to the selected item when the menu is opened
  useEffect(() => {
    if (open) {
      const scrollPosition = activeElementRef.current?.offsetTop;
      if (scrollPosition) {
        scrollContainerRef.current?.scrollTo(0, scrollPosition - FILTER_HEIGHT_SCROLL_OFFSET);
      }
    }
  }, [open]);
  const activeItemProps = { ref: activeElementRef };

  useEffect(() => {
    if (open) {
      setFilter('');
    }
  }, [open]);

  useEffect(() => {
    if (open) {
      searchRef.element.current?.focus();
    }
  }, [open, searchRef]);

  return (
    <div>
      <Search
        ref={searchRef.refCallback as any}
        value={filterText}
        onKeyDown={(e) => {
          if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.escape) {
            closePopover && closePopover();
          }
        }}
        placeholder="Filter…"
        onChange={(e) => setFilter(e.target.value)}
      />
      <div className="synri-popover-breadcrumb-items-container" ref={scrollContainerRef}>
        {filteredItems.length > 0 ? (
          map(filteredItems, (item) => (
            <FilteredDropdownItem
              key={item.id}
              closePopover={closePopover}
              {...item}
              {...(item.selected ? activeItemProps : null)}
            />
          ))
        ) : (
          <Empty description="No results" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </div>
    </div>
  );
};

export default FilteredDropdown;
