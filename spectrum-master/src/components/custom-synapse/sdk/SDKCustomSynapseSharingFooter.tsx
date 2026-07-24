import { Button } from 'antd';
import { createPortal } from 'react-dom';

import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { tc, tCommon } from 'utils/i18nUtil';

export interface SDKCustomSynapseSharingFooterProps {
  onPrevious: (gotoPrevious: () => void) => void;
  onNext: (gotoNext: () => void) => void;
  loadingNext: boolean;
}

export const SDKCustomSynapseSharingFooter = ({
  onPrevious,
  onNext,
  loadingNext,
}: SDKCustomSynapseSharingFooterProps) => {
  const { close, next, previous } = useSkullConfigContext();

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  return createPortal(
    <>
      <Button onClick={() => close()}>{tCommon('cancel')}</Button>

      <Button onClick={() => onPrevious(previous)}>{tCommon('previous')}</Button>

      <Button type="primary" onClick={() => onNext(next)} loading={loadingNext}>
        {tc('next')}
      </Button>
    </>,
    footerRootNode
  );
};
