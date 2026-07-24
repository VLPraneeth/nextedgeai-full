import { Form, Radio } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import Checkbox from 'components/Checkbox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import TokenTextArea from 'components/inputs/tokens';
import { Stack } from 'components/layout';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import SelectInput from 'components/SelectInput';
import { FieldDataType } from 'components/types';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { makeStringToken } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useGetDatasetFromQueryMutation, useGetDatasetsQuery, useGetVariableQuery } from 'store/insights-studio';
import { DataCardVizConfig, Dataset, UnsavedDataCard, Position, VizType } from 'store/insights-studio/types';
import { Token } from 'store/tokens/types';
import { tc, tNamespaced, t } from 'utils/i18nUtil';
import { DeepPartial } from 'utils/TypeUtils';

import { DataCardPreview } from './DataCardPreview';
import { DonutConfigForm } from './DonutConfigForm';
import { FunnelConfigForm } from './FunnelConfigForm';
import { GraphConfigForm } from './GraphConfigForm';
import { SingleValueConfigForm } from './SingleValueConfigForm';
import { TableConfigForm } from './TableConfigForm';

import './DataCardConfigStep.scss';

const tn = tNamespaced('InsightsStudio');

const VizTypeConfigFormMap = {
  BAR: GraphConfigForm,
  LINE: GraphConfigForm,
  COLUMN: GraphConfigForm,
  PIE: DonutConfigForm,
  TABLE: TableConfigForm,
  METRIC: SingleValueConfigForm,
  GAUGE: SingleValueConfigForm,
  FUNNEL: FunnelConfigForm,
};

export interface VizConfigColumnOption {
  value: string;
  label: string;
  dataType?: FieldDataType;
}

export interface VizTypeConfigFormProps {
  vizConfig: DataCardVizConfig;
  updateConfig: (newConfig: DataCardVizConfig) => void;
  columnOptions: VizConfigColumnOption[];
  setHasConfigError: (hasError: boolean) => void;
  hasConfigError: boolean;
  dataCard: UnsavedDataCard;
  dataset?: DeepPartial<Dataset>;
}

export interface DataCardConfigStepProps {
  onCancel: () => void;
  onPrevious: () => void;
  onSuccess?: () => void;
}

