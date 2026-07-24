import { Icon } from 'antd';

import { tCommon } from 'utils/i18nUtil';

import './ClearFilterButton.scss';

export interface ClearFilterButtonProps {
  onClear: () => void;
}

const ClearFilterButton = ({ onClear }: ClearFilterButtonProps) => {
  return (
    <a className="clear-filter-button" onClick={onClear}>
      <Icon theme="outlined" type="close-circle" />
      {tCommon('clear_filter')}
    </a>
  );
};

export default ClearFilterButton;
