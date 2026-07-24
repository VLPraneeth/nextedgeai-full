import { Form } from 'antd';

import InputWithLabel from 'components/inputs/InputWithLabel';
import SelectInput from 'components/SelectInput';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export interface StackingInputProps {
  value: string;
  onChange: (value: string) => void;
}

export const StackingInput = ({ value, onChange }: StackingInputProps) => {
  const stackingOptions = [
    { value: 'normal', label: 'Normal' },
    { value: 'percent', label: 'Percent' },
  ];

  return (
    <div>
      <InputWithLabel
        label={tn('stacking')}
        tooltip={tn('Tooltips.stacking')}
        input={
          <Form.Item>
            <SelectInput id="stacking" value={value} options={stackingOptions} onChange={onChange} allowClear />
          </Form.Item>
        }
      />
    </div>
  );
};
