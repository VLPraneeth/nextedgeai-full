import { Link } from '@reach/router';
import cx from 'classnames';
import { forwardRef } from 'react';

import AppConstants from 'utils/AppConstants';

import './FilteredDropdownItem.less';

export interface FilteredDropdownItemProps {
  id: string;
  title: string;
  onClick: (evt?: React.MouseEvent) => void;
  closePopover?: () => void;
  subtext?: string;
  selected?: boolean;
  to?: string;
}

const FilteredDropdownItem = forwardRef<HTMLAnchorElement, FilteredDropdownItemProps>(
  ({ title, onClick, subtext, to = '', selected = false, closePopover }, ref) => (
    <Link
      ref={ref}
      to={to}
      role="button"
      aria-label={title}
      tabIndex={0}
      onClick={(e) => {
        onClick(e);
        closePopover?.();
      }}
      onKeyDown={(e) => {
        if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.enter) {
          onClick();
          closePopover?.();
        }
      }}
      className={cx('synri-filtered-dropdown-item', {
        'is-selected': selected,
      })}>
      <span className="item-title">{title}</span>
      {subtext && <span className="item-subtext">{subtext}</span>}
    </Link>
  )
);

export default FilteredDropdownItem;
