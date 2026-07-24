// import Button from 'components/Button';
import userflow from 'userflow.js';

import { TopBanner, TopBannerButton, TopBannerTypes } from 'components/top-banner/TopBanner';
import { useCurrentInstanceState } from 'store/instances/useCurrentInstanceState';
import { useIsTrialUser } from 'store/user/selector.hooks';
import { tNamespaced, tc } from 'utils/i18nUtil';

import './TrialBanner.less';

const tn = tNamespaced('PlgTrial');

const requestDemoFlowId = '40d2204c-9711-4e02-84ba-251671f223ad',
  upgradeAccountFlowId = '233dee47-4388-42bd-8cb1-cff8aa7a5c98';

export const TrialBanner = () => {
  const {
    publishLimitExpired,
    recordLimit,
    recordLimitExpired,
    trialDaysLeft,
    trialExpired,
  } = useCurrentInstanceState();
  const isNotTrialUser = !useIsTrialUser();

  if (isNotTrialUser) {
    return null;
  }

  const trialNotActive = trialExpired || recordLimitExpired || publishLimitExpired;

  let bannerText = tn('trial_active', { count: trialDaysLeft });
  if (trialExpired) {
    bannerText = tn('trial_expired');
  } else if (recordLimitExpired) {
    bannerText = tn('trial_record_limit_reached', { limit: Intl.NumberFormat().format(+recordLimit) });
  } else if (publishLimitExpired) {
    bannerText = tn('trial_publish_limit_reached');
  } else if (trialDaysLeft < 1) {
    bannerText = tn('trial_ends_today');
  }

  const launchRequestDemoFlow = () => userflow.start(requestDemoFlowId);
  const launchUpgradeAccountFlow = () => userflow.start(upgradeAccountFlowId);

  return (
    <TopBanner type={trialNotActive ? TopBannerTypes.Warn : TopBannerTypes.Info}>
      <div>{bannerText}</div>
      <div>
        <TopBannerButton ghost onClick={launchRequestDemoFlow}>
          {tc('request_demo')}
        </TopBannerButton>
        <TopBannerButton onClick={launchUpgradeAccountFlow}>{tc('upgrade_account')}</TopBannerButton>
      </div>
    </TopBanner>
  );
};