export const DataCardConfigStep = ({ onCancel, onPrevious, onSuccess }: DataCardConfigStepProps) => {
  const { data: datasets } = useGetDatasetsQuery();
  const {
    isUnifiedWizard,
    dataCardWithNewDataset,
    getDataCardColumnOptions,
    getDatasetAndDataCard,
    dataCardVizConfig,
    setDataCardVizConfig,
    getDatasetPayload,
  } = useUnifiedDataCardAuthoring();

  const authoringContextProp = useUnifiedDataCardAuthoringContext();

  const [getDatasetFromQuery, { data: datasetFromQuery }] = useGetDatasetFromQueryMutation();

  const { newCardInfo, preselectedDatasetId, selectedDataCard } = useDataCardAuthoringContext();

  const [vizConfig, setVizConfig] = useState<DataCardVizConfig>(dataCardVizConfig || blankDataCardConfig);

  const [hasConfigError, setHasConfigError] = useState(false);

  const cardTypeOptions = [
    { value: 'COLUMN', label: tn('bar') },
    { value: 'BAR', label: tn('horizontal_bar') },
    { value: 'LINE', label: tn('line') },
    { value: 'METRIC', label: tn('metric') },
    { value: 'TABLE', label: tn('table') },
    { value: 'PIE', label: tn('donut') },
    { value: 'GAUGE', label: tn('gauge') },
    { value: 'FUNNEL', label: tn('funnel') },
  ];

  // Update the dataset id and vizConfig
  useEffect(() => {
    if (!dataCardWithNewDataset && preselectedDatasetId && preselectedDatasetId !== vizConfig.datasetId) {
      if (selectedDataCard) {
        setVizConfig({
          ...(selectedDataCard.contents?.configuration ?? blankDataCardConfig),
          datasetId: preselectedDatasetId,
        });
      } else {
        setVizConfig({ ...vizConfig, datasetId: preselectedDatasetId });
      }
    }
  }, [vizConfig, preselectedDatasetId, selectedDataCard, dataCardWithNewDataset]);

  const datasetOptions =
    datasets?.map((dataset) => {
      return { value: dataset.id, label: dataset.displayName };
    }) ?? [];

  let columnOptions: VizConfigColumnOption[] = useMemo(() => {
    // TODO: Everything is unified. Remove the isUnified during the edit datacard story
    if (isUnifiedWizard && dataCardWithNewDataset) {
      if (authoringContextProp.configMode === 'BASIC') {
        return getDataCardColumnOptions();
      } else if (authoringContextProp.configMode === 'SQL') {
        const columns: VizConfigColumnOption[] = [];

        datasetFromQuery?.datasetConfig.selectedFields?.forEach((field) => {
          columns.push({
            label: field.alias || field.displayName || field.apiName,
            value: field.alias || field.displayName || field.apiName,
            dataType: field.dataType,
          });
        });
        return columns;
      }
      return [];
    } else {
      const columns: VizConfigColumnOption[] = [];
      const dataset = datasets?.find((ds) => ds.id === vizConfig.datasetId);
      if (dataset) {
        dataset?.datasetConfig?.calculatedFields?.forEach((projection) => {
          columns.push({
            label: projection.aliasName ?? '',
            value: projection.aliasName ?? '',
            dataType: projection.dataType,
          });
        });
        dataset?.datasetConfig?.selectedFields?.forEach((field) => {
          columns.push({
            value: field.alias || field.displayName || field.apiName,
            label: field.alias || field.displayName || field.apiName,
            dataType: field.dataType || undefined,
          });
        });
      }
      return columns;
    }
  }, [
    authoringContextProp.configMode,
    dataCardWithNewDataset,
    datasetFromQuery?.datasetConfig.selectedFields,
    datasets,
    getDataCardColumnOptions,
    isUnifiedWizard,
    vizConfig.datasetId,
  ]);

  useEffect(() => {
    if (authoringContextProp.configMode === 'SQL') {
      const { displayName, apiName: name, description, tags, datasetId } = authoringContextProp;
      const basicInfo = { displayName, name, description, tags, id: datasetId };
      const dataset = getDatasetPayload(basicInfo) as Dataset;
      getDatasetFromQuery(dataset);
    }
  }, [getDatasetFromQuery, authoringContextProp, getDatasetPayload]);

  // Update the context vizConfig each time there's an update
  useEffect(() => {
    setDataCardVizConfig(vizConfig);
  }, [setDataCardVizConfig, vizConfig]);

  const handleDataSetChange = (datasetId: string) => {
    setVizConfig({ ...blankDataCardConfig, vizType: vizConfig.vizType, datasetId });
  };

  const saveConfiguration = () => {
    if (!vizConfig.vizType) {
      return;
    }
    onSuccess?.();
  };

  const labelPositionOptions = useMemo(() => {
    return [
      {
        label: tn('top'),
        value: 'TOP',
      },
      {
        label: tn('bottom'),
        value: 'BOTTOM',
      },
    ] as const;
  }, []);

  const VizTypeConfigForm = VizTypeConfigFormMap[vizConfig.vizType!];

  const previewDataCardConfig = {
    ...newCardInfo!,
    contents: {
      configuration: {
        ...vizConfig,
      },
    },
  };

  const dataset = useMemo(() => {
    if (dataCardWithNewDataset) {
      return getDatasetAndDataCard()?.dataset;
    }
    const datasetId = dataCardVizConfig?.datasetId;
    if (datasetId) {
      return datasets?.find((set) => set.id === vizConfig?.datasetId);
    }
  }, [dataCardVizConfig?.datasetId, dataCardWithNewDataset, datasets, getDatasetAndDataCard, vizConfig?.datasetId]);

  const { variables: unifiedDatasetVariables } = useUnifiedDataCardAuthoring();
  const { data: existingDatasetVariables, isLoading: isVariableLoading } = useGetVariableQuery({
    datasetId: preselectedDatasetId,
  });

  const [datasetVariables, providedTokens] = useMemo(() => {
    if (isVariableLoading) {
      return [];
    }
    const datasetVariables = dataCardWithNewDataset ? unifiedDatasetVariables : existingDatasetVariables || [];
    const suppliedTokens = datasetVariables?.map((variable) => {
      return {
        datatype: variable.datatype as FieldDataType,
        group: '',
        label: variable.displayName,
        shortLabel: variable.displayName,
        token: makeStringToken(variable.apiName),
        value: makeStringToken(variable.apiName),
      };
    });

    const providedTokens: Record<string, Token[]> | undefined = suppliedTokens ? { suppliedTokens } : undefined;
    return [datasetVariables, providedTokens];
  }, [dataCardWithNewDataset, existingDatasetVariables, isVariableLoading, unifiedDatasetVariables]);

  return (
    <>
      <div className="data-card-config-step">
        <ScrollableArea className="data-card-config-step__column data-card-config-step__column--left" bottomOffset={52}>
          <form id="data-card-form">
            {!isUnifiedWizard && (
              <InputWithLabel
                label={tn('dataset')}
                tooltip={tn('Tooltips.data_set')}
                input={
                  <Form.Item>
                    <SelectInput
                      id="dataset-id"
                      value={vizConfig.datasetId}
                      options={datasetOptions}
                      onChange={handleDataSetChange}
                      disabled={!!preselectedDatasetId}
                      showSearch
                      filterOption={(input, option) =>
                        Boolean(option.props.children?.toString().toLowerCase().includes(input))
                      }
                    />
                  </Form.Item>
                }
              />
            )}
            <InputWithLabel
              label={tn('viz_type')}
              tooltip={tn('Tooltips.viz_type')}
              input={
                <Form.Item>
                  <SelectInput
                    id="card-type"
                    value={vizConfig.vizType ?? ''}
                    options={cardTypeOptions}
                    onChange={(selectedType) => setVizConfig({ ...vizConfig, vizType: selectedType as VizType })}
                    showSearch
                    filterOption={(input, option) =>
                      Boolean(option.props.children?.toString().toLowerCase().includes(input))
                    }
                  />
                </Form.Item>
              }
            />
            <Stack spacing="sm">
              {/* Workaround TokenTextArea value behaves like default value. Only render it when our vizType is initialized */}
              {(!selectedDataCard || (vizConfig.vizType && selectedDataCard)) && !isVariableLoading && (
                <InputWithLabel
                  label={tc('label')}
                  input={
                    <TokenTextArea
                      value={vizConfig.vizLabel}
                      suppliedTokens={providedTokens || {}}
                      showTokenSelector={Boolean(datasetVariables?.length)}
                      tokenSelectorLabel={t('Dataset.VariablePicker.variable')}
                      onChange={(evt) => setVizConfig({ ...vizConfig, vizLabel: evt.target.value })}
                    />
                  }
                />
              )}
              <Checkbox
                checked={vizConfig.vizLabelVisible || false}
                onChange={(evt) => {
                  if (!vizConfig.vizLabelPosition) {
                    vizConfig.vizLabelPosition = 'TOP';
                  }
                  setVizConfig({ ...vizConfig, vizLabelVisible: evt.target.checked });
                }}>
                {tn('show_label')}
              </Checkbox>
              <InputWithLabel
                label={tn('label_location')}
                input={
                  <Radio.Group
                    className="data-card-config-step__label-location"
                    value={vizConfig.vizLabelPosition}
                    onChange={(evt) => setVizConfig({ ...vizConfig, vizLabelPosition: evt.target.value as Position })}>
                    {labelPositionOptions.map((option) => {
                      return (
                        <Radio key={option.value} value={option.value}>
                          {option.label}
                        </Radio>
                      );
                    })}
                  </Radio.Group>
                }
              />
            </Stack>

            {columnOptions && VizTypeConfigForm && (
              <VizTypeConfigForm
                setHasConfigError={(hasError) => setHasConfigError(hasError)}
                hasConfigError={hasConfigError}
                vizConfig={vizConfig}
                updateConfig={setVizConfig}
                columnOptions={columnOptions}
                dataCard={previewDataCardConfig}
                dataset={dataset}
              />
            )}
          </form>
        </ScrollableArea>

        <div className="data-card-config-step__column">
          {/* @ts-expect-error dataset missing id */}
          <DataCardPreview dataCard={previewDataCardConfig} dataset={dataset} />
        </div>
      </div>
      <div className="synri-drawer-panel__footer">
        <Button onClick={onCancel}>{tc('close')}</Button>
        <Button form="data-card-form" onClick={onPrevious}>
          {tc('previous')}
        </Button>
        <Button type="primary" onClick={saveConfiguration} disabled={hasConfigError}>
          {tc('next')}
        </Button>
      </div>
    </>
  );
};

