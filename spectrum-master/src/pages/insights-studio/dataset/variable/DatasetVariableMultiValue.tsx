import ASelect from 'antd/lib/select';
import { useMemo, useState } from 'react';

import { FieldDataType } from 'components/types';
import { GroupPicklistSourceField, useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import './DatasetVariableMultiValue.scss';

export interface DatasetVariableMultiValueProps {
  onChange?: (value: string[]) => void;
  defaultValue?: string[];
  value?: string[];
}

export const DatasetVariableMultiValue = ({ onChange, defaultValue, value }: DatasetVariableMultiValueProps) => {
  const [searchText, setSearchText] = useState('');
  const { getSelectOptions } = useDataSourceFields({ searchText });
  const { variables } = useUnifiedDataCardAuthoring();

  const variableOptions = useMemo(() => {
    const variOptions: GroupPicklistSourceField[] =
      variables
        ?.filter((variable) => {
          return (
            variable.apiName &&
            (variable.apiName.toLowerCase().includes(searchText?.toLowerCase()) ||
              variable.displayName.toLowerCase().includes(searchText?.toLowerCase()))
          );
        })
        .map((variable) => {
          return {
            apiName: variable.apiName || '',
            dataType: variable.datatype as FieldDataType,
            datasetId: variable.datasetId || '',
            datasetType: 'DATASET',
            displayName: variable.displayName,
            id: variable.apiName || '',
            label: variable.displayName,
            picklistGroup: 'Variables',
            type: 'variable',
            value: `{{${variable.apiName}}}` || '',
          };
        }) || [];

    return getSelectOptions(variOptions);
  }, [getSelectOptions, searchText, variables]);

  return (
    <div className="data-set-variable-multi-value">
      <ASelect
        className="data-set-variable-multi-value__select"
        mode="tags"
        onChange={onChange}
        value={defaultValue}
        onSearch={(text) => setSearchText(text?.trim())}
        tokenSeparators={[';']}
        // Alway perform our own search
        filterOption={() => true}>
        {variableOptions}
      </ASelect>
    </div>
  );
};
