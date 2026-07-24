import cx from 'classnames';

import { PipelineNodeColors } from '../../types/PipelineV2.types';

import './NodeTitleBar.scss';

export interface NodeTitleBarProps {
  color: PipelineNodeColors | 'solid-blue';
  label?: string;
}

const NodeTitleBar = ({ color, label }: NodeTitleBarProps) => {
  return (
    <div className={cx('pipeline-node-title-bar', color)}>
      {/* \u00A0 is non-breakingi space to have the same spacing if label is empty */}
      <span className="pipeline-node-title-bar__label">{label || '\u00A0'}</span>
    </div>
  );
};

export default NodeTitleBar;
