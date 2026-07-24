import Dropdown, { DropDownProps } from 'antd/lib/dropdown';
import Menu, { ClickParam } from 'antd/lib/menu';
import * as React from 'react';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import { IconButtonProps, IconButton } from 'components/Button';
import { wrapIcon } from 'utils/IconUtils';

import './KebabMenu.less';

export type KebabMenuClickParams<MenuKey> = Omit<ClickParam, 'key'> & { key: MenuKey };

export interface KebabMenuProps<MenuKeyEnum> extends Omit<DropDownProps, 'overlay' | 'children'> {
  ariaLabel?: string;
  dropdownClassName?: string;
  menuItems: React.ReactNode[];
  onClick?: (clickParams: KebabMenuClickParams<MenuKeyEnum>) => void;
  size?: IconButtonProps['size'];
  style?: React.CSSProperties;
  testId?: string;
  buttonTitle?: string;
}

const KebabMenu = <MenuKeysEnum extends unknown>({
  dropdownClassName,
  style,
  onClick,
  menuItems,
  placement = 'bottomRight',
  trigger = ['click'],
  testId,
  ariaLabel,
  size,
  buttonTitle,
  ...props
}: KebabMenuProps<MenuKeysEnum>) => {
  if (menuItems.length === 0) {
    return null;
  }

  return (
    <Dropdown
      data-testid={testId}
      className={dropdownClassName}
      overlayClassName="synri-kebab-menu-dropdown"
      placement={placement}
      overlay={<Menu onClick={(evt) => onClick?.({ ...evt, key: evt.key as MenuKeysEnum })}>{menuItems}</Menu>}
      trigger={trigger}
      {...props}>
      <IconButton aria-label={ariaLabel} size={size} icon={wrapIcon(KebabIcon)} title={buttonTitle} />
    </Dropdown>
  );
};

export const MenuItemGroup = Menu.ItemGroup;
export const MenuItem = Menu.Item;
export const MenuDivider = Menu.Divider;

/**
 * joinGroupsWithDividers
 *
 * Takes in an array of arrays, filters out anything that isn't a react element, and then
 * joins each of the arrays with dividers.
 *
 * This allows a series of items to be conditionally added based on permissions, etc and will
 * join the groups as expected
 */
export const joinGroupsWithDividers = (groups: (React.ReactNode | false | null | undefined)[][]) => {
  return groups.reduce((acc, group, idx) => {
    const groupItems = group.filter(React.isValidElement);

    if (groupItems.length < 1) {
      return acc;
    }
    if (acc.length < 1) {
      return groupItems;
    }

    return [...acc, <MenuDivider key={`menudivider-${idx}`} />, ...groupItems];
  }, []);
};

export default KebabMenu;
