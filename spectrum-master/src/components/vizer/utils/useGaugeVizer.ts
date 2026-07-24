import Highcharts, { SeriesOptionsType } from 'highcharts';
import { useMemo } from 'react';

import { DataCardData, DataCardVizConfig, Range } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';
import { colors } from 'utils/LessConstants';
import { isNotNullOrUndefined } from 'utils/TypeUtils';

import { displayFormatter } from './VizerDisplayFormatter';
import { chartColors, VizerGraphColors } from './VizerGraphColors';

const tn = tNamespaced('InsightsStudio');

export interface Params {
  configuration: DataCardVizConfig;
  data: DataCardData;
}

export interface ReturnTypes {
  series: SeriesOptionsType[];
  yAxis: Highcharts.YAxisOptions;
}

/**
 * Custom hook to convert Syncari's API response to Highcharts-compatible
 * data format.
 *
 * For more info see
 * - https://api.highcharts.com/highcharts/series.pie
 */
export const useGaugeVizer = ({ configuration, data }: Params): ReturnTypes => {
  const yAxis: Highcharts.YAxisOptions = useMemo(() => {
    const firstRow = data?.rows?.[0];
    const firstColumn = configuration?.columns?.[0];
    const value =
      (firstColumn?.name && isNotNullOrUndefined(firstRow?.[firstColumn?.name]) && firstRow[firstColumn.name]) ?? '';

    const ranges = getRanges(
      configuration?.ranges?.slice()?.sort((a, b) => a?.minimumValue - b?.minimumValue),
      value
    );

    const rangesCount = ranges?.length;
    if (!rangesCount) {
      return {};
    }

    const min = isNaN(Number(ranges?.[0]?.minimumValue)) ? 0 : ranges?.[0].minimumValue;
    const max = isNaN(Number(ranges?.[rangesCount - 1]?.maximumValue)) ? 0 : ranges?.[rangesCount - 1].maximumValue;

    const plotBands = ranges?.map((range) => ({
      from: range.minimumValue,
      to: range.maximumValue || 0,
      color: VizerGraphColors.getDefaultColor(range.color).color,
      thickness: 20,
    }));
    return {
      min,
      max,
      minorTickInterval: null,
      plotBands,
      tickLength: 20,
      tickWidth: 2,
      labels: {
        distance: 20,
        style: {
          fontSize: '14px',
        },
      },
    };
  }, [configuration?.ranges, configuration?.columns, data?.rows]);

  const series: SeriesOptionsType[] = useMemo(() => {
    const firstColumn = configuration?.columns?.[0];
    const displayFormat = configuration?.columns?.[0]?.displayFormat || 'text';
    const firstRow = data?.rows?.[0];
    if (firstColumn?.name && isNotNullOrUndefined(firstRow?.[firstColumn?.name])) {
      const value = firstRow[firstColumn.name] ?? '';
      const range = getRange(configuration.ranges, value);

      return [
        {
          type: 'gauge',
          name: configuration.columns?.[0]?.name,
          data: [value],
          dial: {
            radius: '80%',
            backgroundColor: colors.gray800,
            baseWidth: 12,
            baseLength: '0%',
            rearLength: '0%',
          },
          pivot: {
            backgroundColor: colors.gray800,
            radius: 6,
          },
          dataLabels: {
            formatter() {
              return `<span><b>${displayFormatter[displayFormat](value)}</b> ${range.name}</span>`;
            },
            style: {
              fontSize: '16px',
              color: range.color,
              fontWeight: 'normal',
              textOutline: 'none',
            },
            borderWidth: 0,
          },
        },
      ];
    }
    return [];
  }, [configuration?.columns, data?.rows, configuration.ranges]);

  return {
    series,
    yAxis,
  };
};

export const defaultRange: Range = {
  name: '',
  minimumValue: 0,
  maximumValue: 100,
  color: chartColors[0].color,
};

/**
 * Searches through an array of ranges to find the range that a given value falls within.
 * If no matching range is found, returns the defaultRange object.
 * @param ranges
 * @param value
 * @returns The Range object that the given value falls within, or defaultRange if no match is found.
 */
export function getRange(ranges: Range[] | undefined, value: string) {
  const numberValue = Number(value);
  if (isNaN(numberValue)) {
    return defaultRange;
  }
  const range = ranges?.find((range) => {
    if (numberValue >= range.minimumValue && numberValue <= range.maximumValue) {
      return true;
    }
    return false;
  });
  if (range) {
    return range;
  }
  const sortedRanges = ranges?.slice()?.sort((a, b) => a.minimumValue - b.minimumValue) || [];
  const lastRange = sortedRanges[sortedRanges.length - 1];
  const firstRange = sortedRanges[0];
  if (lastRange?.maximumValue && numberValue > lastRange.maximumValue) {
    return {
      name: tn('max_out_of_range_label', { max: lastRange.maximumValue }),
      minimumValue: lastRange.maximumValue,
      maximumValue: numberValue,
      color: VizerGraphColors.getNextColor(lastRange.color),
      isSystemGenerated: true,
    };
  }
  if (firstRange?.minimumValue && numberValue < firstRange.minimumValue) {
    return {
      name: tn('min_out_of_range_label', { min: firstRange.minimumValue }),
      minimumValue: numberValue,
      maximumValue: firstRange.minimumValue,
      color: VizerGraphColors.getNextColor(lastRange.color),
      isSystemGenerated: true,
    };
  }
  return defaultRange;
}

/**
 * Searches through an array of ranges to find the ranges that a given value falls within.
 * If the value falls outside of all ranges, returns the input ranges array with an additional "out of range" range added. Else returns the original ranges array.
 * @param ranges
 * @param value
 */
export function getRanges(ranges: Range[] | undefined, value: string) {
  const numberValue = Number(value);
  if (!ranges || isNaN(numberValue)) {
    return ranges;
  }
  for (const range of ranges) {
    if (numberValue >= range.minimumValue && numberValue <= range.maximumValue) {
      return ranges;
    }
  }

  const lastRange = ranges[ranges.length - 1];
  const firstRange = ranges[0];

  if (lastRange?.maximumValue && numberValue > lastRange.maximumValue) {
    return [
      ...ranges,
      {
        name: tn('max_out_of_range_label', { max: lastRange.maximumValue }),
        minimumValue: lastRange.maximumValue,
        maximumValue: numberValue,
        color: VizerGraphColors.getNextColor(lastRange.color),
        isSystemGenerated: true,
      },
    ];
  }
  if (firstRange?.minimumValue && numberValue < firstRange.minimumValue) {
    return [
      {
        name: tn('min_out_of_range_label', { min: firstRange.minimumValue }),
        minimumValue: numberValue,
        maximumValue: firstRange.minimumValue,
        color: VizerGraphColors.getNextColor(firstRange.color),
        isSystemGenerated: true,
      },
      ...ranges,
    ];
  }
}
