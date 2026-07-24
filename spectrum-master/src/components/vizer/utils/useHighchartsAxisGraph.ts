import { AxisLabelsFormatterContextObject, Point, SeriesOptionsType, TooltipOptions } from 'highcharts';
import { useMemo } from 'react';

import { DataCardData, DataCardVizConfig, DataRow } from 'store/insights-studio/types';

import { displayFormatter } from './VizerDisplayFormatter';
import { VizerGraphColors } from './VizerGraphColors';

export interface Params {
  configuration: DataCardVizConfig;
  data: DataCardData;
}

export interface ReturnTypes {
  categories: string[];
  highchartSeries: SeriesOptionsType[];
  yAxis: { title: { text: string } }[];
  tooltip: TooltipOptions;
}

/**
 * Custom hook to convert Syncari's API response to Highcharts-compatible
 * data format.
 * Returns:
 *    - __categories:__ array of strings to be used as x-axis values
 *    - __highchartsSeries:__ array of data series to display on the graph. In Highcharts,
 *      a series is a set of data points on the y-axis that compose a single
 *      line or set of bars on a graph.
 *    - __yAxis:__ array of configuration objects for the y-axes of a graph.
 *
 * For more info see
 * - https://api.highcharts.com/highcharts/xAxis.categories
 * - https://api.highcharts.com/highcharts/series
 * - https://api.highcharts.com/highcharts/yAxis
 */
export const useHighchartsAxisGraph = ({ configuration, data }: Params): ReturnTypes => {
  const categories = useMemo(
    () => getXaxisValues(data.rows, configuration.xaxis.name),

    [configuration.xaxis.name, data.rows]
  );

  const categoriesMultipleY = useMemo(() => {
    const cats = data.rows?.map((row) => {
      return row[configuration.xaxis.name];
    });
    return cats;
  }, [configuration.xaxis?.name, data.rows]);

  const yAxis = configuration.yaxis.map((axis, i) => {
    return {
      title: { text: axis.displayName },
      labels: {
        formatter(this: AxisLabelsFormatterContextObject, ctx: AxisLabelsFormatterContextObject) {
          // Axis graph doesn't have numberPercent display format. Needed this check for TS typing.
          if (axis?.displayFormat && displayFormatter[axis?.displayFormat] && axis?.displayFormat !== 'numberPercent') {
            return displayFormatter[axis?.displayFormat](String(this.value), {
              // Use compact numbers since theres not much real estate
              notation: 'compact',
            });
          }
          return this.axis.defaultLabelFormatter.call(this);
        },
      },
      opposite: i % 2 !== 0,
      max: axis.displayFormat === 'percent' ? 100 : undefined,
      allowDecimals: false,
      color: axis.color,
    };
  });

  const tooltip = useMemo(() => {
    // Always use the first yaxis configuration for the display formatter for now
    // Add support for formatter per y axis in the future.
    const displayFormat = configuration.yaxis?.[0]?.displayFormat;
    return {
      pointFormatter(this: Point): string {
        const value =
          displayFormat && this.y && displayFormatter[displayFormat]
            ? displayFormatter[displayFormat](String(this.y))
            : this.y;
        // Highchart need a markup in string
        return `<span">&bull;${this.series.name}</span>: <b>${value}</b><br />`;
      },
      shared: true,
    };
  }, [configuration.yaxis]);

  const hasMultipleYAxis = configuration.yaxis?.length > 1;

  const highchartSeries: SeriesOptionsType[] = useMemo(() => getAxisGraphSeries(data, configuration), [
    configuration,
    data,
  ]);

  return {
    categories: hasMultipleYAxis ? categoriesMultipleY : categories,
    highchartSeries,
    yAxis: [yAxis?.[0]].filter(Boolean),
    tooltip,
  };
};

