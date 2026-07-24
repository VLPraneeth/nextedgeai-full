import { Button, message } from 'antd';
import { RcFile } from 'antd/lib/upload';
import { useState } from 'react';
import { createPortal } from 'react-dom';

import Can from 'components/Can';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { useEnhancedDispatch } from 'hooks/redux';
import { createHTTPCustomSynapse, updateHTTPCustomSynapse } from 'store/custom-synapse/http/thunks';
import { useGetAllCustomSynapseListQuery } from 'store/custom-synapse/sdk/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { CustomSynapse } from '../types';
import { HTTPCustomSynapseDetails } from './HTTPCustomSynapseDetails';

export interface HTTPCustomSynapseReviewProps extends SkullRenderTypeBaseProps {
  defaultValue: CustomSynapse & { iconFile?: RcFile };
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

const HTTPCustomSynapseReview = ({ defaultValue: synapse }: HTTPCustomSynapseReviewProps) => {
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
                await dispatch(updateHTTPCustomSynapse(synapse)).unwrap();
              } else {
                await dispatch(createHTTPCustomSynapse(synapse)).unwrap();
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
      <HTTPCustomSynapseDetails synapse={synapse} />

      {footerPortal}
    </Stack>
  );
};

export default HTTPCustomSynapseReview;
