//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import cx from 'classnames';
import { useCallback } from 'react';

import { HStack } from 'components/layout';
import { SkullItem } from 'components/skull';
import AppConstants from 'utils/AppConstants';

import InputContainer from './InputContainer';
import InputWithLabel from './InputWithLabel';
import { DisplayMode } from './types';

import './InputComposite.less';

export type InputCompositeValue = Record<string, string>;

export interface InputCompositeProps {
  configuration?: SkullItem[];
  onChange: (value: InputCompositeValue) => void;
  defaultValue?: InputCompositeValue;
  value?: InputCompositeValue;
  displayMode?: DisplayMode;
}

const InputComposite = ({ configuration, onChange, defaultValue, value, displayMode }: InputCompositeProps) => {
  const isReadonly = displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
  const Component = isReadonly ? InputContainer : InputWithLabel;

  const changeHandler = useCallback(
    (name: string, val: string) => {
      const newVal = {
        ...defaultValue,
        [name]: val,
      };
      onChange(newVal);
    },
    [defaultValue, onChange]
  );

  return (
    <HStack spacing="z" className={cx('input-composite', isReadonly && 'is-readonly')}>
      {configuration?.map((config) => {
        return (
          <>
            <Component
              {...config}
              displayMode={displayMode}
              value={value?.[config.name]}
              defaultValue={defaultValue?.[config.name]}
              onChange={(val: string) => changeHandler(config.name, val)}
            />
            {isReadonly && ' '}
          </>
        );
      })}
    </HStack>
  );
};

export default InputComposite;
