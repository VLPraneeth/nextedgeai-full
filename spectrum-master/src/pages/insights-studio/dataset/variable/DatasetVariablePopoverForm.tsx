//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Alert, Checkbox, Modal } from 'antd';
import Button from 'antd/lib/button';
import { CheckboxChangeEvent } from 'antd/lib/checkbox';
import Popover from 'antd/lib/popover';
import Select, { OptionProps } from 'antd/lib/select';
import React, { ChangeEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputContainer from 'components/inputs/InputContainer';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Divider, Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { hasSpecialCharacters } from 'components/validator/validationFunctions';
import useEventListener from 'hooks/useEventListener';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { DatasetVariable, VariableValue } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { humanize } from 'utils/StringUtil';
import { DeepPartial } from 'utils/TypeUtils';

import { VariableDatatypes } from './DatasetVariableConfiguration';
import DatasetVariableValue from './DatasetVariableValue';

import './DatasetVariablePopoverForm.less';

const { Option } = Select;

const advanceDatasetTn = tNamespaced('InsightsStudio');

export interface DatasetVariablePopoverFormProps {
  visible: boolean;
  setVisible: (visible: boolean) => void;
  popOverTrigger?: React.ReactNode;
  defaultValue?: DatasetVariable;
  onChange: any;
  filterDataTypes?: string[];
  formType?: DatasetVariablePopoverFormType;
}

export type DatasetVariablePopoverFormType = 'popover' | 'modal';

const DatasetVariablePopoverForm = ({
  visible,
  setVisible,
  popOverTrigger,
  defaultValue,
  onChange,
  filterDataTypes,
  formType = 'popover',
}: DatasetVariablePopoverFormProps) => {
  const { t, tc, tn } = useI18nContext();
  const [variable, setVariable] = useState<DeepPartial<DatasetVariable>>({ variableDefaultValue: {} });
  const { variables, setVariables } = useUnifiedDataCardAuthoringContext();
  const { makeVariableApiName } = useDatasetConfig();
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  const isUpdate = !!defaultValue?.apiName;

  useEffect(() => {
    setVariable(defaultValue || {});
  }, [defaultValue]);

  const filterOption = (input: string, option: React.ReactElement<OptionProps>) => {
    return option?.props?.value ? option.props.value.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0 : false;
  };

  const datatypeOptions = useMemo(() => {
    const options = Object.keys(VariableDatatypes)
      .filter((datatype) => (filterDataTypes ? filterDataTypes.includes(datatype) : true))
      .map((datatype) => (
        <Option value={datatype} key={datatype}>
          <div className="dataset-variable-popover__datatype-option">
            <FieldTypeBadge dataType={datatype as FieldDataType} description={humanize(datatype)} disableTooltip />
            <span>{humanize(datatype)}</span>
          </div>
        </Option>
      ));
    return options;
  }, [filterDataTypes]);

  const save = (evt: React.MouseEvent<HTMLElement>) => {
    if (!validate() || !variable.displayName) {
      evt.stopPropagation();
      return;
    }

    setVisible(false);
    const { datatype = 'string', apiName, required, multiValueField, displayName, variableDefaultValue } = variable;

    const updatedVariable: DatasetVariable = {
      apiName,
      datatype,
      required,
      displayName,
      multiValueField,
      variableDefaultValue: {
        ...variable.variableDefaultValue,
        datatype,
        defaultValue: variableDefaultValue?.defaultValue || '',
        defaultValueType: 'LITERAL',
      },
    };
    if (variable.apiName) {
      const localVari = (variables || [])?.map((vari) => {
        return vari.apiName === variable.apiName ? { ...updatedVariable, apiName: variable.apiName } : { ...vari };
      });
      setVariables(localVari);
    } else {
      updatedVariable.apiName = makeVariableApiName(variable.displayName);
      setVariables([...(variables || []), updatedVariable]);
    }
    if (formType === 'modal') {
      setVariable({});
    }
    onChange?.(`{{${updatedVariable.apiName}}}`);
  };

  const validate = () => {
    if (!variable.displayName) {
      setValidationMessage(tn('variable_name_required'));
      return false;
    }

    if (!isUpdate && variables?.find((findVar) => findVar.displayName === variable.displayName)) {
      setValidationMessage(tn('duplicate_not_allowed'));
      return false;
    }

    if (hasSpecialCharacters(variable.displayName)) {
      setValidationMessage(t('FieldValidator.noSpecialCharacters'));
      return false;
    }

    setValidationMessage(null);
    return true;
  };

  const popOverRef = useRef<Popover>(null);
  const datatypeOptionsRef = useRef<HTMLDivElement>(null);
  const variableValueOptionsRef = useRef<HTMLDivElement>(null);

  useEventListener('click', (e) => {
    if (formType === 'modal') {
      return;
    }
    if (!visible) {
      return;
    }

    if (e.target instanceof Node && !popOverRef.current?.getPopupDomNode()?.contains(e.target)) {
      close();
    }
  });

  const close = useCallback(() => {
    setVisible(false);
    setVariable(defaultValue || {});
    setValidationMessage(null);
  }, [defaultValue, setVisible]);

  function renderVariableForm() {
    const InputComponent = formType === 'modal' ? InputWithLabel : InputContainer;
    return (
      <Stack className="dataset-variable-popover--inputs">
        {validationMessage && <Alert message={validationMessage} type="error" />}
        <InputComponent
          label={tc('display_name')}
          name="displayName"
          datatype={AppConstants.INPUT_TYPE.STRING}
          defaultValue={variable?.displayName}
          value={variable?.displayName}
          placeholder={tn('display_name_place_holder')}
          onChange={(evt: ChangeEvent<HTMLInputElement>) =>
            setVariable({ ...(variable || {}), displayName: evt.target.value })
          }
        />
        <div ref={datatypeOptionsRef} />
        <InputComponent
          label={tc('data_type')}
          name="datatype"
          placeholder={tn('datatype_place_holder')}
          className="dataset-variable-popover--inputs--datatype"
          datatype={AppConstants.INPUT_TYPE.PICKLIST}
          defaultValue={variable?.datatype}
          value={variable?.datatype}
          getPopupContainer={() => datatypeOptionsRef.current}
          options={datatypeOptions}
          onChange={(value: string) =>
            setVariable({
              ...variable,
              datatype: value,
              variableDefaultValue: {
                ...variable.variableDefaultValue,
                datatype: value,
              },
            })
          }
          filterOption={filterOption}
        />
        <Checkbox
          checked={variable?.required}
          onChange={(evt: CheckboxChangeEvent) => setVariable({ ...variable, required: evt.target.checked })}>
          {tc('required_text')}
        </Checkbox>
        <Checkbox
          checked={variable?.multiValueField}
          onChange={(evt: CheckboxChangeEvent) => setVariable({ ...variable, multiValueField: evt.target.checked })}>
          {tc('multi_value')}
        </Checkbox>
        <div ref={variableValueOptionsRef} />
        <DatasetVariableValue
          formType={formType}
          className="dataset-variable-popover--inputs--default-value"
          defaultValue={variable.variableDefaultValue as VariableValue}
          placeholder={t('Dataset.VariablePicker.default_value_place_holder')}
          getPopupContainer={() => variableValueOptionsRef.current}
          multiValueField={variable?.multiValueField}
          onChange={(value) =>
            setVariable({
              ...variable,
              variableDefaultValue: value,
            })
          }
        />
      </Stack>
    );
  }

  if (formType === 'popover') {
    return (
      <Popover
        ref={popOverRef}
        placement="bottomLeft"
        overlayClassName="dataset-variable-popover"
        visible={visible}
        content={
          visible ? (
            <>
              {renderVariableForm()}
              <Divider />
              <div className="dataset-variable-popover--footer">
                <Button size="small" onClick={close}>
                  {tc('close')}
                </Button>
                <Button size="small" type="primary" onClick={save}>
                  {defaultValue?.apiName ? tc('update') : tc('add')}
                </Button>
              </div>
            </>
          ) : null
        }>
        {popOverTrigger}
      </Popover>
    );
  }

  if (formType === 'modal') {
    return (
      <Modal
        visible={visible}
        onOk={save}
        onCancel={close}
        title={
          isUpdate
            ? advanceDatasetTn('AdvanceDataset.edit_variable')
            : advanceDatasetTn('AdvanceDataset.create_variable')
        }>
        {renderVariableForm()}
      </Modal>
    );
  }
  return null;
};

export default withI18n(DatasetVariablePopoverForm, 'Dataset.VariablePicker');
