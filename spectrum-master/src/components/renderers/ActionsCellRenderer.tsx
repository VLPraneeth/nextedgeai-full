import { Popover, Tooltip } from 'antd';
import { TooltipAlignConfig } from 'antd/lib/tooltip';
import * as React from 'react';
import { useState } from 'react';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import { IconButton, IconButtonProps } from 'components/Button';
import { HStack } from 'components/layout';

import './ActionsCell.less';

interface ActionMenuButtonItem {
  key?: string;
  label: string;
  ariaLabel?: string;
  tooltipTitle?: string;
  onSelect: () => void;
}

const ActionMenuButton = ({ label, ariaLabel, onSelect, tooltipTitle, ...rest }: ActionMenuButtonItem) => {
  const actionButton = (
    <button aria-label={ariaLabel || label} onClick={onSelect} {...rest}>
      {label}
    </button>
  );

  if (tooltipTitle) {
    return <Tooltip title={tooltipTitle}>{actionButton}</Tooltip>;
  }

  return actionButton;
};

interface ActionMenuLinkItem extends React.HTMLProps<HTMLAnchorElement> {
  key?: string;
  label: string;
}

const ActionMenuLink = ({ label, ...props }: ActionMenuLinkItem) => <a {...props}>{label}</a>;

type ActionMenuItem = ActionMenuLinkItem | ActionMenuButtonItem;

const isActionMenuButtonItem = (item: ActionMenuItem): item is ActionMenuButtonItem => {
  return typeof item.onSelect !== 'undefined';
};

interface KebabMenuProps {
  items: ActionMenuItem[];
}

const KebabMenu = ({ items }: KebabMenuProps) => (
  <div className="actions-cell-dropdown-menu">
    {items.map((actionItem) => (
      <div className="actions-cell-dropdown-menu-item" key={actionItem.key}>
        {isActionMenuButtonItem(actionItem) ? <ActionMenuButton {...actionItem} /> : <ActionMenuLink {...actionItem} />}
      </div>
    ))}
  </div>
);

type OptionalMenuItem = ActionMenuItem | false;

const kebabPopoverAlignment: TooltipAlignConfig = {
  offset: [0, 10],
};

interface ActionsCellProps {
  menuItems?: OptionalMenuItem[];
  size?: IconButtonProps['size'];
  children?: React.ReactNode;
}

const ActionsCell = ({ children, menuItems, size }: ActionsCellProps) => {
  const [isShowingPopover, setIsShowingPopover] = useState(false);
  const close = () => setIsShowingPopover(false);

  const actionItems: ActionMenuItem[] =
    menuItems?.filter(Boolean).map((item) => {
      if (isActionMenuButtonItem(item as ActionMenuItem)) {
        const { onSelect, ...rest } = item as ActionMenuButtonItem;

        return {
          ...rest,
          // wrap onSelect with close so our popover gets put away
          onSelect: () => {
            onSelect();
            close();
          },
        } as ActionMenuButtonItem;
      }

      return item as ActionMenuLinkItem;
    }) ?? [];

  return (
    <HStack className="actions-cell" spacing="sm" justify="end">
      {children}
      {menuItems && (
        <Popover
          content={<KebabMenu items={actionItems} />}
          visible={isShowingPopover}
          onVisibleChange={setIsShowingPopover}
          trigger="click"
          overlayClassName="actions-cell-menu"
          placement="bottomRight"
          align={kebabPopoverAlignment}>
          <IconButton size={size} icon={KebabIcon} />
        </Popover>
      )}
    </HStack>
  );
};

export default ActionsCell;
