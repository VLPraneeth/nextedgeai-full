import { Group } from '@visx/group';
import { ArcPathConfig, Line } from '@visx/shape';
import Pie, { PieProps, PieArcDatum } from '@visx/shape/lib/shapes/Pie';
import { Text as SvgText } from '@visx/text';
import { useMemo } from 'react';
import * as React from 'react';
import { animated, useSpring, interpolate } from 'react-spring';

import { HStack } from 'components/layout';
import { propOr } from 'utils/Fp';
import { colors, variables } from 'utils/LessConstants';

import { arcOuterCentroid, getArcPath } from './utils';

import './PieChart.less';

export interface PieChartDatum {
  id: string;
  label: string;
  value: number;
  color: string;
}

export interface AnimatedPieSliceProps<Datum> {
  arc: PieArcDatum<Datum>;
  outerRadius: number;
  isSelected: boolean;
  onClick: () => void;
  fill?: string;
  labelColor?: React.CSSProperties['color'];
  labelFontSize?: React.CSSProperties['fontSize'];
  stroke: React.CSSProperties['stroke'];
  strokeWidth?: React.CSSProperties['strokeWidth'];
  tickColor?: React.CSSProperties['stroke'];
}

const AnimatedPieSlice = <Datum extends PieChartDatum>({
  arc,
  isSelected,
  outerRadius,
  cornerRadius = 0,
  innerRadius = 0,
  padRadius = 0,
  fill,
  stroke,
  strokeWidth,
  labelColor = colors.black,
  labelFontSize = 12,
  tickColor = colors.gray400,
  onClick,
}: AnimatedPieSliceProps<Datum> & Pick<PieProps<Datum>, 'innerRadius' | 'padRadius' | 'cornerRadius'>) => {
  // the outer centroid is the midpoint of the arc, on the outer radius.
  // we'll use this to help position the label tick
  const [centroidX, centroidY] = arcOuterCentroid<Datum>(outerRadius, arc);

  const spring = useSpring<{ outerRadius: number; opacity: number }>({
    config: {
      tension: 600,
    },
    outerRadius: isSelected ? outerRadius * 1.1 : outerRadius,
    opacity: isSelected ? 0 : 1,
  });

  const coordinates = useMemo(
    () => ({
      from: {
        x: centroidX * 1.05,
        y: centroidY * 1.05,
      },
      to: {
        x: centroidX * 1.12,
        y: centroidY * 1.12,
      },
      labelPosition: {
        left: centroidX * 1.27,
        top: centroidY * 1.27,
      },
    }),
    [centroidX, centroidY]
  );

  return (
    <g>
      <animated.path
        d={interpolate([spring.outerRadius], (radius) =>
          getArcPath(
            {
              innerRadius: innerRadius as number,
              cornerRadius: cornerRadius as number,
              padRadius: padRadius as number,
              outerRadius: radius,
            },
            arc
          )
        )}
        fill={fill}
        stroke={stroke}
        strokeWidth={strokeWidth}
        onClick={onClick}
      />
      <animated.g opacity={spring.opacity}>
        <Line stroke={tickColor} from={coordinates.from} to={coordinates.to} />
      </animated.g>
      <Group left={coordinates.labelPosition.left} top={coordinates.labelPosition.top}>
        <SvgText
          color={labelColor}
          fontWeight={isSelected ? variables.fontWeights.bold : variables.fontWeights.regular}
          fontSize={labelFontSize}
          textAnchor="middle"
          verticalAnchor="middle">
          {arc.data.label}
        </SvgText>
      </Group>
    </g>
  );
};

const defaultMargins = {
  top: 70,
  bottom: 70,
  left: 70,
  right: 70,
};

export interface PieChartProps<Datum> {
  legend?: React.ReactNode;
  data: Datum[];
  height?: React.CSSProperties['height'];
  width?: React.CSSProperties['width'];
  margins?: typeof defaultMargins;
  labelColor?: React.CSSProperties['color'];
  labelFontSize?: React.CSSProperties['fontSize'];
  tickColor?: React.CSSProperties['stroke'];
  paddingColor?: React.CSSProperties['stroke'];
  paddingWidth?: React.CSSProperties['strokeWidth'];
  onClickPieSlice?: (datum: Datum, idx: number) => void;
  selectedPieSliceIdx?: number;
}

const PieChart = <Datum extends PieChartDatum = PieChartDatum>({
  legend,
  data,
  height = 420,
  width = 420,
  margins = defaultMargins,
  cornerRadius,
  innerRadius,
  padRadius,
  paddingColor = colors.white,
  paddingWidth = 5,
  onClickPieSlice,
  selectedPieSliceIdx,
  ...props
}: PieChartProps<Datum> & Pick<ArcPathConfig<PieArcDatum<Datum>>, 'cornerRadius' | 'innerRadius' | 'padRadius'>) => {
  const size = Math.min(+height, +width);
  const positionOffset = size / 2;

  const pieSize = size - (margins.top + margins.bottom);
  const diameter = pieSize;
  const radius = diameter / 2;

  return (
    <HStack align="start" className="pie-chart-hstack">
      <svg height={size} width={size}>
        <Group top={positionOffset} left={positionOffset}>
          <Pie<Datum> data={data} pieValue={propOr('value', 0)} outerRadius={radius}>
            {
              // TODO: make this a bit less repetitive
              ({ arcs }) =>
                arcs.map((arc, idx) => (
                  <AnimatedPieSlice<Datum>
                    key={arc.data.id}
                    arc={arc}
                    isSelected={idx === selectedPieSliceIdx}
                    fill={arc.data.color}
                    stroke={paddingColor}
                    strokeWidth={paddingWidth}
                    cornerRadius={cornerRadius}
                    innerRadius={innerRadius}
                    outerRadius={radius}
                    padRadius={padRadius}
                    onClick={() => onClickPieSlice?.(arc.data, idx)}
                  />
                ))
            }
          </Pie>
        </Group>
      </svg>

      {legend && <div className="pie-chart-legend">{legend}</div>}
    </HStack>
  );
};

export default PieChart;
