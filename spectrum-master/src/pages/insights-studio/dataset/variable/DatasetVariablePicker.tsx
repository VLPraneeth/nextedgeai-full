//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Icon, Mentions, Tag, Tooltip } from 'antd';
import cx from 'classnames';
import { useCallback, useMemo, useState } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputContainer from 'components/inputs/InputContainer';
import { OperatorValue } from 'components/inputs/types';
import { FieldDataType } from 'components/types';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { DatasetVariable } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';

import { isValueVariable } from './DatasetVariable.util';
import { DatasetVariableMultiValue } from './DatasetVariableMultiValue';
import DatasetVariablePopoverForm from './DatasetVariablePopoverForm';

import './DatasetVariablePicker.less';

const { Option: MentionOption } = Mentions;

// TODO: Refactor deep nesting updates and render
export interface DatasetFunctionPickerProps {
  onChange: (value: any) => void;
  values: any[];
  [key: string]: any;
  filterDataTypes?: string[];
  operatorValue?: OperatorValue;
}

const DatasetVariablePicker = ({
  onChange,
  values,
  defaultValue,
  filterDataTypes,
  operatorValue,
  ...rest
}: DatasetFunctionPickerProps) => {
  const [visible, setVisibleState] = useState(false);
  const [variable, setVariable] = useState<DatasetVariable | undefined>();
  const [active, setActive] = useState(false);
  const { tn } = useI18nContext();
  const { setPopupIsOpen, variables } = useUnifiedDataCardAuthoring();

  const setVisible = useCallback(
    (vis: boolean) => {
      setVisibleState(vis);
      setPopupIsOpen(vis);
    },
    [setPopupIsOpen]
  );

  const variablePicklistValues = useMemo(() => {
    return [
      ...(values || []),
      ...(variables || []).map((variable) => {
        return {
          value: `{{${variable.apiName}}}`,
          datatype: variable.datatype,
          label: variable.apiName,
        };
      }),
    ].filter((variable) => (filterDataTypes && variable.datatype ? filterDataTypes.includes(variable.datatype) : true));
  }, [filterDataTypes, values, variables]);

  const variableInput = useMemo(() => {
    if (!Array.isArray(defaultValue) && isValueVariable(defaultValue)) {
      const value = defaultValue.replace('{{', '').replace('}}', '');
      const vari = variables?.find((variable) => variable.apiName === value);

      return (
        <div className="dataset-variable-picker--variable-view">
          <Tag
            closable
            onClose={(evt: React.MouseEvent<HTMLElement>) => {
              evt.stopPropagation();
              onChange?.('');
              setVariable(undefined);
              setVisible(false);
            }}
            onClick={(evt: React.MouseEvent<HTMLElement>) => {
              setVisible(true);
              vari && setVariable(vari);
              evt.stopPropagation();
            }}>
            {vari ? (
              <FieldTypeBadge dataType={vari.datatype as FieldDataType} disableTooltip>
                <Tooltip
                  title={
                    <>
                      {`Api name: ${vari.apiName}`}
                      <br />
                      {`Datatype: ${vari.datatype}`}
                    </>
                  }>
                  {vari.displayName}
                </Tooltip>{' '}
              </FieldTypeBadge>
            ) : (
              value
            )}
          </Tag>
        </div>
      );
    }
    return (
      <>
        <div
          className={cx('dataset-variable-picker__wrapper-div', {
            'dataset-variable-picker__wrapper-div--active': active,
          })}>
          <button
            onClick={(evt) => {
              evt.stopPropagation();
              setVisible(true);
            }}>
            <Icon type="plus" /> {tn('variable')}
          </button>
        </div>
        {values?.length ? (
          <InputContainer
            name="picklist"
            id="picklist"
            defaultValue={defaultValue}
            datatype={AppConstants.INPUT_TYPE.PICKLIST}
            onChange={(value: string) => onChange?.(value)}
            values={variablePicklistValues}
          />
        ) : operatorValue?.datatype === AppConstants.INPUT_TYPE.MULTIVALUETEXT ? (
          <DatasetVariableMultiValue onChange={onChange} defaultValue={defaultValue} />
        ) : (
          <Mentions
            value={defaultValue}
            prefix={['{{']}
            filterOption={(input: string, option: any) => {
              return option.value?.toLowerCase()?.indexOf(input?.toLowerCase()) !== -1;
            }}
            onChange={(value) => onChange?.(value)}
            onSelect={(option) => onChange?.(`{{${option?.value}}}`)}>
            {variables?.map((variable) => {
              return (
                <MentionOption value={variable.apiName} key={variable.apiName}>
                  <div className="synri-field-option">
                    <FieldTypeBadge dataType={variable.datatype as FieldDataType} />
                    <span className="synri-field-option-display-name">{variable.displayName}</span>
                    <span className="synri-field-option-api-name">({variable.apiName})</span>
                  </div>
                </MentionOption>
              );
            })}
          </Mentions>
        )}
      </>
    );
  }, [
    active,
    defaultValue,
    onChange,
    operatorValue?.datatype,
    setVisible,
    tn,
    values?.length,
    variablePicklistValues,
    variables,
  ]);

  return (
    <div
      className={cx('dataset-variable-picker')}
      onMouseEnter={() => setActive(true)}
      onMouseLeave={() => setActive(false)}>
      <DatasetVariablePopoverForm
        visible={visible}
        setVisible={setVisible}
        onChange={(value: string) => {
          onChange?.(
            operatorValue?.datatype === AppConstants.INPUT_TYPE.MULTIVALUETEXT
              ? [...(Array.isArray(defaultValue) ? defaultValue : []), value]
              : value
          );
        }}
        defaultValue={variable}
        filterDataTypes={filterDataTypes}
        popOverTrigger={variableInput}
      />
    </div>
  );
};

export default withI18n(DatasetVariablePicker, 'Dataset.VariablePicker');
