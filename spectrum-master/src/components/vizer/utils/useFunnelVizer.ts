//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Chart, ChartEventsOptions, SeriesOptionsType } from 'highcharts';
import { useMemo } from 'react';

import { DataCardData, DataCardVizConfig, DataRow } from 'store/insights-studio/types';
import { Locale, LocaleStyle, numberFormat } from 'utils/i18nUtil';
import { colors, variables } from 'utils/LessConstants';

import { FunnelChart, FunnelPoint } from '../types';
import { formatValue } from './VizerDisplayFormatter';
import { VizerGraphColors } from './VizerGraphColors';

export interface Params {
  configuration: DataCardVizConfig;
  data: DataCardData;
}

export interface ReturnTypes {
  series: SeriesOptionsType[];
  tooltip: Highcharts.TooltipOptions;
  colors: string[];
  events: ChartEventsOptions;
}

/**
 * Custom hook to convert Syncari's API response to Highcharts-compatible
 * data format.
 *
 * For more info see
 * - https://api.highcharts.com/highcharts/series.pie
 */
export const useFunnelVizer = ({ configuration, data }: Params): ReturnTypes => {
  const seriesData = useMemo(() => {
    const measureColumnName = configuration.measure?.name || '';
    const dataFieldColumnName = configuration.dataField?.name || '';

    const dataLabels = {
      enabled: configuration.labelVisible ?? false,
      inside: configuration.labelPosition === 'INSIDE',
    };

    if (configuration.sortBy === 'stage') {
      const availableStages = (data?.rows || []).map((row) => ({
        name: String(row[dataFieldColumnName]),
        value: row[measureColumnName],
      }));

      const total = (configuration?.stages || []).map(extractStageName).reduce((acc, name) => {
        const stage = availableStages.find((s) => s.name === name);
        return acc + (stage ? Number(stage.value) : 0);
      }, 0);

      const stages = (configuration?.stages || []).map(extractStageName).reduce((acc, name) => {
        const stage = availableStages.find((s) => s.name === name);
        if (stage && stage.value) {
          acc.push({
            name: stage.name,
            y: Number(stage.value),
            dataLabels,
            percentageValue: (Number(stage.value) / total) * 100,
          });
        }
        return acc;
      }, [] as { name: string; y: number; dataLabels: { enabled: boolean; inside: boolean }; percentageValue: number }[]);

      if (configuration.displayAdditional === 'step_to_step_ratio') {
        return constructStagesForStepRatio(stages, total);
      }

      return stages;
    }

    const rows = data?.rows || [];

    const total = rows.reduce((acc, row) => {
      const measure = row[measureColumnName];
      return acc + Number(measure);
    }, 0);

    const stages = rows
      .map((row) => {
        const measure = row[measureColumnName];
        const dataField = row[dataFieldColumnName];
        return {
          name: String(dataField),
          y: measure,
          dataLabels,
          percentageValue: (measure / total) * 100,
        };
      })
      .sort((a, b) => {
        return b.y - a.y;
      });

    if (configuration.displayAdditional === 'step_to_step_ratio') {
      return constructStagesForStepRatio(stages, total);
    }

    return stages;
  }, [
    configuration.sortBy,
    configuration?.stages,
    data?.rows,
    configuration.dataField?.name,
    configuration.measure?.name,
    configuration.labelPosition,
    configuration.labelVisible,
    configuration.displayAdditional,
  ]);

  // For step ratio to handle label and hover behaviour.
  const events = useMemo(() => {
    return {
      load(this: Chart) {
        const chart = this;
        chart.series.forEach((series) => {
          series.points.forEach((point) => {
            if (point.name === '') {
              point.update(
                {
                  events: {
                    mouseOver() {
                      return false;
                    },
                  },
                },
                false
              );
            }
          });
        });
      },
      render(this: FunnelChart) {
        const chart = this;
        if (chart.renderedLabels) {
          chart.renderedLabels.forEach((label: Highcharts.SVGElement) => label.destroy());
          chart.renderedLabels.length = 0;
        } else {
          chart.renderedLabels = [];
        }

        chart?.series?.[0]?.points?.forEach((point: FunnelPoint) => {
          if (point?.options?.customPercentage) {
            const labelXPosition = point.plotX || 0;
            const labelYPosition = (point.plotY || 0) + 5; // Added 5 units to position it in the middle

            chart.renderedLabels?.push(
              chart.renderer
                .label(point.options.customPercentage, labelXPosition, labelYPosition)
                .css({
                  fontSize: variables.fontSizes.xs,
                })
                .add()
            );
            const label = chart.renderedLabels?.[chart.renderedLabels.length - 1];
            const { width: labelWidth = 0, height: labelHeight = 0 } = label?.getBBox() ?? {};
            label?.attr({
              zIndex: 3,
              x: labelXPosition - labelWidth / 2,
              y: labelYPosition - labelHeight / 2,
            });
          }
        });
      },
    };
  }, []);

  const series = [
    {
      name: configuration.dataField?.displayName || '',
      data: seriesData,
    },
  ] as SeriesOptionsType[];

  const colors = useMemo(() => {
    return VizerGraphColors.getShades(configuration?.colorTheme, seriesData?.length || 0);
  }, [configuration.colorTheme, seriesData?.length]);

  const tooltip = useMemo(() => {
    return {
      pointFormatter(this: FunnelPoint): string {
        const value = this.y && formatValue(configuration.measure?.displayFormat, this.y, this?.percentageValue);
        return `<span">&bull;${this.series.name}</span>: <b>${value}</b><br />`;
      },
      shared: true,
    };
  }, [configuration.measure?.displayFormat]);

  return {
    series,
    tooltip,
    colors,
    events,
  };
};

interface Stage {
  name: string;
  y: number;
  dataLabels: {
    enabled: boolean;
    inside: boolean;
  };
  percentageValue: number;
}

function constructStagesForStepRatio(stages: Stage[], total: number) {
  return stages.reduce((acc, stage, index, updatedRows) => {
    acc.push(stage);

    index !== stages.length - 1 &&
      acc.push({
        name: '',
        y: total / stages.length,
        color: colors.white,
        customPercentage: numberFormat(updatedRows[index + 1].y / stage.y, Locale.EN_US, LocaleStyle.PERCENT, 2),
        dataLabels: {
          enabled: false,
        },
      });

    return acc;
  }, [] as DataRow[]);
}

export function extractStageName(stage: string) {
  const lastIndex = stage.lastIndexOf('-');
  if (lastIndex === -1) {
    return stage;
  }
  return stage.substring(0, lastIndex);
}
