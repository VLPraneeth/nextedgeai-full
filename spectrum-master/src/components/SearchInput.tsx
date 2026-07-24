import { Icon, Input } from 'antd';
import { ChangeEvent } from 'react';

import './SearchInput.scss';
import { tc } from 'utils/i18nUtil';

export interface SearchInputProps {
  onChange: (value: ChangeEvent<HTMLInputElement>) => void;
  defaultValue?: string;
  placeholder?: string;
}
export const SearchInput = ({ defaultValue, onChange, placeholder }: SearchInputProps) => {
  return (
    <div className="search-input">
      <Icon className="search-input__icon" type="search" />
      <Input
        className="search-input__search"
        defaultValue={defaultValue}
        onChange={onChange}
        placeholder={placeholder || tc('search_placeholder')}
      />
    </div>
  );
};
