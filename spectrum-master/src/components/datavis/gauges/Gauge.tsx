import { Group } from '@visx/group';
import { Arc, Circle, Pie, Polygon } from '@visx/shape';
import { ProvidedProps } from '@visx/shape/lib/shapes/Pie';
import { Text } from '@visx/text';
import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import * as React from 'react';

import useDimensions from 'hooks/useDimensions';
import { gaugeSegments as defaultGaugeSegments, SegmentConfig, getSegmentForValue } from 'store/datascore';
import { getTextColorForBackgroundColor } from 'utils/ColorUtil';
import { colors } from 'utils/LessConstants';
import { ArrayElement } from 'utils/TypeUtils';

import './Gauge.less';

interface GaugeRadialSegmentConfig {
  idx: number;
  fill: React.SVGProps<SVGTextElement>['fill'];
  segmentWidth: number;
  segmentStartAngle: number;
  segmentEndAngle: number;
}

const syncariBlue = colors.syncariBlue;
const cursorChannelBg = '#F2F8FE';

const deg = (rad: number) => (rad * 180) / Math.PI;
const rad = (deg: number) => deg * (Math.PI / 180);

const segmentBarWidth = 12;

type PieRenderFnProvidedProps = ProvidedProps<GaugeRadialSegmentConfig>;

interface PieSegmentProps {
  arc: ArrayElement<PieRenderFnProvidedProps['arcs']>;
  path: PieRenderFnProvidedProps['path'];
}

const PieSegment = ({ path, arc }: PieSegmentProps) => {
  const d = path(arc);
  return d ? <path d={d} fill={arc.data.fill} /> : null;
};

const initialTextBox = {
  x: 0,
  y: 0,
  height: 0,
  width: 0,
};

export interface PillProps {
  children: string;
  backgroundColor?: React.SVGProps<SVGTextElement>['fill'];
  textColor?: React.SVGProps<SVGTextElement>['fill'];
  fontSize?: React.SVGProps<SVGTextElement>['fontSize'];
}

const Pill = ({ children, backgroundColor = 'silver', textColor = 'black', fontSize = 20 }: PillProps) => {
  const textRef = useRef<SVGTextElement | null>(null);
  const [textBounds, setTextBounds] = useState(() => initialTextBox);

  useLayoutEffect(() => {
    if (textRef.current) {
      // get bounds of the text element so we can properly scale
      // the pill label background
      setTextBounds(textRef.current.getBBox());
    }
  }, [children, fontSize]);

  return (
    <Group>
      <rect
        style={{
          transition: 'fill 0.25s ease-in-out',
        }}
        x={textBounds.x - 20}
        y={textBounds.y - 10}
        width={textBounds.width + 40}
        height={textBounds.height + 20}
        fill={backgroundColor}
        ry={textBounds.width}
        rx={textBounds.height}
      />
      <text
        className="gauge-subtitle"
        ref={textRef}
        fill={textColor}
        fontSize={fontSize}
        textAnchor="middle"
        vertical-anchor="start">
        {children}
      </text>
    </Group>
  );
};

export interface GaugeProps {
  height?: number;
  width?: number;
  className?: string;

  segments?: SegmentConfig[];
  subTitle?: string;
  value?: number;

  /* radius of the gauge */
  radius?: number;
  /* bounds, in degrees, of the gauge sweep */
  bounds?: [number, number];
  /* bounds, in degrees, of the cursor channel. */
  cursorChannelBounds?: [number, number];
  /* how thick is the gauge legend segments */
  segmentBarWidth?: number;
  /* how wide, in degrees, is the padding between segments */
  segmentPadAngle?: number;
  /* color of the cursor */
  cursorColor?: React.CSSProperties['backgroundColor'];
  /* padding of the cursor channel from the segments */
  cursorChannelPadding?: number;
  /* color of the cursor channel */
  cursorChannelColor?: React.CSSProperties['backgroundColor'];
  /* color of the value shield */
  shieldBgColor?: React.CSSProperties['backgroundColor'];
  /* color of the value shield inset ring */
  shieldAccentColor?: React.CSSProperties['color'];
}

// svg viewbox dimensions
const height = 500;
const width = 600;

const padding = 20;

const cursorStyle = {
  transition: 'all 0.5s ease-in-out',
};

// these look weird, but it gets our widget centered in the viewbox
const mainGroupLeftOffset = (height - padding) / 2;
const mainGroupTopOffset = width / 2 + padding * 2;

