import cx from 'classnames';
import * as React from 'react';

import { HStack, Stack } from 'components/layout';
import { gaugeSegments as dataScoreGaugeSegments, SegmentConfig, getSegmentForValue } from 'store/datascore';

import './HorizontalGauge.less';

const Segment = ({ color, min, max }: SegmentConfig) => {
  return <span className="h-gauge-segment" style={{ backgroundColor: color, flexBasis: `${max - min}%` }} />;
};

export interface CursorProps {
  color: React.CSSProperties['color'];
  value: number;
}

const Cursor = ({ color: borderColor, value }: CursorProps) => {
  return <div className="h-gauge-cursor" style={{ borderColor, marginLeft: `${value}%` }} />;
};

export interface HorizontalGaugeProps {
  className?: string;
  segments?: SegmentConfig[];
  /* number between 0 - 100 to represent the current gauge value */
  value?: number;
  subTitle?: string;
}

export const HorizontalGauge = ({
  className,
  segments = dataScoreGaugeSegments,
  subTitle,
  value = 0,
}: HorizontalGaugeProps) => {
  const currentSegment = getSegmentForValue(segments, value);
  const currentColor = (currentSegment || segments[0]).color;

  return (
    <div className={cx('h-gauge-container', className)}>
      <Stack spacing="z">
        <HStack align="baseline" spacing="xs">
          <span className="h-gauge-title">{value}</span>
          {subTitle && <span className="h-gauge-subtitle">{subTitle}</span>}
        </HStack>
        <div className="h-gauge">
          {segments.map((segmentProps, idx) => (
            <Segment key={idx} {...segmentProps} />
          ))}
          <Cursor color={currentColor} value={value} />
        </div>
      </Stack>
    </div>
  );
};

export default HorizontalGauge;
