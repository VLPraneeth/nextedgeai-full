import { useEnhancedSelector } from 'hooks/redux';
import { selectUserGhosted, selectUserEmail, selectUserFullName } from 'store/user/selectors';
import { tNamespaced } from 'utils/i18nUtil';

import { TopBanner, TopBannerTypes } from './top-banner/TopBanner';

const tn = tNamespaced('Banners');

const GhostUserBanner = () => {
  const ghosted = useEnhancedSelector(selectUserGhosted);
  const fullName = useEnhancedSelector(selectUserFullName);
  const email = useEnhancedSelector(selectUserEmail);

  if (!ghosted) {
    return null;
  }

  return <TopBanner type={TopBannerTypes.Ghosted}>{tn('ghosted', { fullName, email })}</TopBanner>;
};

export default GhostUserBanner;
