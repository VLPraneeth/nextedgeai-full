//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Spin, Icon } from 'antd';
import Select, { SelectProps, OptionProps as AntOptionProps } from 'antd/lib/select';

import './AutoSuggest.less';

const { Option } = Select;

export interface AutoSuggestOptionProps extends AntOptionProps {
  text: string;
}

export interface AutoSuggestProps extends SelectProps {
  fetching?: boolean;
  data: AutoSuggestOptionProps[];
  placeholder?: string;
}

function AutoSuggest({ data, fetching, value, onChange, onSearch, defaultValue, placeholder }: AutoSuggestProps) {
  return (
    <Select
      dropdownMatchSelectWidth
      mode="multiple"
      suffixIcon={<Icon type="tag" style={{ color: '#AAB6BE' }} />}
      className="add-tag-input"
      placeholder={placeholder || 'Select tags…'}
      notFoundContent={fetching ? <Spin size="small" /> : null}
      filterOption={false}
      onSearch={onSearch}
      onChange={onChange}
      value={value}
      defaultValue={defaultValue}>
      {data.map((d) => (
        <Option key={d.value}>{d.text}</Option>
      ))}
    </Select>
  );
}

export default AutoSuggest;
