import { Dropdown, Menu, Tooltip } from 'antd';
import { useState } from 'react';

import { ReactComponent as ChevronDown } from 'assets/icons/chevron-down.svg';
import { ReactComponent as Tick } from 'assets/icons/tick.svg';
import { VizerGraphColors, chartColorGrays, chartColors, darkColors } from 'components/vizer/utils/VizerGraphColors';
import { tc } from 'utils/i18nUtil';
import './ColorPickerGrid.scss';

export function ColorPickerGrid({
  color,
  onChange,
  label,
}: {
  color: string;
  onChange: (value: string) => void;
  label?: string;
}) {
  const [visible, setVisible] = useState(false);
  const [selectedColor, setSelectedColor] = useState(
    [...chartColors, ...chartColorGrays].find((clr) => clr.color === color) || VizerGraphColors.getDefaultColor(color)
  );

  function handleColorSelect(newColor: typeof chartColors[number]) {
    setSelectedColor(newColor);
    setVisible(false);
    onChange(newColor.color);
  }

  return (
    <div className="color-picker-grid">
      <div className="color-picker-grid__label">{label}</div>
      <Dropdown
        overlay={
          <Menu>
            <Menu.Item className="color-picker-grid__option">
              <ColorGrid onSelect={handleColorSelect} selectedColor={selectedColor} />
            </Menu.Item>
          </Menu>
        }
        trigger={['click']}
        visible={visible}
        onVisibleChange={(v) => setVisible(v)}>
        <div className="color-picker-grid__trigger">
          <div className="color-picker-grid__selected-color">
            <div
              style={{
                background: selectedColor.color,
              }}
            />
            <span>{selectedColor.name}</span>
          </div>
          <ChevronDown width={10} height={6} />
        </div>
      </Dropdown>
    </div>
  );
}

function ColorGrid({
  onSelect,
  selectedColor,
}: {
  onSelect: (color: typeof chartColors[number]) => void;
  selectedColor?: typeof chartColors[number];
}) {
  return (
    <div className="color-grid-container">
      <p>{tc('greyscale')}</p>
      <div className="gray-grid">
        {chartColorGrays.map((color) => (
          <ColorItem color={color} onSelect={onSelect} selectedColor={selectedColor} />
        ))}
      </div>
      <p>{tc('colors')}</p>
      <div className="color-grid">
        {chartColors.map((color) => (
          <ColorItem color={color} onSelect={onSelect} selectedColor={selectedColor} />
        ))}
      </div>
    </div>
  );
}

function ColorItem({
  color: { color, name },
  onSelect,
  selectedColor,
}: {
  color: typeof chartColors[number];
  onSelect: (color: typeof chartColors[number]) => void;
  selectedColor?: typeof chartColors[number];
}) {
  const [tooltipVisible, setTooltipVisible] = useState(false);

  return (
    <Tooltip title={name} visible={tooltipVisible} onVisibleChange={(v) => setTooltipVisible(v)}>
      <div
        className="color-item"
        onClick={() => {
          setTooltipVisible(false);
          onSelect({ name, color });
        }}
        style={{
          background: color,
        }}>
        <Tick
          width={20}
          height={20}
          className={`tick ${darkColors.includes(color) ? 'dark-color' : ''} ${
            name === selectedColor?.name ? 'active' : ''
          }`}
        />
      </div>
    </Tooltip>
  );
}
