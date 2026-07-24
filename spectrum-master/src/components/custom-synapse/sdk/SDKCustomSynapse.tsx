import { CustomSynapseRenderComponents } from 'pages/connector/custom-synapse/sdk/SDKCustomSynapse.skull';

import CustomSynapseAuthenticationTest from './SDKCustomSynapseAuthenticationTest';
import CustomSynapseFileUpload, { SkullFileUploadProps } from './SDKCustomSynapseFileUpload';
import CustomSynapseReview from './SDKCustomSynapseReview';
import { SDKCustomSynapseSharingOptions } from './SDKCustomSynapseSharingOptions';

export interface SDKCustomSynapseProps extends SkullFileUploadProps {
  componentName: CustomSynapseRenderComponents;
}

const SDKCustomSynapse = ({ componentName, ...rest }: SDKCustomSynapseProps) => {
  switch (componentName) {
    case CustomSynapseRenderComponents.CREATE:
      return <CustomSynapseFileUpload {...rest} />;

    case CustomSynapseRenderComponents.AUTHENTICATE:
      return <CustomSynapseAuthenticationTest {...rest} />;

    case CustomSynapseRenderComponents.SHARE:
      return <SDKCustomSynapseSharingOptions {...rest} />;

    case CustomSynapseRenderComponents.REVIEW:
      return <CustomSynapseReview {...rest} />;
  }
};

export default SDKCustomSynapse;
