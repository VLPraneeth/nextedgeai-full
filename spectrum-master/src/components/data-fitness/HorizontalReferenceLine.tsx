import { Group } from '@visx/group';
import { Point } from '@visx/point';
import { D3Scale } from '@visx/scale';
import { Line } from '@visx/shape';
import { Text } from '@visx/text';
import { useMemo } from 'react';
import * as React from 'react';

import { fontFamily } from './svgTheme';

interface HorizontalReferenceLineProps {
  lineColor?: React.CSSProperties['color'];
  textColor?: React.CSSProperties['color'];
  textMargin?: number;
  reference: number;
  title: string;
  xScale: D3Scale<number>;
  yScale: D3Scale<number>;
}

const HorizontalReferenceLine = ({
  textColor = '#AAB6BE',
  lineColor = '#D7E1E8',
  textMargin = 5,
  reference,
  title,
  xScale,
  yScale,
}: HorizontalReferenceLineProps) => {
  const [x0, x1] = xScale.range();
  const scaledY = yScale(reference);
  const textX = x0 - textMargin;

  const from = useMemo(
    () =>
      new Point({
        x: x0,
        y: scaledY,
      }),
    [scaledY, x0]
  );
  const to = useMemo(
    () =>
      new Point({
        x: x1,
        y: scaledY,
      }),
    [x1, scaledY]
  );

  return (
    <Group>
      <Line stroke={lineColor} strokeWidth={1} from={from} to={to} />
      <Text
        fill={textColor}
        fontFamily={fontFamily}
        fontSize="9px"
        textAnchor="end"
        verticalAnchor="middle"
        x={textX}
        y={scaledY}>
        {title}
      </Text>
    </Group>
  );
};

export default HorizontalReferenceLine;
