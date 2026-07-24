import { Form } from 'antd';
import { OptionsStackingValue } from 'highcharts';
import { useEffect, useMemo, useState } from 'react';

import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import SelectInput from 'components/SelectInput';
import { getNumericColumns } from 'pages/insights-studio/utils/datacardConfigUtils';
import { CategoryValue, DataCardVizConfig, FieldConfig, SeriesConfig } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { AxisConfigInput } from './AxisConfigInput';
import { CategoryColorPicker } from './CategoryColorPicker';
import { blankDataCardConfig, VizTypeConfigFormProps } from './DataCardConfigStep';
import { FieldSplitter } from './FieldSplitter';
import { LegendPositionInput } from './LegendPositionInput';
import { StackingInput } from './StackingInput';
import { VariablesConfigForm } from './VariablesConfigForm';

const tn = tNamespaced('InsightsStudio');

export type StackingOptions = OptionsStackingValue;

export const GraphConfigForm = ({
  vizConfig,
  updateConfig,
  columnOptions,
  dataCard,
  dataset,
}: VizTypeConfigFormProps) => {
  const [activeTab, setActiveTab] = useState('setup');

  useEffect(() => {
    if (!vizConfig?.xaxis || !vizConfig?.yaxis) {
      // If either axis is null, config is invalid.
      ///Add blank axis objects to create valid configuration
      updateConfig({ ...vizConfig, xaxis: blankDataCardConfig.xaxis, yaxis: blankDataCardConfig.yaxis });
    }
  });

  // Y-axis can only use numeric values
  const yAxisOptions = useMemo(() => getNumericColumns(columnOptions), [columnOptions]);

  if (!vizConfig?.xaxis || !vizConfig?.yaxis) {
    // If either axis is null, config is invalid and form should not render.
    return null;
  }

  const setStacking = (stacking: string) => updateConfig({ ...vizConfig, stacking: stacking as OptionsStackingValue });
  const setXAxis = (xAxis: FieldConfig[]) => updateConfig({ ...vizConfig, xaxis: xAxis[0] });
  const setYAxis = (yaxis: FieldConfig[]) =>
    updateConfig({ ...{ ...vizConfig, ...{ series: yaxis?.length > 1 ? [] : vizConfig.series } }, yaxis });
  const setSeries = (series: SeriesConfig[]) => updateConfig({ ...vizConfig, series });
  const setLegend = (legendPosition: DataCardVizConfig['legendPosition']) =>
    updateConfig({ ...vizConfig, legendPosition });
  const setCategoryValues = (categoryValue: CategoryValue) => {
    if (vizConfig.series?.[0]?.column) {
      updateConfig({
        ...vizConfig,
        categoryValues: vizConfig.categoryValues?.map((catVal) => {
          if (catVal.name === categoryValue.name) {
            return categoryValue;
          }
          return catVal;
        }),
      });
    } else {
      updateConfig({
        ...vizConfig,
        yaxis: vizConfig.yaxis.map((yaxis) => {
          if (yaxis.column === categoryValue.name) {
            return {
              ...yaxis,
              color: categoryValue.color,
            };
          }
          return yaxis;
        }),
      });
    }
  };

  const setCategoryIntialValues = (categoryValues: CategoryValue[]) => {
    if (vizConfig.series?.[0]?.column) {
      updateConfig({
        ...vizConfig,
        categoryValues,
      });
    } else {
      updateConfig({
        ...vizConfig,
        yaxis: vizConfig.yaxis.map((yaxis) => {
          return {
            ...yaxis,
            color: categoryValues.find((val) => val.name === yaxis.column)?.color,
          };
        }),
      });
    }
  };

  const setVariables = (variablesMap: DataCardVizConfig['variablesMap']) =>
    updateConfig({ ...vizConfig, variablesMap });

  const onSelectSeries = (column: string) => {
    if (!column) {
      setSeries([]);
      return;
    }
    const selectedOption = columnOptions.find((opt) => opt.value === column);
    if (selectedOption) {
      setSeries([
        {
          column: selectedOption.value,
          name: selectedOption.value,
          displayName: selectedOption.value,
          displayFormat: 'text',
        },
      ]);
    }
  };

  return (
    <>
      <div className="data-card-config-step__tabs">
        <InlineTabs
          selectedTab={activeTab}
          onChange={(clickedTab) => {
            setActiveTab(clickedTab);
          }}>
          <InlineTab id="setup">{tc('setup')}</InlineTab>
          <InlineTab id="style">{tc('style')}</InlineTab>
          <InlineTab id="vars">{tc('variables')}</InlineTab>
        </InlineTabs>
      </div>
      <div>
        {activeTab === 'setup' && (
          <div>
            {vizConfig.vizType === 'BAR' ? (
              // Swap axis configs for bar graphs because highcharts  visually rotates them, making the y-axis horizontal
              // and x-axis veritcal
              <>
                <AxisConfigInput
                  title="X-Axis"
                  axisConfig={vizConfig.yaxis}
                  vizConfig={vizConfig}
                  onChange={setYAxis}
                  repeatable
                  options={yAxisOptions}
                  vizType={vizConfig.vizType}
                />
                <AxisConfigInput
                  title="Y-Axis"
                  axisConfig={[vizConfig.xaxis]}
                  onChange={setXAxis}
                  options={columnOptions}
                  vizType={vizConfig.vizType}
                />
              </>
            ) : (
              <>
                <AxisConfigInput
                  title="X-Axis"
                  axisConfig={[vizConfig.xaxis]}
                  onChange={setXAxis}
                  options={columnOptions}
                  vizType={vizConfig.vizType}
                />
                <AxisConfigInput
                  title="Y-Axis"
                  repeatable
                  vizConfig={vizConfig}
                  axisConfig={vizConfig.yaxis}
                  onChange={setYAxis}
                  options={yAxisOptions}
                  vizType={vizConfig.vizType}
                />
              </>
            )}
            <InputWithLabel
              label={tn('select_series')}
              tooltip={tn('select_seriels_tooltip')}
              input={
                <Form.Item>
                  <SelectInput
                    allowClear
                    disabled={vizConfig.yaxis?.length > 1}
                    value={vizConfig.series?.[0]?.column}
                    options={columnOptions}
                    onChange={onSelectSeries}
                  />
                </Form.Item>
              }
            />
            <StackingInput value={vizConfig.stacking ?? ''} onChange={setStacking} />
          </div>
        )}
        {activeTab === 'style' && (
          <div>
            <FieldSplitter label={tc('legends')} />
            <LegendPositionInput value={vizConfig.legendPosition} onChange={setLegend} />
            <FieldSplitter label={tc('variables')} />
            <CategoryColorPicker
              vizType={vizConfig.vizType}
              dataset={dataset}
              dataCard={dataCard}
              handleColorChange={setCategoryValues}
              setInitialValues={setCategoryIntialValues}
            />
          </div>
        )}

        {activeTab === 'vars' && (
          <VariablesConfigForm dataCardVariables={vizConfig.variablesMap} setVariables={setVariables} />
        )}
      </div>
    </>
  );
};
