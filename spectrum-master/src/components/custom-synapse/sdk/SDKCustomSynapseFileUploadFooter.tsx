import { Button } from 'antd';
import { createPortal } from 'react-dom';

import { useI18nContext } from 'components/I18nProvider';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { tCommon } from 'utils/i18nUtil';

export interface SDKCustomSynapseFileUploadFooterProps {
  processing?: boolean;
  editing?: boolean;
  hasChanges?: boolean;
  disableNextButton?: boolean;
  onSave: () => void;
}

export const SDKCustomSynapseFileUploadFooter = ({
  processing,
  hasChanges,
  disableNextButton,
  editing,
  onSave,
}: SDKCustomSynapseFileUploadFooterProps) => {
  const { tn } = useI18nContext();
  const { close, next } = useSkullConfigContext();

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  let buttonText = tn('create_custom_synapse');
  let actionButtonAction = onSave;
  if (editing) {
    if (processing) {
      buttonText = tn('processing_synapse');
    } else if (hasChanges) {
      buttonText = tn('update_draft');
    } else {
      buttonText = tCommon('next');
      actionButtonAction = next;
    }
  }

  return createPortal(
    <>
      <Button key="cancel" onClick={close}>
        {tCommon('cancel')}
      </Button>

      <Button
        type="primary"
        loading={processing}
        disabled={processing || disableNextButton}
        onClick={actionButtonAction}>
        {buttonText}
      </Button>
    </>,
    footerRootNode
  );
};
