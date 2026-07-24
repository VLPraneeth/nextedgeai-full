import { AxisScaleOutput } from '@visx/axis';
import { AxisScale } from '@visx/axis/lib/types';
import { curveMonotoneX } from '@visx/curve';
import { ParentSize } from '@visx/responsive';
import { ScaleTypeToScaleConfig } from '@visx/scale/lib/types/ScaleConfig';
import {
  AnimatedAreaSeries,
  AnimatedAxis,
  AnimatedGrid,
  AnimatedLineSeries,
  Tooltip,
  XYChart,
  buildChartTheme,
} from '@visx/xychart';
import { ThemeConfig } from '@visx/xychart/lib/theme/buildChartTheme';
import cx from 'classnames';
import * as React from 'react';
import { useCallback, useMemo } from 'react';

import { AnimatedAxisProps } from '@visx/xychart/lib/components/axis/AnimatedAxis';
import { XYChartProps } from '@visx/xychart/lib/components/XYChart';
import { HStack, Stack } from 'components/layout';
import { colors } from 'utils/LessConstants';
import { ValuesOf } from 'utils/TypeUtils';

import './LineChart.less';

export interface LineChartDatum<X = number | string, Y = number> {
  id: string;
  label: string;
  color?: React.CSSProperties['color'];
  x: X;
  y: Y;
}

const defaultHeight = 275;
const defaultWidth = 285;

const defaultMargins = {
  top: 10,
  right: 5,
  bottom: 10,
  left: 20,
};

const syncariBlue = colors.syncariBlue;
const gridLineStroke = colors.gray400;

const gridLineStyle = {
  stroke: gridLineStroke,
};

const tooltipVerticalCrosshairStyle = {
  stroke: syncariBlue,
  strokeWidth: 3,
};

// function getBounds<T extends {} = any>(data: T[][], lens: (item: T) => number): [number, number] {
//   // we have nested arrays, so we'll
//   // 1. map over the outer, and apply our lens fn to all elements of the inner
//   // 2. find the min/max of our inner elements
//   // 3. find min/max of the outer
//   const findMin = compose(min, map(min), map(map(lens)));
//   const findMax = compose(max, map(max), map(map(lens)));
//
//   return [findMin(data), findMax(data)];
// }

export interface LineChartAxis extends AnimatedAxisProps<AxisScale> {
  /* disables the axis */
  hide?: boolean;
}

// some super generic types to bootstrap our defaults :/
type BaseAxisScaleConfig = ValuesOf<ScaleTypeToScaleConfig<AxisScaleOutput>>; // ScaleConfig<AxisScaleOutput>;
type BaseXYChartProps = XYChartProps<BaseAxisScaleConfig, BaseAxisScaleConfig, any>;
type BaseXScaleConfig = BaseXYChartProps['xScale'];
type BaseYScaleConfig = BaseXYChartProps['yScale'];

export interface LineChartProps<T extends LineChartDatum = LineChartDatum>
  extends Omit<XYChartProps<NonNullable<BaseXScaleConfig>, NonNullable<BaseYScaleConfig>, T>, 'children' | 'theme'> {
  data: T[][];
  height?: number;
  width?: number;
  margins?: typeof defaultMargins;
  theme?: ThemeConfig;
  xAxis?: LineChartAxis;
  yAxis?: LineChartAxis;
}

const defaultXAxisConfig: LineChartAxis = {
  animationTrajectory: 'min',
  orientation: 'bottom',
  hideTicks: true,
  hideAxisLine: true,
};

const defaultYAxisConfig: LineChartAxis = {
  animationTrajectory: 'min',
  orientation: 'right',
};

const defaultSyncariTheme = {
  backgroundColor: 'white',
  colors: [syncariBlue],
  gridColor: gridLineStroke,
  gridColorDark: gridLineStroke,
  tickLength: 5,
  gridStyles: {},
  htmlLabel: {},
  svgLabelSmall: {},
  svgLabelBig: {},
};

