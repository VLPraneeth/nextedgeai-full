import { ReactComponent as RocketIcon } from 'assets/icons/rocket.svg';
import { START_PRODUCT_TOUR_EVENT } from 'utils/GuidedDemo';

import './LaunchTourButton.less';

export const LaunchTourButton = () => {
  const launchTour = () => {
    window.dispatchEvent(new CustomEvent(START_PRODUCT_TOUR_EVENT));
  };

  const buttonLabel = 'Product tour';

  return (
    <button className="launch-tour-button" aria-label={buttonLabel} onClick={launchTour}>
      <RocketIcon className="header-icon launch-tour-button__icon" />
      <span>{buttonLabel}</span>
    </button>
  );
};
