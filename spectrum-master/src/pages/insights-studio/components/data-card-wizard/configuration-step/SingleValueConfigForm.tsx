import { cloneDeep } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import { VizerDisplayFormat } from 'components/vizer/types';
import { defaultRange } from 'components/vizer/utils/useGaugeVizer';
import { VizerGraphColors } from 'components/vizer/utils/VizerGraphColors';
import { getNumericColumns } from 'pages/insights-studio/utils/datacardConfigUtils';
import { Range } from 'store/insights-studio/types';
import { tc } from 'utils/i18nUtil';

import { ColorPickerGrid } from './ColorPickerGrid';
import { ColumnInput } from './ColumnInput';
import { VizTypeConfigFormProps } from './DataCardConfigStep';
import { RangeInput } from './RangeInput';
import { VariablesConfigForm } from './VariablesConfigForm';

export const SingleValueConfigForm = ({
  vizConfig,
  updateConfig,
  columnOptions,
  setHasConfigError,
  hasConfigError,
}: VizTypeConfigFormProps) => {
  const localConfig = useMemo(() => {
    return cloneDeep(vizConfig);
  }, [vizConfig]);
  const [activeTab, setActiveTab] = useState('setup');
  const isGauge = vizConfig.vizType === 'GAUGE';
  const isMetric = vizConfig.vizType === 'METRIC';

  useEffect(() => {
    if (localConfig.columns && localConfig.columns.length > 1) {
      localConfig.columns = [localConfig.columns[0]];
      updateConfig(localConfig);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vizConfig.vizType]);

  useEffect(() => {
    if (!localConfig.columns || localConfig.columns.length < 1) {
      localConfig.columns = [{ name: '', displayName: '' }];
      updateConfig(localConfig);
    }
    if (isGauge && (!localConfig.ranges || localConfig.ranges.length < 1)) {
      localConfig.ranges = [defaultRange];
      updateConfig(localConfig);
    }
    if (isMetric && !localConfig.ranges) {
      localConfig.ranges = [];
      updateConfig(localConfig);
    }
  });

  useEffect(() => {
    const hasError = !isRangesValid(vizConfig.ranges);
    if (hasError !== hasConfigError) {
      setHasConfigError(hasError);
    }
    // hasConfigError and setHasConfigError are state and stateSet function from the parent.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vizConfig.ranges]);

  // Gauge can only use numeric values
  const gaugeColumnOptions = useMemo(() => getNumericColumns(columnOptions), [columnOptions]);

  const updateColumn = (columnName: string, columnIndex: number) => {
    const colToUpdate = localConfig.columns?.[columnIndex];
    const selectedOption = columnOptions.find((opt) => opt.value === columnName);
    if (colToUpdate) {
      colToUpdate.name = columnName;
      colToUpdate.displayName = columnName;
      colToUpdate.displayFormat =
        selectedOption?.dataType === 'integer' || selectedOption?.dataType === 'double' ? 'number' : 'text';
      updateConfig(localConfig);
    }
  };

  const updateFormat = (format: VizerDisplayFormat, columnIndex: number) => {
    const colToUpdate = localConfig.columns?.[columnIndex];
    if (colToUpdate) {
      colToUpdate.displayFormat = format;
      updateConfig(localConfig);
    }
  };

  const addRange = () => {
    const previousRange = localConfig.ranges?.[localConfig.ranges.length - 1];
    const min = previousRange?.maximumValue || defaultRange.minimumValue;
    const max = previousRange?.maximumValue ? min + 1 : defaultRange.maximumValue;
    localConfig.ranges?.push({ ...defaultRange, minimumValue: min, maximumValue: max });
    updateConfig(localConfig);
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
          {isMetric && <InlineTab id="style">{tc('style')}</InlineTab>}
          <InlineTab id="vars">{tc('variables')}</InlineTab>
        </InlineTabs>
      </div>
      {activeTab === 'setup' && (
        <>
          {vizConfig.columns?.map((column, i) => {
            return (
              <ColumnInput
                columnIndex={i}
                updateFormat={updateFormat}
                column={column}
                columnOptions={isGauge ? gaugeColumnOptions : columnOptions}
                updateColumn={updateColumn}
                allowRemove={false}
              />
            );
          })}

          {vizConfig.ranges?.map((range, index) => (
            <RangeInput
              rangeIndex={index}
              updateConfig={updateConfig}
              vizConfig={vizConfig}
              isNameRequired={isMetric}
            />
          ))}
          <Button type="link" onClick={addRange}>
            {tc('plus_add')} {tc('range')}
          </Button>
        </>
      )}
      {activeTab === 'vars' && (
        <VariablesConfigForm
          dataCardVariables={vizConfig.variablesMap}
          setVariables={(variables) => {
            localConfig.variablesMap = variables;
            updateConfig(localConfig);
          }}
        />
      )}
      {activeTab === 'style' && (
        <ColorPickerGrid
          onChange={(color) => {
            updateConfig({
              ...localConfig,
              columns:
                localConfig?.columns?.map((col) => ({
                  ...col,
                  color,
                })) || [],
            });
          }}
          color={VizerGraphColors.metricDefaultColor(localConfig.columns?.[0])}
          label={tc('color')}
        />
      )}
    </>
  );
};

function isRangesValid(ranges: Range[] | undefined) {
  if (!ranges) {
    return false;
  }

  let isMinMoreThanMax = false;
  for (const range of ranges) {
    if (range.minimumValue >= range.maximumValue) {
      isMinMoreThanMax = true;
    }
  }
  return !isMinMoreThanMax && findOverlappingRanges(ranges) === null;
}

export function findOverlappingRanges(ranges: Range[] | undefined) {
  if (!ranges) {
    return null;
  }

  for (let i = 0; i < ranges.length - 1; i++) {
    for (let j = i + 1; j < ranges.length; j++) {
      if (ranges[i].minimumValue < ranges[j].maximumValue && ranges[j].minimumValue < ranges[i].maximumValue) {
        return [ranges[i], ranges[j]] as const;
      }
    }
  }
  return null;
}
