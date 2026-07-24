import { getIconFromPath } from 'components/icons/Icons';
import StatusTag, { StatusTagColors } from 'components/StatusTag';

import { SearchItemNode } from './SyncStudioSearch';

import './SyncStudioSearch.less';

type SyncStudioSearchOption = SearchItemNode;

type SyncStudioSearchItem = {
  option: SyncStudioSearchOption;
  onClick: (e: React.MouseEvent<HTMLElement>, option: SyncStudioSearchOption) => void;
  statusDisplayText: string;
  statusColor: StatusTagColors;
};

const SearchItem = ({ option, onClick, statusDisplayText, statusColor }: SyncStudioSearchItem) => (
  <div
    key={`${option.name}-${option.path}`}
    onClick={(e: React.MouseEvent<HTMLElement>) => onClick(e, option)}
    className="sync-studio-search__menu-item"
    aria-label={`sync-studio-search__menu-item-${option.name}`}>
    <span className="sync-studio-search__menu-item-icon">{option.iconPath && getIconFromPath(option.iconPath)}</span>
    <p className="sync-studio-search__menu-item-title">{option.title}</p>
    <div className="sync-studio-search__menu-item-path-container">
      <p className="sync-studio-search__menu-item-path">{`in ${option.path}`}</p>
      <StatusTag text={statusDisplayText} color={statusColor} />
    </div>
  </div>
);

export default SearchItem;
