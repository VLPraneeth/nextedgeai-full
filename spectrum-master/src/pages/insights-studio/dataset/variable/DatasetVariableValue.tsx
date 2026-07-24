import { useCallback, useMemo } from 'react';

import InputContainer, { InputContainerProps } from 'components/inputs/InputContainer';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { VariableValue } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';
import { tc } from 'utils/i18nUtil';

import { DatasetVariablePopoverFormType } from './DatasetVariablePopoverForm';

export interface VariableDefaultValueProps extends InputContainerProps {
  name?: string;
  defaultValue?: VariableValue;
  onChange?: (value: VariableValue) => void;
  multiValueField?: boolean;
  getPopupContainer?: () => HTMLDivElement | null;
  className?: string;
  placeholder?: string;
  formType?: DatasetVariablePopoverFormType;
}

export const initialDefaultValue: VariableValue = {
  additionalParamForDefaultVal: {},
  defaultValue: '',
  datatype: AppConstants.INPUT_TYPE.STRING,
  defaultValueType: 'LITERAL',
};

const VariableDefaultValue = ({
  name,
  defaultValue = initialDefaultValue,
  multiValueField,
  onChange,
  getPopupContainer,
  className,
  placeholder,
  formType = 'popover',
  ...rest
}: VariableDefaultValueProps) => {
  const { datatype } = defaultValue;

  const inputDefaultChecked = useMemo(() => {
    if (datatype === AppConstants.INPUT_TYPE.BOOLEAN) {
      if (
        typeof defaultValue?.defaultValue === 'string' &&
        defaultValue.defaultValue?.toLocaleLowerCase() === AppConstants.TRUE
      ) {
        return true;
      }
      return defaultValue?.defaultValue;
    }
  }, [datatype, defaultValue.defaultValue]);

  const inputOnChange = useCallback(
    (evt: React.ChangeEvent<HTMLInputElement> | string | boolean | string[]) => {
      let changeDefaultValue = {};

      switch (datatype) {
        case AppConstants.INPUT_TYPE.BOOLEAN:
          changeDefaultValue = { defaultValue: typeof evt === 'boolean' ? evt : false };
          break;
        default:
          changeDefaultValue = {
            defaultValue:
              typeof evt === 'string' || Array.isArray(evt) ? evt : typeof evt === 'boolean' ? evt : evt?.target?.value,
          };
          break;
      }
      onChange?.({
        ...defaultValue,
        ...changeDefaultValue,
      });
    },
    [datatype, defaultValue, onChange]
  );

  // Use string as datatype for the picklist and reference default values until
  // we support getting the picklist values.
  const datatypesThatShouldUseString: string[] = [AppConstants.INPUT_TYPE.PICKLIST, AppConstants.INPUT_TYPE.REFERENCE];
  const inputDataType = multiValueField
    ? AppConstants.INPUT_TYPE.MULTIVALUETEXT
    : (datatypesThatShouldUseString.includes(datatype) ? AppConstants.INPUT_TYPE.STRING : datatype) ||
      AppConstants.INPUT_TYPE.STRING;

  const InputComponent = formType === 'modal' ? InputWithLabel : InputContainer;

  return (
    <InputComponent
      label={formType === 'modal' ? tc('default_value') : undefined}
      getPopupContainer={getPopupContainer}
      className={className}
      name={name}
      datatype={inputDataType}
      defaultValue={defaultValue.defaultValue}
      value={defaultValue.defaultValue}
      defaultChecked={inputDefaultChecked}
      onChange={inputOnChange}
      placeholder={placeholder}
      {...rest}
    />
  );
};

export default VariableDefaultValue;
