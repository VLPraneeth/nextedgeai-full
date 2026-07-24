import cx from 'classnames';
import { useMemo } from 'react';
import * as React from 'react';

import Text, { TextProps } from 'components/typography/Text';

import './BatteryMeter.less';

export type BatterySize = 'small' | 'large';

export type BatteryMeterProps = {
  className?: string;
  color: string;
  label: React.ReactNode | string;
  /** current level to display */
  level: number;
  /** max level allowed, used to calculate the number of segments to fill. */
  maxLevel?: number;
  /** how many battery segments are shown. */
  segments?: number;
  size?: BatterySize;
};

const labelSizeProps: Record<BatterySize, Omit<TextProps, 'children'>> = {
  small: {
    color: 'black',
    lineHeight: 'single',
    size: 'sm',
    weight: 'bold',
  },
  large: {
    color: 'black',
    lineHeight: 'single',
    size: 'xl',
    weight: 'semibold',
  },
};

const getBatteryLevel = (level: number, maxLevel: number, segments: number) => {
  const segmentValue = maxLevel / segments;
  return Math.ceil(level / segmentValue);
};

const BatteryMeter = ({
  className,
  color,
  label,
  level,
  maxLevel = 100,
  segments = 5,
  size = 'small',
}: BatteryMeterProps) => {
  // stabilize style object
  const containerStyle = useMemo(() => ({ backgroundColor: color }), [color]);

  const batteryLevel = getBatteryLevel(level, maxLevel, segments);

  return (
    <div className={cx('battery-meter-container', size, className)} style={containerStyle}>
      <div className="battery-meter">
        {
          /* generate the battery meter, and highlight the bars. Because this stacks
           * top to bottom, we'll flip it to build bottom to top */
          Array.from({ length: segments })
            .map((_, i) => [
              <div key={`bar-${i}`} className={cx('battery-bar', i <= batteryLevel && 'filled')} />,
              i > 0 && <div key={`spacer-${i}`} className="battery-bar-spacer" />,
            ])
            .reverse()
        }
      </div>
      <div className="label">{typeof label === 'string' ? <Text {...labelSizeProps[size]}>{label}</Text> : label}</div>
    </div>
  );
};

export default BatteryMeter;
