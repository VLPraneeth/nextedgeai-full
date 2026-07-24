import DataScoreBatteryMeter from 'components/data-fitness/DataScoreBatteryMeter';

import './ScoreCell.less';

interface ScoreCellProps {
  score: number;
}

const ScoreCell = ({ score }: ScoreCellProps) => {
  return <DataScoreBatteryMeter score={score} />;
};

const ScoreCellRenderer = (score: number) => <ScoreCell score={score} />;

export default ScoreCellRenderer;
