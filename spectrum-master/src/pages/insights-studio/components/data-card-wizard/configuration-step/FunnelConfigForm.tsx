import { Radio } from 'antd';
import ObjectID from 'bson-objectid';
import { useEffect, useMemo, useState } from 'react';

import Checkbox from 'components/Checkbox';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import SelectInput from 'components/SelectInput';
import { extractStageName } from 'components/vizer/utils/useFunnelVizer';
import { formatValue } from 'components/vizer/utils/VizerDisplayFormatter';
import { chartColors } from 'components/vizer/utils/VizerGraphColors';
import {
  CompositeInputValue,
  GroupCompositeValues,
} from 'pages/insights-studio/dataset/configuration/sections/GroupPicker';
import { getNumericColumns } from 'pages/insights-studio/utils/datacardConfigUtils';
import { usePreviewDataCardMutation } from 'store/insights-studio';
import { DataCardVizConfig, FieldConfig } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { AxisConfigInput } from './AxisConfigInput';
import { ColorThemeInput } from './ColorThemeInput';
import { blankDataCardConfig, VizTypeConfigFormProps } from './DataCardConfigStep';
import './FunnelConfigForm.scss';
import { FieldSplitter } from './FieldSplitter';
import { VariablesConfigForm } from './VariablesConfigForm';

const tn = tNamespaced('InsightsStudio');

export interface StagesCompositeValue {
  repeatId?: string;
  stages?: CompositeInputValue;
}

export type StagesCompositeValues = GroupCompositeValues<StagesCompositeValue>;

