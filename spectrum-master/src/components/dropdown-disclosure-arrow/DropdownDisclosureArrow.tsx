import cx from 'classnames';

import { ReactComponent as ChevronDown } from 'assets/icons/chevron-down.svg';
import { tc } from 'utils/i18nUtil';

import './DropdownDisclosureArrow.less';

interface DropdownDisclosureArrowProps {
  isOpen: boolean;
  closeMargin?: boolean;
}

export const DropdownDisclosureArrow = ({ isOpen, closeMargin = false }: DropdownDisclosureArrowProps) => {
  return (
    <div
      aria-label={tc('expand_items')}
      className={cx('dropdown-disclosure-arrow', {
        'dropdown-disclosure-arrow--open': isOpen,
        'dropdown-disclosure-arrow--close-margin': closeMargin,
      })}
      data-testid="dropdown-disclosure-arrow">
      <ChevronDown />
    </div>
  );
};
