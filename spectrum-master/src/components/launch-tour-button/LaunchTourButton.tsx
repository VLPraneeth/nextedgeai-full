import { Tooltip } from 'antd';
import userflow from 'userflow.js';

import { ReactComponent as RocketIcon } from 'assets/icons/rocket.svg';
import { useIsTrialUser } from 'store/user/selector.hooks';
import { t } from 'utils/i18nUtil';

import './LaunchTourButton.less';

export const LaunchTourButton = () => {
  const isTrial = useIsTrialUser();
  // Only visible in trial instances
  if (!isTrial) {
    return null;
  }

  const launchTour = () => {
    userflow.start('c43ebd33-87f3-4c4b-9af6-88b796c203dd');
  };

  const buttonLabel = t('MainHeader.launch_tour');

  return (
    <Tooltip title={buttonLabel}>
      <button className="launch-tour-button" aria-label={buttonLabel} onClick={launchTour}>
        <RocketIcon className="header-icon launch-tour-button__icon" />
      </button>
    </Tooltip>
  );
};