const LineChart = <T extends LineChartDatum = LineChartDatum>({
  data,
  theme = defaultSyncariTheme,
  height = defaultHeight,
  width = defaultWidth,
  margins = defaultMargins,
  xAxis: xAxisConfig,
  xScale: xScaleConfig,
  yAxis: yAxisConfig,
  yScale: yScaleConfig,
}: LineChartProps<T>) => {
  // these callbacks only need to be created once, and they are component local for the typing
  const getLabel = useCallback((datum: T | undefined) => datum?.label, []);
  const getX = useCallback((datum: T | undefined) => datum?.x, []);
  const getY = useCallback((datum: T | undefined) => datum?.y, []);

  const chartTheme = useMemo(() => buildChartTheme(theme), [theme]);

  const xAxis = useMemo(() => ({ ...defaultXAxisConfig, ...xAxisConfig }), [xAxisConfig]);
  const xScale: BaseXScaleConfig = useMemo(() => ({ type: 'band', ...xScaleConfig }), [xScaleConfig]);
  const yAxis = useMemo(() => ({ ...defaultYAxisConfig, ...yAxisConfig }), [yAxisConfig]);
  const yScale: BaseYScaleConfig = useMemo(() => ({ type: 'linear', ...yScaleConfig }), [yScaleConfig]);

  return (
    <XYChart<NonNullable<BaseXScaleConfig>, NonNullable<BaseYScaleConfig>, T>
      theme={chartTheme}
      height={height}
      width={width}
      xScale={xScale}
      yScale={yScale}>
      {!xAxis?.hide && <AnimatedAxis animationTrajectory={xAxis.animationTrajectory} {...xAxis} />}
      {!yAxis?.hide && <AnimatedAxis animationTrajectory={yAxis.animationTrajectory} {...yAxis} />}
      <AnimatedGrid columns={false} rows numTicks={10} lineStyle={gridLineStyle} stroke={gridLineStroke} />

      <Tooltip<T>
        detectBounds
        showVerticalCrosshair
        showSeriesGlyphs
        snapTooltipToDatumX
        verticalCrosshairStyle={tooltipVerticalCrosshairStyle}
        className="syncari-datavis-tooltip-wrapper"
        renderTooltip={({ tooltipData, colorScale }) => {
          // get keys provided for each series of data. We will need this for a few places,
          // namely showing the labels in the tooltip properly
          // TODO: remove hardcoding for series label, provide as config. Right now we only have 1 data series
          const seriesLabel = 'NextEdge AI';
          return (
            <div className="syncari-datavis-tooltip">
              <Stack>
                <div className="title">{getLabel(tooltipData?.nearestDatum?.datum)}</div>
                <Stack>
                  {Object.keys(tooltipData?.datumByKey || {})
                    .filter(Boolean)
                    .map((d) => (
                      <HStack
                        key={d}
                        spacing="xxs"
                        className={cx('series-item', {
                          nearest: tooltipData?.nearestDatum?.key === d,
                        })}>
                        <span className="datum-value">{getY(tooltipData?.datumByKey?.[d]?.datum)}</span>
                        <span className="datum-title">({seriesLabel})</span>
                      </HStack>
                    ))}
                </Stack>
              </Stack>
            </div>
          );
        }}
      />

      {data.map((d, idx) =>
        idx > 0 ? (
          <AnimatedLineSeries
            key={idx}
            curve={curveMonotoneX}
            dataKey={idx.toString()}
            data={d}
            stroke={syncariBlue}
            xAccessor={getX}
            yAccessor={getY}
          />
        ) : (
          <AnimatedAreaSeries
            renderLine
            key={idx}
            fill={syncariBlue}
            fillOpacity={0.05}
            curve={curveMonotoneX}
            dataKey={idx.toString()}
            data={d}
            xAccessor={getX}
            yAccessor={getY}
          />
        )
      )}
    </XYChart>
  );
};

export const ResponsiveLineChart = <T extends LineChartDatum = LineChartDatum>(
  props: Omit<LineChartProps<T>, 'width'>
) => {
  return (
    <ParentSize debounceTime={300} enableDebounceLeadingCall={false} ignoreDimensions={['height', 'top', 'left']}>
      {({ height, width }) => {
        return <LineChart height={height || defaultHeight} width={width || defaultWidth} {...props} />;
      }}
    </ParentSize>
  );
};

export const SparklineTitle = ({ children }: { children?: React.ReactNode }) => (
  <div className="sparkline-title">{children}</div>
);

export default LineChart;
