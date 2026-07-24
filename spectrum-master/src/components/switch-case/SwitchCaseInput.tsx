import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { upperFirst } from 'lodash';
import { useCallback, useMemo, useState } from 'react';

import { FieldItem } from 'components/inputs/FieldOptions';
import InputContainer from 'components/inputs/InputContainer';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select, { Option } from 'components/inputs/Select';
import TokenizableFieldGroup from 'components/inputs/TokenizableFieldGroup';
import { HStack, Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { JsonInputPopup } from './JsonInput';
import { SupportedDatatype, SwitchCaseInputValue } from './SwitchCase.types';

import './SwitchCaseInput.scss';

const INPUT_TYPE = AppConstants.INPUT_TYPE;

const tn = tNamespaced('SwitchCaseInput');

// Datatypes that use the json input component
const JSON_DATATYPES = ['json', 'object'];

export interface SwitchCaseInputProps {
  isDefault?: boolean;
  datatypes?: SupportedDatatype[];
  onChange?: (value: SwitchCaseInputValue) => void;
  defaultValue: SwitchCaseInputValue;
  displayMode?: string;
}

export const SwitchCaseInput = ({
  isDefault = false,
  datatypes,
  defaultValue,
  onChange,
  displayMode,
}: SwitchCaseInputProps) => {
  const [value, setValue] = useState<SwitchCaseInputValue>(
    defaultValue?.caseId
      ? defaultValue
      : {
          caseId: ObjectID.generate(),
        }
  );

  const changeHandler = useCallback(
    (key: string, newValue: string | boolean) => {
      setValue((current) => {
        return {
          ...current,
          [key]: newValue,
        };
      });
      if (value?.caseId) {
        onChange?.({ ...value, [key]: newValue });
      }
    },
    [onChange, value]
  );

  const filterOption = useCallback((inputValue, option) => {
    return (option as any).props.children.props.displayName?.toLowerCase()?.includes(inputValue.toLowerCase());
  }, []);

  const dataTypeOptions = useMemo(() => {
    const datatype = datatypes?.map((dataType) => dataType.value);

    return datatype
      ? datatype.map((dataType: string) => (
          <Option key={dataType} value={dataType}>
            <FieldItem displayName={upperFirst(dataType)} dataType={dataType as FieldDataType} apiName="" />
          </Option>
        ))
      : null;
  }, [datatypes]);

  return displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY ? (
    <SwitchCaseInputReadonly {...{ isDefault, datatypes, defaultValue, onChange, displayMode }} />
  ) : (
    <Stack spacing="z">
      {isDefault ? <label className="synri-label">{tn('default_case_label')}</label> : null}
      <Stack className={cx('switch-case', isDefault && 'switch-case--default')} spacing="xxxs">
        <HStack align="center" justify="center" className="switch-case__inputs" spacing="z">
          <InputWithLabel
            label={tn('case_name')}
            value={isDefault ? tc('default') : value?.caseName}
            className="switch-case__inputs__name"
            disabled={isDefault}
            onChange={(evt: React.ChangeEvent<HTMLInputElement>) => changeHandler('caseName', evt.target.value)}
            datatype={INPUT_TYPE.STRING}
          />
          <InputWithLabel
            label={tn('data_type')}
            className="switch-case__inputs__data-type-input"
            placeholder={tn('select_data_type')}
            input={
              <Select<FieldDataType>
                onChange={(datatype: string) => changeHandler('datatype', datatype)}
                filterOption={filterOption}
                value={value?.datatype as FieldDataType}>
                {dataTypeOptions}
              </Select>
            }
          />
          <InputWithLabel
            className="switch-case__inputs__multivalued"
            datatype={INPUT_TYPE.CHECKBOX}
            checked={value?.multivalued}
            onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
              changeHandler('multivalued', evt.target.checked);
            }}
            label={tc('multi_valued')}
          />
        </HStack>
        <SwitchCaseValueInput
          multivalued={value?.multivalued}
          datatype={value?.datatype || INPUT_TYPE.STRING}
          defaultValue={value?.multivalued && !value?.value ? JSON.stringify(EMPTY_ARRAY) : value?.value}
          onChange={(value) => {
            changeHandler('value', value);
          }}
        />
      </Stack>
    </Stack>
  );
};

type SwitchCaseInputReadonlyProps = Omit<SwitchCaseInputProps, 'datatypes'>;

const SwitchCaseInputReadonly = ({ isDefault = false, defaultValue }: SwitchCaseInputReadonlyProps) => {
  return (
    <div className={cx('switch-case-input-readonly', isDefault && 'switch-case-input-readonly--default')}>
      {isDefault ? tn('default_case') : tn('case')} <b>{defaultValue.caseName}</b> with{' '}
      {defaultValue?.multivalued ? ' multi-valued ' : ' '} <b>{defaultValue?.datatype}</b> datatype and value{' '}
      <b>{defaultValue?.value}</b>
    </div>
  );
};

export interface SwitchCaseValueInputProps {
  multivalued?: boolean;
  defaultValue?: string;
  onChange: (value: string) => void;
  datatype: string;
}

export const SwitchCaseValueInput = ({ defaultValue, onChange, multivalued, datatype }: SwitchCaseValueInputProps) => {
  if (JSON_DATATYPES.includes(datatype?.toLowerCase()) || multivalued) {
    return <JsonInputPopup defaultValue={defaultValue} onChange={onChange} />;
  } else {
    return (
      <TokenizableFieldGroup
        className="switch-case-value-input"
        disableTokens={false}
        label={tc('value')}
        fallbackOnTokenSelect={(token) => onChange(token.token)}>
        <InputContainer
          datatype={datatype}
          className="switch-case__inputs__value"
          value={defaultValue}
          renderType={AppConstants.INPUT_RENDER_TYPE.TOKENS}
          onChange={(evt: React.ChangeEvent<HTMLInputElement> | string) => {
            onChange(typeof evt === 'string' ? evt : evt.target.value);
          }}
        />
      </TokenizableFieldGroup>
    );
  }
};