export function getAxisGraphSeries(data: DataCardData, configuration: DataCardVizConfig) {
  const seriesDiscriminatorColumnName = configuration.series?.[0]?.name;
  const hasSeries = !!seriesDiscriminatorColumnName;
  const categories = getXaxisValues(data.rows, configuration.xaxis.name);

  // For each series, produce a data array with one data point per x-axis value
  const getSeriesDescriminator = () => {
    const series = data.series
      // Ignore any data series without a display name
      ?.filter((series) => !!series.displayName)
      .map((series, index) => {
        const rowsForSeries = hasSeries
          ? data.rows.filter((row) => row[seriesDiscriminatorColumnName] === series.displayName)
          : data.rows;

        const yAxisForSeries = series.yaxis ?? 0;

        const dataArray = categories.map((xValue, index) => {
          const seriesRowForXValue = rowsForSeries.find((row) => {
            if (hasSeries) {
              // When there are multiple series, ensure returning the data point for the correct x-value and series
              return (
                row[configuration.xaxis.name] === xValue && row[seriesDiscriminatorColumnName] === series.displayName
              );
            } else {
              return row[configuration.xaxis.name]?.toString() === xValue;
            }
          });

          const yValue = seriesRowForXValue?.[configuration.yaxis[yAxisForSeries].name] ?? undefined;
          // This MUST return one object for every x-axis value, otherwise
          // the data points will not be placed on the graph correctly
          return {
            x: index,
            // Try to convert it to a number. Mostlikely the data card has a wrong configuration
            // if highchart throws an error. https://assets.highcharts.com/errors/14/
            y: isNaN(parseFloat(yValue)) ? yValue : parseFloat(yValue),
            name: seriesRowForXValue?.[configuration.xaxis.displayName],
          };
        });

        return {
          color:
            configuration.categoryValues?.find((val) => val.name === series.displayName)?.color ||
            (!configuration.colorTheme ? VizerGraphColors.getColorFromIndex(index) : undefined),
          data: dataArray,
          name: series.displayName, // Displays in the legend of the graph
          type: configuration.vizType?.toLowerCase() as 'xrange', // type cast to x-range is required to avoid ts error
          yAxis: yAxisForSeries, // y-axis this data point should use. Defaults to the first.
        };
      });
    return series;
  };

  const getMultipleYSeries = () => {
    const series = data.series
      // Ignore any data series without a display name
      ?.filter((series) => !!series.displayName)
      .map((series, index) => {
        const dataArray = data?.rows?.map((datum, dataIndex) => {
          return {
            x: dataIndex,
            y: datum[series.displayName],
            name: datum[configuration.xaxis.name],
          };
        });
        const ycolor =
          configuration.yaxis.find((y) => y.name === series.displayName)?.color ||
          (!configuration.colorTheme ? VizerGraphColors.getColorFromIndex(index) : undefined);

        return {
          color: ycolor,
          data: dataArray,
          name: series.displayName,
          type: configuration.vizType?.toLowerCase() as 'xrange', // type cast to x-range is required to avoid ts error
          yAxis: 0,
        };
      });
    return series;
  };

  return hasSeries ? getSeriesDescriminator() : getMultipleYSeries();
}

/**
 * Pure function that extracts the x-axis values from the Syncari API response
 * into a single array of string values.
 * @param rows array of objects representing rows of a data table. The keys are column names.
 * @param columnName name of the column to pull values from each row object
 * @returns array of strings for x-axis values
 */
function getXaxisValues(rows: DataRow[], columnName: string) {
  const xAxisValues: string[] = [];

  rows.forEach((row) => {
    if (!xAxisValues.includes(row[columnName]?.toString())) {
      xAxisValues.push(row[columnName]?.toString());
    }
  });

  return xAxisValues;
}
/**
 * Utilify function to check if the error is from highcharts
 * See for official highchart error https://www.highcharts.com/forum/viewtopic.php?t=30697
 *
 * @param errorMsg string possible error message from highcharts
 * @returns boolean true if highchart error otherwise false
 */
export const isHighChartsError = (errorMsg: string) => errorMsg?.toLowerCase()?.indexOf('highcharts error #') !== -1;
