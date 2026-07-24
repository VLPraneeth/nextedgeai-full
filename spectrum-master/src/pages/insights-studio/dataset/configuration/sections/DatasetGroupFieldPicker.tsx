import cx from 'classnames';
import { useCallback } from 'react';

import InputContainer from 'components/inputs/InputContainer';
import { HStack } from 'components/layout';
import { useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import AppConstants from 'utils/AppConstants';

import { GroupInputValue } from './GroupPicker';

import './DatasetGroupFieldPicker.scss';

export interface DatasetGroupFieldPickerProps {
  defaultValue?: GroupInputValue;
  name: string;
  onChange?: (value?: GroupInputValue) => void;
}

export const DatasetGroupFieldPicker = ({ defaultValue, name, onChange }: DatasetGroupFieldPickerProps) => {
  const { sortDataSourceFields } = useDataSourceFields({ searchText: '' });

  const onFieldChange = useCallback(
    (fieldId: string) => {
      const fField = sortDataSourceFields.find((field) => field.id === fieldId);

      if (fField) {
        onChange?.({
          field: {
            fieldId: fField.id,
            apiName: fField.apiName,
            dataType: fField.dataType,
            datasetId: fField.datasetId,
            displayName: fField.displayName,
            datasetType: fField.datasetType,
            alias: fField.alias,
            datasourceAlias: fField.datasourceAlias,
            type: 'variable',
          },
          name,
        });
      }
    },
    [name, onChange, sortDataSourceFields]
  );

  return (
    <HStack className={cx('dataset-group-field-picker')}>
      <InputContainer
        name="field"
        optionsKey="group"
        disabled
        renderType={AppConstants.INPUT_RENDER_TYPE.DATA_SOURCE_FIELD_PICKER}
        defaultValue={{
          id: defaultValue?.field?.fieldId || '',
          value: defaultValue?.field?.fieldId || '',
          datasourceAlias: defaultValue?.field?.datasourceAlias,
        }}
        onChange={onFieldChange}
      />
    </HStack>
  );
};