export const FunnelConfigForm = ({
  vizConfig,
  updateConfig,
  columnOptions,
  dataCard,
  dataset,
}: VizTypeConfigFormProps) => {
  const [activeTab, setActiveTab] = useState('setup');
  const [previewDataCard, { data: dataCardPreview }] = usePreviewDataCardMutation();

  useEffect(() => {
    if (
      dataCard.contents?.configuration.measure?.column &&
      dataCard?.contents?.configuration.dataField?.column &&
      dataset
    ) {
      previewDataCard({ datacard: dataCard, dataset });
    }
    // Only run once when both dataCard and dataset is non empty.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    dataCard?.contents?.configuration?.vizType,
    dataset?.id,
    dataCard?.contents?.configuration.measure?.column,
    dataCard?.contents?.configuration.dataField?.column,
  ]);

  useEffect(() => {
    if (!vizConfig?.measure || !vizConfig?.dataField) {
      updateConfig({
        ...vizConfig,
        measure: blankDataCardConfig.measure,
        dataField: blankDataCardConfig.dataField,
      });
    }
  });

  const sortOptions = useMemo(() => {
    return [
      {
        label: tn('values'),
        value: 'value',
      },
      {
        label: tn('stage'),
        value: 'stage',
      },
    ];
  }, []);

  const displayInformationOptions = useMemo(() => {
    return [
      {
        label: tc('labels'),
        value: 'labels',
      },
      {
        label: tc('legends'),
        value: 'legends',
      },
      {
        label: tn('step_to_step_ratio'),
        value: 'step_to_step_ratio',
      },
    ];
  }, []);

  const legendPositionOptions = useMemo(() => {
    return [
      {
        label: tc('left'),
        value: 'LEFT',
      },
      {
        label: tc('right'),
        value: 'RIGHT',
      },
    ];
  }, []);

  const labelPositionOptions = useMemo(() => {
    return [
      {
        label: tc('right'),
        value: 'RIGHT',
      },
      {
        label: tc('inside'),
        value: 'INSIDE',
      },
    ];
  }, []);

  const compositeConfiguration = useMemo(() => {
    const dataFieldColumn = vizConfig.dataField?.name || '';
    const measureColumn = vizConfig.measure?.name || '';

    const availableStages = (dataCardPreview?.contents.data.rows || []).map((row) => ({
      name: String(row[dataFieldColumn]),
      value: row[measureColumn],
    }));

    const options = dataCardPreview?.contents.data.rows.map((row) => {
      const measureValue = row[measureColumn];

      const total = (vizConfig?.stages || []).map(extractStageName).reduce((acc, name) => {
        const stage = availableStages.find((s) => s.name === name);
        return acc + (stage ? Number(stage.value) : 0);
      }, 0);

      const value =
        measureValue &&
        formatValue(vizConfig.measure?.displayFormat, measureValue, (Number(measureValue) / total) * 100);

      return {
        label: `${row[dataFieldColumn]} = ${value}`,
        value: `${row[dataFieldColumn]}-${row[measureColumn]}`,
      };
    });

    return [
      {
        id: 'stages',
        datatype: AppConstants.INPUT_TYPE.PICKLIST,
        name: 'stages',
        values: options,
      },
    ];
  }, [
    dataCardPreview?.contents.data.rows,
    vizConfig.dataField?.name,
    vizConfig.measure?.displayFormat,
    vizConfig.measure?.name,
    vizConfig?.stages,
  ]);

  const stagesCompositeValues = useMemo(() => {
    return {
      name: 'sort',
      compositeValues: vizConfig?.stages?.map((stage) => {
        return {
          repeatId: ObjectID.generate(),
          stages: {
            name: 'stages',
            value: stage,
          },
        };
      }),
    };
  }, [vizConfig?.stages]);

  // measure can only use numeric values
  const measureOptions = useMemo(() => getNumericColumns(columnOptions), [columnOptions]);

  const displayInfoAs = useMemo(() => {
    if (vizConfig.displayAdditional) {
      return vizConfig.displayAdditional;
    }
    if (vizConfig.labelVisible) {
      return 'labels';
    }
    if (vizConfig.legendVisible) {
      return 'legends';
    }
  }, [vizConfig.displayAdditional, vizConfig.labelVisible, vizConfig.legendVisible]);

  if (!vizConfig?.measure || !vizConfig?.dataField) {
    return null;
  }

  const setMeasue = (val: FieldConfig[]) => updateConfig({ ...vizConfig, measure: val?.[0] });
  const setDataField = (val: FieldConfig[]) => updateConfig({ ...vizConfig, dataField: val?.[0] });
  const setSortBy = (sortBy: DataCardVizConfig['sortBy']) => updateConfig({ ...vizConfig, sortBy });
  const setStages = (stages: string[]) => updateConfig({ ...vizConfig, stages });
  const setAscending = (ascending: DataCardVizConfig['ascending']) => updateConfig({ ...vizConfig, ascending });
  const setColorTheme = (colorTheme: string) => updateConfig({ ...vizConfig, colorTheme });
  const setLegendPosition = (legendPosition: DataCardVizConfig['legendPosition']) =>
    updateConfig({ ...vizConfig, legendPosition });
  const setLabelPosition = (labelPosition: DataCardVizConfig['labelPosition']) =>
    updateConfig({ ...vizConfig, labelPosition });
  const setDisplayInfoAs = (displayInfoAs: DataCardVizConfig['displayAdditional']) => {
    updateConfig({
      ...vizConfig,
      labelVisible: displayInfoAs === 'labels' || displayInfoAs === 'step_to_step_ratio',
      legendVisible: displayInfoAs === 'legends',
      displayAdditional: displayInfoAs,
    });
  };

  const setVariables = (variablesMap: DataCardVizConfig['variablesMap']) =>
    updateConfig({ ...vizConfig, variablesMap });

  const legendPosition = vizConfig.legendPosition === 'RIGHT' ? 'RIGHT' : 'LEFT';
  const labelPosition = vizConfig.labelPosition === 'INSIDE' ? 'INSIDE' : 'RIGHT';
  const sortBy = vizConfig.sortBy === 'stage' ? 'stage' : 'value';
  const ascending = vizConfig.ascending ?? false;
  const colorTheme = vizConfig.colorTheme ?? chartColors[0].color;

  return (
    <>
      <div className="data-card-config-step__tabs">
        <InlineTabs
          selectedTab={activeTab}
          onChange={(clickedTab) => {
            setActiveTab(clickedTab);
          }}>
          <InlineTab id="setup">{tc('setup')}</InlineTab>
          <InlineTab id="vars">{tc('variables')}</InlineTab>
          <InlineTab id="appearance">{tc('appearance')}</InlineTab>
        </InlineTabs>
      </div>
      <div>
        {activeTab === 'setup' && (
          <div>
            <AxisConfigInput
              title={tn('measure')}
              axisConfig={[vizConfig.measure]}
              onChange={setMeasue}
              options={measureOptions}
              vizType={vizConfig.vizType}
            />
            <AxisConfigInput
              title={tn('data_field')}
              showDisplayFormat={false}
              axisConfig={[vizConfig.dataField]}
              onChange={setDataField}
              options={columnOptions}
              vizType={vizConfig.vizType}
            />

            <InputWithLabel
              label={tn('sort_by')}
              input={
                <Radio.Group
                  value={sortBy}
                  onChange={(e) => {
                    setSortBy(e.target.value);
                  }}>
                  {sortOptions.map((option) => {
                    return (
                      <Radio key={option.value} value={option.value} className="funnel-config-form__radio">
                        {option.label}
                      </Radio>
                    );
                  })}
                </Radio.Group>
              }
            />

            {sortBy === 'value' && (
              <Checkbox
                checked={ascending}
                onChange={(e) => setAscending(e.target.checked)}
                className="funnel-config-form__checkbox">
                {tn('invert_order')}
              </Checkbox>
            )}

            {sortBy === 'stage' && vizConfig?.dataField?.name && (
              <InputWithLabel
                className="funnel-config-form__sort-container"
                name="sort"
                addText={tn('add_stage')}
                datatype={AppConstants.INPUT_TYPE.COMPOSITE}
                configuration={compositeConfiguration}
                repeatable={stagesCompositeValues?.compositeValues?.length !== 20}
                hideOrderNumber
                defaultValue={stagesCompositeValues}
                value={stagesCompositeValues}
                onChange={(stagesCompositeValues: StagesCompositeValues) => {
                  const stages =
                    stagesCompositeValues?.compositeValues?.map(
                      (compositeValue) => compositeValue?.stages?.value || ''
                    ) || [];
                  setStages(stages);
                }}
              />
            )}
          </div>
        )}
        {activeTab === 'vars' && (
          <VariablesConfigForm dataCardVariables={vizConfig.variablesMap} setVariables={setVariables} />
        )}
        {activeTab === 'appearance' && (
          <div>
            <FieldSplitter label={tn('additional_information')} />
            <InputWithLabel
              label={tn('display_additional')}
              className="funnel-config-form__display-additional"
              input={
                <SelectInput
                  value={displayInfoAs}
                  options={displayInformationOptions}
                  onChange={(value) => {
                    setDisplayInfoAs(value as DataCardVizConfig['displayAdditional']);
                  }}
                  allowClear
                />
              }
            />

            {displayInfoAs === 'legends' && (
              <InputWithLabel
                label={tn('legend_position')}
                input={
                  <Radio.Group
                    value={legendPosition}
                    onChange={(e) => {
                      setLegendPosition(e.target.value);
                    }}>
                    {legendPositionOptions.map((option) => {
                      return (
                        <Radio key={option.value} value={option.value} className="funnel-config-form__radio">
                          {option.label}
                        </Radio>
                      );
                    })}
                  </Radio.Group>
                }
              />
            )}

            {(displayInfoAs === 'labels' || displayInfoAs === 'step_to_step_ratio') && (
              <InputWithLabel
                label={tn('label_position')}
                input={
                  <Radio.Group
                    value={labelPosition}
                    onChange={(e) => {
                      setLabelPosition(e.target.value);
                    }}>
                    {labelPositionOptions.map((option) => {
                      return (
                        <Radio key={option.value} value={option.value} className="funnel-config-form__radio">
                          {option.label}
                        </Radio>
                      );
                    })}
                  </Radio.Group>
                }
              />
            )}

            <FieldSplitter label={tn('viz_theme')} />
            <ColorThemeInput value={colorTheme} onChange={setColorTheme} />
          </div>
        )}
      </div>
    </>
  );
};
