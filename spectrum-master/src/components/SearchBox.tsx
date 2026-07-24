import { Empty, Input, Menu, Spin } from 'antd/lib';
import { SearchProps } from 'antd/lib/input';
import cx from 'classnames';
import { useRef, useState } from 'react';
import { animated, useTransition } from 'react-spring';

import useEffectOnValueChange from 'hooks/useEffectOnValueChange';
import useEventListener from 'hooks/useEventListener';
import useOnClickOutside from 'hooks/useOnClickOutside';
import AppConstants from 'utils/AppConstants';

import './SearchBox.less';

const { Search } = Input;

export type SearchBoxProps = SearchProps;

const SearchBox = ({ className, size, ...moreProps }: SearchBoxProps) => (
  <Search
    className={cx('synri-search-box-input', size && `synri-search-box-input-${size}`, className)}
    size={size}
    autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
    {...moreProps}
  />
);

const MAX_COLLAPSED_WIDTH = 1400;

export type AutoCompleteSearchProps = SearchProps & {
  className?: string;
  size?: number;
  onSearch: () => void;
  dataSource?: (false | JSX.Element)[];
  openDropdown: () => void;
  closeDropdown: () => void;
  loading: boolean;
  noDataMessage?: string;
  dropdownOpen?: boolean;
};

export const AutoCompleteSearchBox = ({
  className,
  size,
  dropdownOpen,
  openDropdown,
  closeDropdown,
  onSearch,
  dataSource = [],
  loading,
  noDataMessage,
  ...props
}: AutoCompleteSearchProps) => {
  const ref = useRef(null);

  useOnClickOutside(ref, () => closeDropdown());

  const inputRef = useRef<any>(null);
  const startCollapsed = window.innerWidth <= MAX_COLLAPSED_WIDTH;
  const [collapseSearch, setCollapseSearch] = useState(startCollapsed);

  // If the dropdown closes, also collapse the search bar if the screen is small
  useEffectOnValueChange(() => {
    if (!dropdownOpen && startCollapsed && !collapseSearch) {
      setCollapseSearch(startCollapsed);
    }
  }, [dropdownOpen]);

  // When the window resizes, set the search dropdown
  useEventListener('resize', () => {
    const newCollapsed = window.innerWidth <= MAX_COLLAPSED_WIDTH;
    if (newCollapsed !== collapseSearch) {
      setCollapseSearch(newCollapsed);
      if (newCollapsed) {
        closeDropdown();
      }
    }
  });

  const transitions = useTransition(dropdownOpen, {
    config: { duration: 100 },
    from: { maxHeight: 0 },
    enter: { maxHeight: 300 },
    leave: { maxHeight: 0 },
    reverse: !dropdownOpen,
    delay: 100,
  });

  return (
    <div
      ref={ref}
      className={cx('synri-search-main-header', collapseSearch && 'collapse-search')}
      onClick={() => {
        // Open the collapsed search when clicked
        setCollapseSearch(false);
        setTimeout(() => inputRef.current.input.focus());
      }}>
      <Search
        ref={inputRef}
        onFocus={openDropdown}
        className={cx('synri-search-main-header__input', collapseSearch && 'collapse-search')}
        onKeyDown={(e) => {
          if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.enter) {
            onSearch();
          }
        }}
        size={size}
        autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
        {...props}
      />
      {transitions(
        (props, item) =>
          item && (
            <animated.div style={props} className="synri-search-main-header__items-container">
              {loading ? (
                <div className="synri-search-main-header__loader">
                  <Spin spinning />
                </div>
              ) : dataSource.length > 0 ? (
                <Menu>{dataSource}</Menu>
              ) : (
                <Empty description={noDataMessage ?? 'No Results'} image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </animated.div>
          )
      )}
    </div>
  );
};

export default SearchBox;
