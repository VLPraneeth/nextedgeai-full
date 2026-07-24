import { filter, includes } from 'lodash';

import { FilteredDropdownItemProps } from './FilteredDropdownItem';

// Filter items based on both the title and subtext
export const filterDropdownItems = (items: FilteredDropdownItemProps[], filterText?: string) => {
  const sortedItems = items.sort((a, b) => a.title?.localeCompare(b.title));

  if (!filterText) {
    return sortedItems;
  }

  return filter(
    sortedItems,
    ({ title, subtext }) =>
      includes(title?.toLowerCase(), filterText.toLowerCase()) ||
      includes(subtext?.toLowerCase(), filterText.toLowerCase())
  );
};