export const blankDataCardConfig: DataCardVizConfig = {
  datasetId: '',
  vizType: undefined,
  vizLabel: '',
  vizLabelVisible: false,
  legendPosition: 'BOTTOM',
  labelVisible: true,

  xaxis: {
    column: '',
    name: '',
    displayName: '',
    displayFormat: 'text',
  },
  yaxis: [
    {
      column: '',
      name: '',
      displayName: '',
      displayFormat: 'number',
    },
  ],
  category: {
    column: '',
    name: '',
    displayName: '',
    displayFormat: 'text',
  },
  subCategory: {
    column: '',
    name: '',
    displayName: '',
    displayFormat: 'text',
  },
  value: {
    column: '',
    name: '',
    displayName: '',
    displayFormat: 'number',
  },
  minimumValue: {
    label: '',
    applyToSubCategories: false,
    value: undefined,
  },
  columns: [
    {
      name: '',
      displayName: '',
      displayFormat: 'text',
    },
  ],
  measure: {
    column: '',
    name: '',
    displayName: '',
    displayFormat: 'number',
  },
  dataField: {
    column: '',
    name: '',
    displayName: '',
    displayFormat: 'text',
  },
  sortBy: 'value',
  categoryValues: [],

  variablesMap: {} as DataCardVizConfig['variablesMap'],
};
