import { Button, message } from 'antd';
import { RcFile } from 'antd/lib/upload';
import { useState } from 'react';
import { createPortal } from 'react-dom';

import Can from 'components/Can';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetAllCustomSynapseListQuery } from 'store/custom-synapse/sdk/api';
import { createWebhookCustomSynapse, updateWebhookCustomSynapse } from 'store/custom-synapse/webhook/thunks';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { CustomSynapse } from '../types';
import { WebhookCustomSynapseDetails } from './WebhookCustomSynapseDetails';

export interface WebhookCustomSynapseReviewProps extends SkullRenderTypeBaseProps {
  defaultValue: CustomSynapse & { iconFile?: RcFile };
}

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

const WebhookCustomSynapseReview = ({ defaultValue: synapse }: WebhookCustomSynapseReviewProps) => {
  const { close, previous } = useSkullConfigContext();
  const { refetch } = useGetAllCustomSynapseListQuery();
  const dispatch = useEnhancedDispatch();
  const [submitting, setSubmitting] = useState(false);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tc('cancel')}</Button>

      <Button onClick={previous}>{tc('previous')}</Button>

      <Can key="save" permission={AllPermissions.WRITE_CONNECTOR}>
        <Button
          type="primary"
          loading={submitting}
          onClick={async () => {
            setSubmitting(true);
            try {
              if (synapse.id) {
                await dispatch(updateWebhookCustomSynapse(synapse)).unwrap();
              } else {
                await dispatch(createWebhookCustomSynapse(synapse)).unwrap();
              }
              setSubmitting(false);
              refetch();
              close();
            } catch (err: any) {
              message.error(getRtkQueryErrorMessage(err) || tn('error_while_save'));
              setSubmitting(false);
              return;
            }
          }}>
          {synapse.id ? tc('save') : tc('create')}
        </Button>
      </Can>
    </>,
    footerRootNode
  );

  return (
    <Stack>
      <WebhookCustomSynapseDetails synapse={synapse} />

      {footerPortal}
    </Stack>
  );
};

export default WebhookCustomSynapseReview;
