//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import Select, { SelectProps } from 'antd/lib/select';
import { forwardRef } from 'react';

import { Text } from 'components/typography';
import AppConstants from 'utils/AppConstants';

import './MultiValueText.less';
import { DisplayMode } from './types';

interface MultiValueTextProps extends SelectProps {
  value?: string[];
  defaultValue?: string[];
  displayMode?: DisplayMode;
  optionsData?: { label: string; value: string }[];
}

const MultiValueText = forwardRef<HTMLDivElement, MultiValueTextProps>(
  ({ value, defaultValue, displayMode, optionsData, ...rest }, ref) => {
    if (displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY) {
      return <Text data-testid="readonly-multi-value-text">{(defaultValue || [])?.join(', ')}</Text>;
    }
    return (
      <div ref={ref} className="synri-multi-value-text">
        <Select
          mode="tags"
          defaultValue={Array.isArray(defaultValue) ? defaultValue : []}
          value={Array.isArray(value) ? value : []}
          tokenSeparators={[';']}
          {...rest}>
          {optionsData?.map(({ label, value }) => (
            <Select.Option key={value} value={value}>
              {label}
            </Select.Option>
          ))}
        </Select>
      </div>
    );
  }
);

export default MultiValueText;
