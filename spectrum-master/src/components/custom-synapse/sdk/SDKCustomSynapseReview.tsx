import { Button } from 'antd';
import { useState } from 'react';
import { createPortal } from 'react-dom';

import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { Text } from 'components/typography';
import { useSDKCustomSynapseStudio } from 'pages/connector/custom-synapse/sdk/SDKCustomSynapses.hook';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

import { SDKCustomSynapseFileState } from './SDKCustomSynapseFileUpload';

export interface SDKCustomSynapseReviewProps extends SkullRenderTypeBaseProps {
  defaultValue: SDKCustomSynapseFileState;
}

const tn = tNamespaced('CustomSynapse');

const SDKCustomSynapseReview = ({ defaultValue }: SDKCustomSynapseReviewProps) => {
  const { close, previous } = useSkullConfigContext();
  const { submitForApproval } = useSDKCustomSynapseStudio();

  const [submitting, setSubmitting] = useState(false);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tCommon('cancel')}</Button>

      <Button onClick={previous}>{tCommon('previous')}</Button>

      <Button
        type="primary"
        loading={submitting}
        onClick={async () => {
          setSubmitting(true);
          await submitForApproval(defaultValue.id as string, defaultValue.displayName);
          setSubmitting(false);
          close();
        }}>
        {tn('submit_for_review')}
      </Button>
    </>,
    footerRootNode
  );

  return (
    <Stack>
      <InfoBox message={tn('submit_for_review')} description={tn('submit_for_review_description')} />
      <InputWithLabel label={tCommon('display_name')} input={<Text>{defaultValue.displayName}</Text>} />
      <InputWithLabel label={tCommon('api_name')} input={<Text>{defaultValue.name}</Text>} />
      {footerPortal}
    </Stack>
  );
};

export default SDKCustomSynapseReview;
