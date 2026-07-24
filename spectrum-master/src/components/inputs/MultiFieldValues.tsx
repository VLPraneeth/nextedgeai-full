//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Icon } from 'antd';
import cx from 'classnames';
import { each, map } from 'lodash';
import * as React from 'react';
import { useEffect, useState } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { HStack } from 'components/layout';
import usePreviousValue from 'hooks/usePreviousValue';
import { MutiValueFieldPickListValues } from 'store/test/types';
import AppConstants from 'utils/AppConstants';
import { tc } from 'utils/i18nUtil';

import './MultiFieldValues.less';

export interface FieldValue {
  id?: string;
  label?: string;
  datatype?: string;
  value?: string | boolean | string[];
  isMultiValueField?: boolean;
  renderType?: string;
}

export type MultiValues = Record<string, FieldValue>;

export interface MultiFieldValueProps {
  name: string;
  className?: string;
  label?: string;
  onChange?: (values: MultiValues) => void;
  defaultValue?: MultiValues;
  value?: MultiValues;
  disabled?: boolean;
  picklistValues?: MutiValueFieldPickListValues;
}

const MULTIFIELD_DATATYPE_MAP: Record<string, string> = {
  [AppConstants.INPUT_TYPE.PICKLIST]: AppConstants.INPUT_TYPE.AUTOCOMPLETE,
  [AppConstants.INPUT_TYPE.REFERENCE]: AppConstants.INPUT_TYPE.AUTOCOMPLETE,
  [AppConstants.INPUT_TYPE.BOOLEAN]: AppConstants.INPUT_TYPE.CHECKBOX,
};

const getDatatype = (datatype?: string) =>
  datatype && MULTIFIELD_DATATYPE_MAP[datatype] ? MULTIFIELD_DATATYPE_MAP[datatype] : datatype;

const MultiFieldValue = ({
  name,
  onChange,
  label,
  className,
  defaultValue,
  value,
  disabled,
  picklistValues,
}: MultiFieldValueProps) => {
  const [formValues, setFormValues] = useState<MultiValues>(defaultValue || {});
  const [fieldPicklistVisible, setFieldPicklistVisible] = useState<boolean>(false);
  const [selectedField, setSelectedField] = useState<string>('');
  const previousValue = usePreviousValue(value);

  useEffect(() => {
    if (previousValue !== value) {
      setFormValues(value || {});
      if (!value) {
        setFieldPicklistVisible(false);
      }
    }
  }, [value, previousValue]);

  const onAdd = () => {
    if (!fieldPicklistVisible) {
      setFieldPicklistVisible(true);
    } else {
      const picklistValue = picklistValues?.find((val) => val.value === selectedField);
      if (picklistValue) {
        const { value: id, label, datatype, isMultiValueField } = picklistValue;
        const newValues = {
          ...formValues,
          [selectedField]: {
            id,
            label,
            datatype: isMultiValueField ? AppConstants.INPUT_TYPE.MULTIVALUETEXT : getDatatype(datatype),
            value: datatype === AppConstants.INPUT_TYPE.BOOLEAN ? false : undefined,
          },
        };

        setFormValues(newValues);
        setSelectedField('');
        onChange?.(newValues);
        setFieldPicklistVisible(false);
      }
    }
  };

  const onDelete = (id: string) => {
    const newValues: MultiValues = {};
    each(formValues, (v, k) => {
      if (k !== id) {
        newValues[k] = v;
      }
    });
    setFormValues(newValues);
    onChange?.(newValues);
  };

  const onPicklistChange = (name: string) => {
    setSelectedField(name);
  };

  const onValueChange = (field: FieldValue, evt: React.ChangeEvent<HTMLInputElement> | string | string[]) => {
    if (field?.id) {
      let value: string | string[] | boolean = '';
      if (Array.isArray(evt)) {
        value = evt;
      } else {
        const isBooleanField =
          field.datatype === AppConstants.INPUT_TYPE.BOOLEAN || field.datatype === AppConstants.INPUT_TYPE.CHECKBOX;
        value = typeof evt === 'string' ? evt : isBooleanField ? evt.target.checked : evt.target.value;
      }
      formValues[field.id] = {
        ...formValues[field.id],
        value,
      };

      setFormValues(formValues);
      onChange?.(formValues);
    }
  };

  return (
    <div className={cx('synri-multi-field-value', className)}>
      <InputWithLabel
        label={label}
        input={
          formValues &&
          map(formValues, (field, key) => {
            return (
              <HStack key={`multivalue-${key}`} className="synri-child-value">
                <InputWithLabel
                  label={field.label}
                  datatype={
                    field.isMultiValueField ? AppConstants.INPUT_TYPE.MULTIVALUETEXT : getDatatype(field.datatype)
                  }
                  disabled={disabled}
                  onChange={(val: any) => onValueChange(field, val)}
                  value={field.value}
                  defaultValue={field.value}
                  renderType={field.renderType}
                  {...(field?.datatype === AppConstants.INPUT_TYPE.BOOLEAN ? { defaultChecked: field?.value } : {})}
                />
                {!disabled && (
                  <div className="synri-delete-container">
                    <Icon type="delete" theme="filled" onClick={() => onDelete(key)} />
                  </div>
                )}
              </HStack>
            );
          })
        }
      />
      {!disabled && (
        <div className="synri-multi-field-value-add-container">
          {fieldPicklistVisible && <Select optionData={picklistValues} onChange={onPicklistChange} />}
          <Button onClick={onAdd} type="primary">
            {tc('plus_add')}
          </Button>
        </div>
      )}
    </div>
  );
};

export default MultiFieldValue;