export const Gauge = ({
  className,
  segments = defaultGaugeSegments,
  subTitle,
  value = 0,
  bounds = [rad(-95), rad(95)],
  cursorChannelBounds = [rad(-105), rad(105)],
  cursorColor = syncariBlue,
  segmentPadAngle = rad(1.5),
  cursorChannelColor = cursorChannelBg,
  shieldAccentColor = cursorChannelBg,
  shieldBgColor = '#FFF',
}: GaugeProps) => {
  // svg viewbox - some padding
  const radius = width / 2 - padding * 2;

  const shieldRadius = radius * 0.68;
  const shieldInsetRadius = shieldRadius - 17;

  const titleFontSize = width * 0.2;
  const subTitleFontSize = width * 0.035;
  const subTitleTopMargin = radius * 0.27;

  const cursorSize = radius * 0.15;

  const domain = bounds.map((b) => Math.abs(deg(b))).reduce((a, b) => a + b);
  const currentValueAngle = deg(bounds[0]) + domain * (value / 100);

  const segmentData: GaugeRadialSegmentConfig[] = useMemo(
    () =>
      segments.map((segment, idx, allSegments) => {
        const segmentAngle = (bounds[1] - bounds[0]) / allSegments.length;
        const segmentStartAngle = rad(bounds[0] + idx * segmentAngle);
        const segmentEndAngle = rad(segmentStartAngle + segmentAngle);

        return {
          idx,
          segmentWidth: segmentAngle,
          segmentStartAngle,
          segmentEndAngle,
          fill: segment.color,
        };
      }),
    [bounds, segments]
  );

  const getCurrentSegment = useCallback((value: number) => getSegmentForValue(segments, value), [segments]);

  const currentSegment = getCurrentSegment(value);

  return (
    <svg className={className} viewBox={`0 0 ${height} ${width}`}>
      <filter id="circle">
        <feDropShadow dx="0" dy="0" stdDeviation="7" floodOpacity="0.25" />
      </filter>
      <Group top={mainGroupTopOffset} left={mainGroupLeftOffset} height={radius} width={radius}>
        <Arc
          key="cursor-channel"
          fill={cursorChannelColor}
          startAngle={cursorChannelBounds[0]}
          endAngle={cursorChannelBounds[1]}
          innerRadius={shieldRadius}
          outerRadius={radius - 20}
        />
        <Pie
          key="gauge-segments"
          startAngle={bounds[0]}
          endAngle={bounds[1]}
          data={segmentData}
          pieValue={(d) => d.segmentWidth}
          padAngle={segmentPadAngle}
          outerRadius={radius + segmentBarWidth}
          innerRadius={radius}>
          {({ arcs, path }) => arcs.map((arc) => <PieSegment key={arc.data.idx} arc={arc} path={path} />)}
        </Pie>

        <Group
          key="cursor"
          top={0}
          left={0}
          width={width}
          height={height}
          style={cursorStyle}
          transform={`rotate(${-90 + currentValueAngle})`}>
          <Group transform={`translate(${shieldRadius} 0)`}>
            <Polygon sides={3} size={cursorSize} fill={cursorColor} />
          </Group>
        </Group>

        <Circle key="shield" style={{ filter: 'url(#circle)' }} fill={shieldBgColor} r={shieldRadius} />
        <Arc
          key="shield-inset-stroke"
          fill={shieldBgColor}
          stroke={cursorChannelColor}
          startAngle={rad(0)}
          endAngle={rad(360)}
          innerRadius={shieldInsetRadius}
          outerRadius={shieldInsetRadius}
        />
        <Text
          key="gauge-value"
          className="gauge-value"
          fontSize={titleFontSize}
          textAnchor="middle"
          verticalAnchor="end"
          width={radius * 2}>
          {value}
        </Text>
        {subTitle && (
          <Group top={subTitleTopMargin}>
            <Pill
              fontSize={subTitleFontSize}
              backgroundColor={currentSegment.color}
              textColor={getTextColorForBackgroundColor(currentSegment.color)}>
              {subTitle}
            </Pill>
          </Group>
        )}
      </Group>
    </svg>
  );
};

export const GaugeWithParentSize = (props: GaugeProps) => {
  const [measureRef, dimensions] = useDimensions({ liveMeasure: true });
  const wrapperStyle = useMemo(() => ({ height: dimensions.height, width: dimensions.height }), [dimensions]);

  return (
    <div ref={measureRef} className="gauge-responsive-wrapper">
      <div style={wrapperStyle}>
        <Gauge className="gauge-responsive" {...props} />
      </div>
    </div>
  );
};

export default Gauge;
