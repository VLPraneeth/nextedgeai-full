import { Popover } from 'antd';
import cx from 'classnames';
import { useRef, useState } from 'react';

import { DropdownDisclosureArrow } from 'components/dropdown-disclosure-arrow/DropdownDisclosureArrow';
import InlineSvg from 'components/icons/InlineSvg';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { TextTag } from 'components/text-tag';
import { TextTagColorOptions } from 'components/text-tag/TextTag';
import { UserflowTags } from 'utils/UserflowTags';

import './Dropdown.less';

// Extra padding on the popover so the user would see the
// top and bottom of the popover list
const POPOVER_PADDING = 20;

interface OptionItemProps {
  title: string;

  icon?: string | React.FC;
  isSelected?: boolean;
  onClick?: () => void;
  subtitle?: string;
  textTag?: string;
  tagColor?: TextTagColorOptions;
  disabled?: boolean;
}

export const OptionItem = ({
  disabled,
  title,
  icon,
  isSelected = false,
  onClick,
  subtitle,
  textTag,
  tagColor = 'dark-gray',
}: OptionItemProps) => {
  const iconPath = typeof icon === 'string' && icon;
  const IconNode = typeof icon !== 'undefined' && typeof icon !== 'string' && icon;

  return (
    <button
      disabled={disabled}
      aria-label={title}
      onClick={onClick}
      className={cx('dropdown-selector-item', {
        'is-selected': isSelected,
      })}>
      <div className="dropdown-selector-item__left-content">
        {IconNode && (
          <span className="option-icon">
            <IconNode />
          </span>
        )}
        {iconPath && <InlineSvg src={iconPath} title={title} className="option-icon" />}
        <span className="option-name">{title}</span>
        {!!subtitle && <span className="option-subtitle">{subtitle}</span>}
      </div>
      {!!textTag && <TextTag text={textTag} color={tagColor} className="tag-skip-margin" />}
    </button>
  );
};

export interface DropdownOption {
  id: string;
  name: string;

  icon?: string | React.FC;
  subtitle?: string;
  textTag?: string;
  tagColor?: TextTagColorOptions;
  disabled?: boolean;
}

interface DropdownProps<T extends DropdownOption> {
  selected?: T;
  options: T[];
  onChange: (selected: T) => void;
  disabled?: boolean;
  placeholderText?: string;
  tagColor?: TextTagColorOptions;
  style?: React.CSSProperties;
}

function Dropdown<T extends DropdownOption = DropdownOption>({
  disabled = false,
  selected,
  options,
  placeholderText = 'Select',
  onChange,
  style,
  tagColor,
}: DropdownProps<T>) {
  const [popoverShowing, setPopoverShowing] = useState(false);
  const selectorControlRef = useRef<HTMLDivElement>(null);

  const handleChange = (item: T) => () => {
    onChange(item);
    setPopoverShowing(false);
  };

  return (
    <div className={cx('toolbar-dropdown', { disabled })} data-userflow-tag={UserflowTags.Toolbar.Selector}>
      <Popover
        placement="bottomLeft"
        trigger="click"
        overlayClassName="toolbar-dropdown-selector-dropdown"
        visible={!disabled && popoverShowing}
        onVisibleChange={(val) => {
          if (disabled) {
            return;
          }
          setPopoverShowing(val);
        }}
        overlayStyle={{
          minWidth: selectorControlRef.current?.offsetWidth,
        }}
        content={
          // TODO: Add search capability
          <ScrollableArea bottomOffset={POPOVER_PADDING} collapse>
            {options.map((item) => (
              <OptionItem
                key={item.id}
                icon={item.icon}
                isSelected={item.id === selected?.id}
                onClick={handleChange(item)}
                subtitle={item.subtitle}
                tagColor={tagColor}
                textTag={item.textTag}
                title={item.name}
                disabled={item.disabled}
              />
            ))}
          </ScrollableArea>
        }>
        <div style={{ ...style }} className="toolbar-dropdown-selector-control" ref={selectorControlRef}>
          {selected ? (
            <OptionItem
              icon={selected.icon}
              subtitle={selected.subtitle}
              title={selected.name}
              textTag={selected.textTag}
              tagColor={selected.tagColor}
            />
          ) : (
            <div className="toolbar-dropdown-selector-placeholder">{placeholderText}</div>
          )}
          {!disabled && <DropdownDisclosureArrow isOpen={popoverShowing} />}
        </div>
      </Popover>
    </div>
  );
}

export default Dropdown;
