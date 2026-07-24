import BatteryMeter, { BatteryMeterProps } from 'components/BatteryMeter';
import { gaugeSegments, getSegmentForValue } from 'store/datascore';

const getSegment = getSegmentForValue.bind(null, gaugeSegments);

export type DataScoreBatteryMeterProps = {
  score?: number | null;
} & Pick<BatteryMeterProps, 'className' | 'size'>;

const DataScoreBatteryMeter = ({ score, ...props }: DataScoreBatteryMeterProps) => {
  const { color } = getSegment(score);
  const label = typeof score === 'number' ? score.toString() : '––';
  const value = score ?? 0;

  return <BatteryMeter color={color} label={label} level={value} {...props} />;
};

export default DataScoreBatteryMeter;
