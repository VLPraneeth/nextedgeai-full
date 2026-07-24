import { Form } from 'antd';

import InputWithLabel from 'components/inputs/InputWithLabel';
import SelectInput from 'components/SelectInput';
import { baseChartColors } from 'components/vizer/utils/VizerGraphColors';
import { tNamespaced } from 'utils/i18nUtil';
import './ColorThemeInput.scss';

const tn = tNamespaced('InsightsStudio');

export interface StackingInputProps {
  value: string;
  onChange: (value: string) => void;
  label?: string;
  tooltip?: string;
}

export const ColorThemeInput = ({ value, onChange, label, tooltip }: StackingInputProps) => {
  const options = baseChartColors.map(({ color, name }) => ({
    value: color,
    label: name,
    labelComponent: (
      <span className="color-theme-input__label">
        <span
          style={{
            background: color,
          }}
          className="color-theme-input__label-color"
        />
        {name}
      </span>
    ),
  }));

  return (
    <div>
      <InputWithLabel
        label={label || tn('color_theme')}
        tooltip={tooltip ?? tn('Tooltips.color_theme')}
        input={
          <Form.Item>
            <SelectInput
              id="color-theme"
              className="color-theme-input"
              value={value}
              options={options}
              onChange={onChange}
              dropdownMatchSelectWidth
            />
          </Form.Item>
        }
      />
    </div>
  );
};
