import { Form } from 'antd';
import { cloneDeep } from 'lodash';

import Checkbox, { CheckboxChangeEvent } from 'components/Checkbox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { FieldValidator } from 'components/validator/FieldValidator';
import DatasetVariableValue from 'pages/insights-studio/dataset/variable/DatasetVariableValue';
import { DatasetVariable, VariableValue } from 'store/insights-studio/types';
import { tNamespaced, tc } from 'utils/i18nUtil';

import './VariableConfigInput.scss';

const tn = tNamespaced('InsightsStudio');

export interface VariableConfigInputProps {
  disabled: boolean;
  removeVariable: (variableApiName: string) => void;
  updateVariable: (variable: DatasetVariable) => void;
  variable: DatasetVariable;
}

export const VariableConfigInput = ({
  disabled,
  removeVariable,
  updateVariable,
  variable,
}: VariableConfigInputProps) => {
  const toggleVariable = (e: CheckboxChangeEvent) => {
    if (e.target.checked) {
      updateVariable(variable);
    } else {
      variable.apiName && removeVariable(variable.apiName);
    }
  };

  const updateDefaultValue = (variableValue: VariableValue) => {
    const newVar = cloneDeep(variable);
    newVar.variableDefaultValue = variableValue;
    updateVariable(newVar);
  };

  const updateUpdatable = (e: CheckboxChangeEvent) => {
    const newVar = cloneDeep(variable);
    newVar.updatable = e.target.checked;
    updateVariable(newVar);
  };

  return (
    <div className="variable-config-input">
      <div>
        <Checkbox checked={!disabled} onChange={toggleVariable}>
          <span className="synri-label">{variable.displayName}</span>
        </Checkbox>
      </div>
      <div className="variable-config-input__field-row">
        <div className="variable-config-input__field-column">
          <FieldValidator
            name={tc('default_value')}
            validationOptions={{ required: variable.required }}
            value={variable.variableDefaultValue.defaultValue}
            render={({ isValid, errorMessage, value, onChange, onBlur }) => {
              return (
                <InputWithLabel
                  label={tc('default_value')}
                  required={variable.required}
                  input={
                    <Form.Item validateStatus={isValid ? '' : 'error'} help={errorMessage}>
                      <DatasetVariableValue
                        disabled={disabled}
                        defaultValue={variable.variableDefaultValue}
                        multiValueField={variable.multiValueField}
                        onChange={(val) => {
                          updateDefaultValue(val);
                          // @ts-expect-error simulating event that isn't passed up through component tree
                          onChange({ target: { value: val.defaultValue } });
                        }}
                        onBlur={(e: React.FocusEvent<HTMLInputElement, Element> | string | string[]) => {
                          // Field validator does not support string and array. Skipping it.
                          if (Array.isArray(e) || typeof e === 'string') {
                            return;
                          }
                          onBlur?.(e);
                        }}
                        value={value}
                      />
                    </Form.Item>
                  }
                />
              );
            }}
          />
        </div>
        <div className="variable-config-input__allow-change-field">
          <Checkbox disabled={disabled} checked={!disabled && variable.updatable} onChange={updateUpdatable}>
            {tn('allow_change')}
          </Checkbox>
        </div>
      </div>
    </div>
  );
};
