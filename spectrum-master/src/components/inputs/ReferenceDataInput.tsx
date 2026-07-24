//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Select, Tooltip } from 'antd';

import { useGetReferenceDataSetsQuery } from 'store/data-quality-v2/api';

const { Option } = Select;

interface ReferenceDataInputProps {
  value?: any;
  onChange?: (value: any) => void;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
  style?: React.CSSProperties;
}

const ReferenceDataInput = ({
  value,
  onChange,
  disabled = false,
  placeholder = 'Select reference data set...',
  className,
  style,
}: ReferenceDataInputProps) => {
  const { data: referenceDataSets } = useGetReferenceDataSetsQuery();

  const handleChange = (selectedValue: string) => {
    onChange?.(selectedValue);
  };

  return (
    <Select
      value={value}
      onChange={handleChange}
      disabled={disabled}
      placeholder={placeholder}
      className={className}
      style={style}
      showSearch>
      {referenceDataSets?.referenceDataSets?.map((refDataSet: any) => (
        <Option key={refDataSet.value} value={refDataSet.value}>
          <Tooltip title={refDataSet.label} placement="right">
            <span>{refDataSet.label}</span>
          </Tooltip>
        </Option>
      ))}
    </Select>
  );
};

export default ReferenceDataInput;
