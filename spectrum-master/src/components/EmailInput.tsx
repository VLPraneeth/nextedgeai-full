//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useState, useCallback } from 'react';
import Select, { SelectProps } from 'antd/lib/select';

import './EmailInput.less';

const TOKEN_SEPARATORS = [',', ';'];
const EMAIL_SEPARATORS = [',', ';', ' '];

export type EmailInputProps = Pick<SelectProps<string[]>, 'onChange' | 'value' | 'defaultValue' | 'placeholder'>;

const EmailInput = ({ onChange, value, defaultValue, placeholder }: EmailInputProps) => {
  const [currentInput, setCurrentInput] = useState('');

  const isTokenInput = useCallback((input: string) => {
    return input.includes('{{');
  }, []);

  const handleSearch = useCallback((searchText: string) => {
    setCurrentInput(searchText);
  }, []);

  const getSeparators = useCallback(() => {
    return isTokenInput(currentInput) ? TOKEN_SEPARATORS : EMAIL_SEPARATORS;
  }, [currentInput, isTokenInput]);

  return (
    <Select
      className="synri-email-input"
      defaultValue={defaultValue}
      value={value}
      dropdownMatchSelectWidth
      mode="tags"
      onChange={onChange}
      tokenSeparators={getSeparators()}
      placeholder={placeholder}
      onSearch={handleSearch}
      showSearch
    />
  );
};

export default EmailInput;
