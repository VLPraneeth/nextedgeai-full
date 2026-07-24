import { Button } from 'antd';

import { ReactComponent as FilterIcon } from 'assets/icons/filter.svg';
import { tCommon } from 'utils/i18nUtil';

import './FilterButton.scss';

export interface FilterButtonProps {
  onClick: () => void;
  isFilterActive: boolean;
}

const FilterButton = ({ onClick, isFilterActive }: FilterButtonProps) => {
  return (
    <Button className="filter-button" type="default" onClick={onClick}>
      <FilterIcon />
      {tCommon('filter_no_ellipse')}
      {isFilterActive && <div className="filter-button__active-indicator" />}
    </Button>
  );
};

export default FilterButton;
