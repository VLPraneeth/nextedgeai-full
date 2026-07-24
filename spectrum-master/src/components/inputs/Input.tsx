import AInput, { InputProps as AInputProps } from 'antd/lib/input';
import cx from 'classnames';
import * as React from 'react';

import { ReactComponent as XIcon } from 'assets/icons/remove-x.svg';
import AppConstants from 'utils/AppConstants';

import { DisplayMode } from './types';
import './Input.less';

export interface InputProps extends AInputProps {
  displayMode?: DisplayMode;
  onClear?: () => void;
}

export type InputRef = Pick<HTMLInputElement, 'blur' | 'focus'>;

const Input = React.forwardRef<InputRef, InputProps>(({ displayMode, allowClear, onClear, ...rest }, ref) => {
  if (displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY) {
    return <span className={rest.className}>{rest.value}</span>;
  }

  const handleClearInput = () => {
    if (onClear) {
      onClear();
    } else {
      rest.onChange?.({
        target: {
          name: rest.name,
          id: rest.id,
          value: '',
        },
      } as React.ChangeEvent<HTMLInputElement>);
    }
  };

  return (
    <div className={cx('synri-text-input-container', allowClear && 'allow-clear')}>
      <AInput
        // @ts-ignore: odd antd typing here
        ref={ref}
        {...rest}
      />
      {allowClear && (
        <div
          className={cx('synri-text-input-clear', rest.value && 'is-visible')}
          role="button"
          onClick={handleClearInput}>
          <XIcon />
        </div>
      )}
    </div>
  );
});

export default Input;
