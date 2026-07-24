import { navigate, useMatch } from '@reach/router';

import { Dropdown } from 'components/toolbar';
import { DropdownOption } from 'components/toolbar/Dropdown';
import RouteConstants from 'utils/RouteConstants';

import './InsightsVersionSelector.less';

export interface InsightsVersionSelectorProps {
  isDraft: boolean;
  hasDraft: boolean;
  isDraftOfPublished: boolean;
}

export const InsightsVersionSelector = ({ isDraft, hasDraft, isDraftOfPublished }: InsightsVersionSelectorProps) => {
  const urlMatch = useMatch('/insights-studio/:dashboardId/*');

  const versionOptions = [
    { id: 'draft', name: 'Draft', disabled: !hasDraft && !isDraft },
    { id: 'published', name: 'Published', disabled: isDraft && !isDraftOfPublished },
  ];

  const activeVersion = isDraft ? versionOptions[0] : versionOptions[1];

  const handleVersionChange = (selectedVersion: DropdownOption) => {
    const versionRoute =
      selectedVersion.id === 'draft'
        ? RouteConstants.INSIGHTS_STUDIO + '/' + urlMatch?.dashboardId + '/draft'
        : RouteConstants.INSIGHTS_STUDIO + '/' + urlMatch?.dashboardId;

    navigate(versionRoute);
  };

  return (
    <div className="insights-version-selector">
      <Dropdown onChange={handleVersionChange} options={versionOptions} selected={activeVersion} />
    </div>
  );
};
