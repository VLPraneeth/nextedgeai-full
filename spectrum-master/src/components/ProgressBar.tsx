import { clamp } from 'utils';

import './ProgressBar.less';

export interface ProgressBarProps {
  progress: number;
}

const ProgressBar = ({ progress }: ProgressBarProps) => {
  // Progress number clamped to bounds of 0 and 100
  const progressWidth = clamp(progress, 0, 100);

  return (
    <div className="progress-bar-container">
      <div
        data-testid="progress-bar-progress"
        className="progress-bar-progress"
        style={{ width: `${progressWidth}%` }}
      />
    </div>
  );
};

export default ProgressBar;
