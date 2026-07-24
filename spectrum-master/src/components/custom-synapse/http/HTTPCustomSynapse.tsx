import { CustomSynapseRenderComponents } from 'pages/connector/custom-synapse/sdk/SDKCustomSynapse.skull';

import HTTPCustomSynapseAuthStep from './HTTPCustomSynapseAuthStep';
import { HTTPCustomSynapseConfigureStep, HTTPCustomSynapseConfigureStepProps } from './HTTPCustomSynapseConfigureStep';
import HTTPCustomSynapseReview from './HTTPCustomSynapseReview';

export interface HTTPCustomSynapseProps extends HTTPCustomSynapseConfigureStepProps {
  componentName: CustomSynapseRenderComponents;
}

const HTTPCustomSynapse = ({ componentName, ...rest }: HTTPCustomSynapseProps) => {
  switch (componentName) {
    case CustomSynapseRenderComponents.CREATE:
      return <HTTPCustomSynapseConfigureStep {...rest} />;

    case CustomSynapseRenderComponents.AUTHENTICATE:
      return <HTTPCustomSynapseAuthStep {...rest} />;

    case CustomSynapseRenderComponents.REVIEW:
      return <HTTPCustomSynapseReview {...rest} />;
  }
};

export default HTTPCustomSynapse;
