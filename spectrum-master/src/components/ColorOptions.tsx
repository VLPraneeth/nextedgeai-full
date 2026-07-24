import { Icon, Tooltip } from 'antd';
import cx from 'classnames';

import './ColorOptions.less';

export interface ColorOptionProps {
  id: string;
  label: string;
  hex: string;
  selected?: boolean;
  onSelectionChange?: (newColor: string) => void;
}

export const ColorOption = ({ id, label, hex, selected, onSelectionChange }: ColorOptionProps) => {
  return (
    <Tooltip title={label}>
      <div
        role="button"
        className={cx('color-options__color', selected && 'color-options__color--selected')}
        style={{ backgroundColor: hex }}
        onClick={() => onSelectionChange?.(id)}>
        {selected && <Icon type="check" className="color-options__color-icon" />}
      </div>
    </Tooltip>
  );
};

export interface ColorOptionsProps {
  colors: { id: string; label: string; hex: string }[];
  selectedColor?: string;
  onSelectionChange?: (newColor: string) => void;
}

const ColorOptions = ({ colors, onSelectionChange, selectedColor }: ColorOptionsProps) => {
  return (
    <div className={cx('color-options')}>
      {colors.map((color) => (
        <ColorOption
          key={color.hex}
          {...color}
          selected={selectedColor === color.hex}
          onSelectionChange={onSelectionChange}
        />
      ))}
    </div>
  );
};

export default ColorOptions;
