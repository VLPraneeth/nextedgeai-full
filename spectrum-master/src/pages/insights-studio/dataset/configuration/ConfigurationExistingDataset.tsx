import InputWithLabel from 'components/inputs/InputWithLabel';
import SelectInput from 'components/SelectInput';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import { useGetDatasetsQuery } from 'store/insights-studio';
import { tNamespaced } from 'utils/i18nUtil';

import './ConfigurationExistingDataset.less';

const tn = tNamespaced('InsightsStudio');

export const ConfigurationExistingDataset = () => {
  const { isThoughtSpotView } = useInsightsViewContext();
  const { data: datasets } = useGetDatasetsQuery(isThoughtSpotView);
  const { preselectedDatasetId, setPreselectedDatasetId } = useDataCardAuthoringContext();
  const handleDataSetChange = (datasetId: string) => setPreselectedDatasetId(datasetId);

  const datasetOptions = datasets?.map((dataset) => ({ value: dataset.id, label: dataset.displayName })) ?? [];

  return (
    <div className="configuration-existing-dataset">
      <InputWithLabel
        label="Select an existing Data set"
        tooltip={tn('Tooltips.data_set')}
        input={
          <SelectInput
            className="configuration-existing-dataset__dataset-picker"
            value={preselectedDatasetId}
            options={datasetOptions}
            onChange={handleDataSetChange}
            showSearch
            filterOption={(input, option) => Boolean(option.props.children?.toString().toLowerCase().includes(input))}
          />
        }
      />
    </div>
  );
};
