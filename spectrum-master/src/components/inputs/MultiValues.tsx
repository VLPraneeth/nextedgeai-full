//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Icon, Input } from 'antd';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { each, values as ldValues, map } from 'lodash';
import * as React from 'react';
import { useEffect, useState } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack } from 'components/layout';
import { tc } from 'utils/i18nUtil';

import './MultiValues.less';

interface MultiValueProps {
  name: string;
  className?: string;
  label?: string;
  onChange?: (values: string[]) => void;
  vals?: string[];
  disabled?: boolean;
}

interface MultiValues {
  [k: string]: string;
}

const MultiValue = ({ name, onChange, label, className, vals, disabled }: MultiValueProps) => {
  const [values, setValues] = useState<MultiValues>({});

  useEffect(() => {
    const formValues: MultiValues = {};
    vals?.forEach((value: string, index: number) => {
      formValues[ObjectID.generate()] = value;
    });
    setValues(formValues);
  }, [vals]);

  const onAdd = () => {
    setValues({
      ...values,
      [ObjectID.generate()]: '',
    });
  };

  const onDelete = (id: string) => {
    const newValues: MultiValues = {};
    each(values, (v, k) => {
      if (k !== id) {
        newValues[k] = v;
      }
    });
    setValues(newValues);
    onChange && onChange(ldValues(newValues));
  };

  const onPicklistChange = (name: string, id: string, evt: React.ChangeEvent<HTMLInputElement>) => {
    const newValues = {
      ...values,
      [id]: evt.target.value,
    };
    setValues(newValues);
    onChange && onChange(ldValues(newValues));
  };

  return (
    <div className={cx('synri-multi-values', className)}>
      <InputWithLabel
        label={label}
        input={
          values &&
          map(values, (value: string, key: string) => {
            return (
              <HStack key={`multivalue-${key}`} className="synri-child-value">
                <Input disabled={disabled} onChange={onPicklistChange.bind(null, name, key)} value={value} />
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
        <Button type="link" onClick={onAdd}>
          {tc('plus_add')}
        </Button>
      )}
    </div>
  );
};

export default MultiValue;
