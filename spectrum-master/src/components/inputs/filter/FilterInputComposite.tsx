//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import produce from 'immer';
import { useCallback, useEffect, useMemo } from 'react';

import { SkullItem } from 'components/skull';
import { usePicklistValues } from 'store/picklists/hooks';

import InputComposite, { InputCompositeValue } from '../InputComposite';
import { OperatorValue } from '../types';

export interface FilterInputCompositeProps {
  operatorValue: OperatorValue;
  dependantId?: string;
  onChange: (value: InputCompositeValue) => void;
  [k: string]: any;
}

const FilterInputComposite = ({ operatorValue, onChange, dependantId, ...rest }: FilterInputCompositeProps) => {
  const [picklistValues, fetchPicklistValues] = usePicklistValues();

  const makePicklistKey = useCallback(
    (config: SkullItem) => {
      let key = '';
      if (config.dependantType && config.dependantId && rest[config.dependantId]) {
        key = `${config.dependantType}${rest[config.dependantId].value}`;
      }
      return key;
    },
    [rest]
  );

  useEffect(() => {
    if (operatorValue?.configuration) {
      operatorValue?.configuration.forEach((config) => {
        const picklistKey = makePicklistKey(config);
        if (picklistKey && !picklistValues[picklistKey] && config?.dependantId && rest[config.dependantId]?.value) {
          fetchPicklistValues({
            id: picklistKey,
            dependantType: config.dependantType,
            dependantId: rest[config.dependantId]?.value,
          });
        }
      });
    }
  }, [fetchPicklistValues, makePicklistKey, operatorValue?.configuration, picklistValues, rest]);

  const configuration = useMemo(() => {
    if (!operatorValue?.configuration) {
      return;
    }
    return produce(operatorValue.configuration, (config) => {
      config?.forEach((conf) => {
        const values = picklistValues[makePicklistKey(conf)];
        if (values?.length) {
          conf.values = values;
        }
      });
    });
  }, [makePicklistKey, operatorValue?.configuration, picklistValues]);

  return <InputComposite {...rest} configuration={configuration} onChange={onChange} />;
};

export default FilterInputComposite;
