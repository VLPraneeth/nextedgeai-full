import { Form } from 'antd';

import InputWithLabel from 'components/inputs/InputWithLabel';
import SelectInput from 'components/SelectInput';
import { DataCardVizConfig } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export interface LegendPositionInputProps {
  value: DataCardVizConfig['legendPosition'];
  onChange: (legendPosition: DataCardVizConfig['legendPosition']) => void;
}

//@ts-ignore
export const LegendPositionInput = ({ value, onChange }: LegendPositionInputProps) => {
  const options = [
    { value: 'TOP', label: 'Top' },
    { value: 'BOTTOM', label: 'Bottom' },
  ];

  const localOnChange = (value: string) => {
    if (value === 'TOP' || value === 'BOTTOM') {
      onChange(value);
    }
  };

  return (
    <div>
      <InputWithLabel
        label={tn('legend_position')}
        tooltip={tn('Tooltips.legend_position')}
        input={
          <Form.Item>
            <SelectInput id="legend-position" value={value} options={options} onChange={localOnChange} />
          </Form.Item>
        }
      />
    </div>
  );
};
