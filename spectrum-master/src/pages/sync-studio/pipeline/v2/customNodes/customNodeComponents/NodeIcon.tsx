import cx from 'classnames';

import { PipelineNodeColors } from '../../types/PipelineV2.types';

import './NodeIcon.scss';

export interface NodeIconProps {
  icon: string;
  color: PipelineNodeColors;
}

const NodeIcon = ({ icon, color }: NodeIconProps) => {
  return (
    <div className={cx('pipeline-node-icon', color)}>
      <img className="pipeline-node-icon__icon" src={icon} alt="node-icon" />
    </div>
  );
};

export default NodeIcon;
