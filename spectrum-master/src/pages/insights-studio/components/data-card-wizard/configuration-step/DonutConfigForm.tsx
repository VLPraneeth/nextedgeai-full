import { Radio } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { getNumericColumns } from 'pages/insights-studio/utils/datacardConfigUtils';
import { CategoryValue, DataCardVizConfig, FieldConfig } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { AxisConfigInput } from './AxisConfigInput';
import { CategoryColorPicker } from './CategoryColorPicker';
import { blankDataCardConfig, VizTypeConfigFormProps } from './DataCardConfigStep';
import './DonutConfigForm.scss';
import { FieldSplitter } from './FieldSplitter';
import { MinimumValueInput } from './MinimumValueInput';
import { VariablesConfigForm } from './VariablesConfigForm';

const tn = tNamespaced('InsightsStudio');

export const DonutConfigForm = ({
  vizConfig,
  updateConfig,
  columnOptions,
  dataCard,
  dataset,
}: VizTypeConfigFormProps) => {
  const [activeTab, setActiveTab] = useState('setup');

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
    ] as const;
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
    ] as const;
  }, []);

  useEffect(() => {
    if (!vizConfig?.category || !vizConfig?.value || !vizConfig.subCategory) {
      updateConfig({
        ...vizConfig,
        category: blankDataCardConfig.category,
        subCategory: blankDataCardConfig.subCategory,
        value: blankDataCardConfig.value,
        minimumValue: blankDataCardConfig.minimumValue,
      });
    }
  });

  // value can only use numeric values
  const valueOptions = useMemo(() => getNumericColumns(columnOptions), [columnOptions]);

  if (!vizConfig?.category || !vizConfig?.value || !vizConfig.subCategory) {
    return null;
  }

  const setCategory = (val?: FieldConfig[]) => updateConfig({ ...vizConfig, category: val?.[0] });
  const setSubCategory = (val?: FieldConfig[]) => updateConfig({ ...vizConfig, subCategory: val?.[0] });
  const setValue = (val?: FieldConfig[]) => updateConfig({ ...vizConfig, value: val?.[0] });
  const setMinimumValue = (minimumValue: DataCardVizConfig['minimumValue']) =>
    updateConfig({ ...vizConfig, minimumValue });
  const setLegendPosition = (legendPosition: DataCardVizConfig['legendPosition']) =>
    updateConfig({ ...vizConfig, legendPosition });
  const setDisplayInfoAs = (displayInfoAs: typeof displayInformationOptions[number]['value']) => {
    updateConfig({
      ...vizConfig,
      labelVisible: displayInfoAs === 'labels',
      legendVisible: displayInfoAs === 'legends',
    });
  };

  const setCategoryValues = (categoryValue: CategoryValue) =>
    updateConfig({
      ...vizConfig,
      categoryValues: vizConfig.categoryValues?.map((catVal) => {
        if (catVal.name === categoryValue.name) {
          return categoryValue;
        }
        return catVal;
      }),
    });

  const setCategoryIntialValues = (categoryValues: CategoryValue[]) =>
    updateConfig({
      ...vizConfig,
      categoryValues,
    });

  const setVariables = (variablesMap: DataCardVizConfig['variablesMap']) =>
    updateConfig({ ...vizConfig, variablesMap });

  const displayInfoAs = vizConfig.legendVisible ? 'legends' : 'labels';
  const legendPosition = vizConfig.legendPosition === 'RIGHT' ? 'RIGHT' : 'LEFT';

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
          <InlineTab id="appearance">{tc('appearance')}</InlineTab>
        </InlineTabs>
      </div>
      <div>
        {activeTab === 'setup' && (
          <div>
            <AxisConfigInput
              title={tc('categories')}
              axisConfig={[vizConfig.category]}
              onChange={setCategory}
              showDisplayFormat={false}
              options={columnOptions}
              vizType={vizConfig.vizType}
            />
            <AxisConfigInput
              disabled={!vizConfig.category.column}
              title={tc('sub_categories')}
              showDisplayFormat={false}
              axisConfig={[vizConfig.subCategory]}
              onChange={setSubCategory}
              options={columnOptions}
              vizType={vizConfig.vizType}
            />
            <AxisConfigInput
              title={tc('value')}
              axisConfig={[vizConfig.value]}
              onChange={setValue}
              options={valueOptions}
              vizType={vizConfig.vizType}
            />
            <MinimumValueInput minimumValue={vizConfig.minimumValue} onChange={setMinimumValue} />
          </div>
        )}
        {activeTab === 'vars' && (
          <VariablesConfigForm dataCardVariables={vizConfig.variablesMap} setVariables={setVariables} />
        )}
        {activeTab === 'appearance' && (
          <div>
            <FieldSplitter label={tn('additional_information')} />
            <InputWithLabel
              label={tn('display_info_as')}
              input={
                <Radio.Group
                  value={displayInfoAs}
                  onChange={(e) => {
                    setDisplayInfoAs(e.target.value);
                  }}>
                  {displayInformationOptions.map((option) => {
                    return (
                      <Radio key={option.value} value={option.value} className="donut-config-form__radio">
                        {option.label}
                      </Radio>
                    );
                  })}
                </Radio.Group>
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
                        <Radio key={option.value} value={option.value} className="donut-config-form__radio">
                          {option.label}
                        </Radio>
                      );
                    })}
                  </Radio.Group>
                }
              />
            )}
          </div>
        )}
        {activeTab === 'style' && (
          <>
            <FieldSplitter label={tc('variables')} />
            <CategoryColorPicker
              vizType="PIE"
              dataset={dataset}
              dataCard={dataCard}
              handleColorChange={setCategoryValues}
              setInitialValues={setCategoryIntialValues}
            />
          </>
        )}
      </div>
    </>
  );
};
