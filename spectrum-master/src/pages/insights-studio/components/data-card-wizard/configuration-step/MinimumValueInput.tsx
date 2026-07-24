import { Form, InputNumber } from 'antd';
import { ChangeEventHandler } from 'react';

import Fieldset from 'components/Fieldset';
import Input from 'components/inputs/Input';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { DataCardVizConfig } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced, tc } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export interface MinimumValueInputProps {
  minimumValue: DataCardVizConfig['minimumValue'];
  onChange: (value: DataCardVizConfig['minimumValue']) => void;
}

export const MinimumValueInput = ({ minimumValue, onChange }: MinimumValueInputProps) => {
  const setMinimumValue = (value: number | undefined) => {
    minimumValue && onChange({ ...minimumValue, value });
  };
  const setLabel: ChangeEventHandler<HTMLInputElement> = (e) => {
    minimumValue && onChange({ ...minimumValue, label: e.target.value });
  };
  const setApplyToSubCategories = (applyToSubCategories: boolean) => {
    minimumValue && onChange({ ...minimumValue, applyToSubCategories });
  };
  return (
    <Fieldset collapsible title={tn('minimum_value')} className="">
      <div className="data-card-config-step__input-group data-card-config-step__input-group--fieldset">
        <InputWithLabel
          label={tn('minimum_value')}
          tooltip={tn('Tooltips.minimum_value')}
          input={
            <Form.Item>
              <InputNumber width={'100%'} value={minimumValue?.value} onChange={setMinimumValue} />
            </Form.Item>
          }
        />

        <InputWithLabel
          label={tc('label')}
          tooltip={tn('Tooltips.minimum_value_label')}
          input={
            <Form.Item>
              <Input value={minimumValue?.label} onChange={setLabel} />
            </Form.Item>
          }
        />

        <InputWithLabel
          label={tn('apply_to_sub_categories')}
          tooltip={tn('Tooltips.apply_to_sub_categories')}
          datatype={AppConstants.INPUT_TYPE.BOOLEAN}
          value={minimumValue?.applyToSubCategories}
          onChange={setApplyToSubCategories}
        />
      </div>
    </Fieldset>
  );
};
