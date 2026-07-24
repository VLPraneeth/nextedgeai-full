import { curveCardinal } from '@visx/curve';
import { MarkerCircle } from '@visx/marker';
import { ParentSize } from '@visx/responsive';
import { scaleLinear, scaleTime } from '@visx/scale';
import { LinePath } from '@visx/shape';
import { useCallback, useEffect, useMemo } from 'react';

import HorizontalReferenceLine from './HorizontalReferenceLine';

import './Sparkline.less';

export interface SparklineDatum {
  date: number;
  value: number;
}

const defaultWidth = 285;

const defaultMargins = {
  top: 10,
  right: 5,
  bottom: 10,
  left: 20,
};

interface SparklineProps<T extends SparklineDatum> {
  data: T[];
  height?: number;
  width?: number;
  margins?: typeof defaultMargins;
}

const Sparkline = <T extends SparklineDatum = SparklineDatum>({
  data,
  height = 50,
  width = defaultWidth,
  margins = defaultMargins,
}: SparklineProps<T>) => {
  const enhancedData = useMemo(() => data.slice(0, 30), [data]);

  const getDate = useCallback((datum: T) => datum.date, []);
  const getValue = useCallback((datum: T) => datum.value, []);

  const [minDate, maxDate] = useMemo(
    () => [Math.floor(Math.min(...enhancedData.map(getDate))), Math.ceil(Math.max(...enhancedData.map(getDate)))],
    [enhancedData, getDate]
  );
  const [minValue, maxValue] = useMemo(
    () => [Math.floor(Math.min(...enhancedData.map(getValue))), Math.ceil(Math.max(...enhancedData.map(getValue)))],
    [getValue, enhancedData]
  );

  const xScale = useMemo(
    () =>
      scaleTime<number>({
        domain: [minDate, maxDate],
      }),
    [minDate, maxDate]
  );

  const yScale = useMemo(
    () =>
      scaleLinear<number>({
        domain: [minValue, maxValue],
      }),
    [minValue, maxValue]
  );

  useEffect(() => {
    if (margins) {
      xScale.range([margins.left, width - margins.right]);
      // it's important to have this as max, min so we get the scale correct. By default,
      // it's reversed using Y coordinates from the browser
      yScale.range([height - margins.top, margins.bottom]);
    }
  }, [height, margins, xScale, width, yScale]);

  const getX = useCallback((d: any) => xScale(getDate(d)), [getDate, xScale]);
  const getY = useCallback((d: any) => yScale(getValue(d)), [getValue, yScale]);

  return (
    <svg width={width} height={height}>
      <MarkerCircle id="sparkline-start-circle" refX={6} size={2.5} fill="#FFFFFF" stroke="#2C8FF2" strokeWidth={1} />
      <MarkerCircle id="sparkline-end-circle" refX={6} size={2.5} fill="#FFFFFF" stroke="#2C8FF2" strokeWidth={1} />
      {enhancedData?.length > 0 && (
        <>
          <HorizontalReferenceLine reference={maxValue} title={maxValue.toString()} xScale={xScale} yScale={yScale} />
          <HorizontalReferenceLine reference={minValue} title={minValue.toString()} xScale={xScale} yScale={yScale} />
          <LinePath<T>
            curve={curveCardinal}
            data={enhancedData}
            stroke="#2C8FF2"
            strokeWidth={1.5}
            x={getX}
            y={getY}
            markerStart="url(#sparkline-start-circle)"
            markerEnd="url(#sparkline-end-circle)"
          />
        </>
      )}
    </svg>
  );
};

export const ResponsiveSparkline = <T extends SparklineDatum = SparklineDatum>(
  props: Omit<SparklineProps<T>, 'width'>
) => {
  return (
    <ParentSize enableDebounceLeadingCall={false} ignoreDimensions={['height', 'top', 'left']}>
      {({ width }) => {
        return <Sparkline width={width || defaultWidth} {...props} />;
      }}
    </ParentSize>
  );
};

export const SparklineTitle = ({ children }: { children?: React.ReactNode }) => (
  <div className="sparkline-title">{children}</div>
);

export default Sparkline;
