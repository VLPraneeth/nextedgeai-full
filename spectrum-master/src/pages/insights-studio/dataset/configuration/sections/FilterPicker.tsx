import { useMemo } from 'react';

import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { FilterValue } from 'components/inputs/types';
import { useEnhancedDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { flatDataSourceFields } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { fetchPicklistValues, FetchPicklistValuesParams } from 'store/picklists/thunks';
import AppConstants from 'utils/AppConstants';

import './FilterPicker.less';

const { INPUT_TYPE } = AppConstants;

export const FilterPicker = () => {
  const picklistValues = useSelector((state) => state.picklist.picklistValues);
  const { selectedDataSources, filter, setFilter } = useUnifiedDataCardAuthoring();
  const dispatch = useEnhancedDispatch();
  const { dataSourceFields } = useDatasetConfig();

  const values = useMemo(
    () =>
      flatDataSourceFields(dataSourceFields)?.map((field) => {
        return {
          ...field,
          value: field.fieldId || '',
          id: field.fieldId || '',
          renderType: AppConstants.INPUT_RENDER_TYPE.DATASET_VARIABLE_PICKER,
          datasourceAlias: field.datasourceAlias,
        };
      }),
    [dataSourceFields]
  );

  if (selectedDataSources?.length < 1) {
    return <InfoBox message="You need to select a data source to add filters." type="info" showIcon />;
  }

  return (
    <div className="filter-picker">
      <InputWithLabel
        name="filter"
        id="filter"
        datatype={INPUT_TYPE.PREDICATE}
        leftRenderType={AppConstants.INPUT_RENDER_TYPE.DATA_SOURCE_FIELD_PICKER}
        picklistValues={picklistValues}
        defaultValue={filter}
        values={values}
        fetchPicklistValues={(param: FetchPicklistValuesParams) => dispatch(fetchPicklistValues(param))}
        onChange={(name: string, id: string, value: FilterValue) => {
          setFilter(value);
        }}
      />
    </div>
  );
};
