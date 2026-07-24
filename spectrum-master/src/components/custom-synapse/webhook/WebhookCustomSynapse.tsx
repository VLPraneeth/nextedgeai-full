import { CustomSynapseRenderComponents } from 'pages/connector/custom-synapse/sdk/SDKCustomSynapse.skull';

import {
  WebhookCustomSynapseConfigureStep,
  WebhookCustomSynapseConfigurationProps,
} from './WebhookCustomSynapseConfigureStep';
import { WebhookCustomSynapsePayloadConfigStep } from './WebhookCustomSynapsePayloadConfigStep';
import { WebhookCustomSynapseResponseStep } from './WebhookCustomSynapseResponseStep';
import WebhookCustomSynapseReview from './WebhookCustomSynapseReview';
import { WebhookCustomSynapseTestPayloadStep } from './WebhookCustomSynapseTestPayloadStep';

export interface WebhookCustomSynapseProps extends WebhookCustomSynapseConfigurationProps {
  componentName: CustomSynapseRenderComponents;
}

const WebhookCustomSynapse = ({ componentName, ...rest }: WebhookCustomSynapseProps) => {
  switch (componentName) {
    case CustomSynapseRenderComponents.CREATE:
      return <WebhookCustomSynapseConfigureStep {...rest} />;

    case CustomSynapseRenderComponents.PAYLOAD_CONFIG:
      return <WebhookCustomSynapsePayloadConfigStep {...rest} />;

    case CustomSynapseRenderComponents.RESPONSE:
      return <WebhookCustomSynapseResponseStep {...rest} />;

    case CustomSynapseRenderComponents.TEST_PAYLOAD:
      return <WebhookCustomSynapseTestPayloadStep {...rest} />;

    case CustomSynapseRenderComponents.REVIEW:
      return <WebhookCustomSynapseReview {...rest} />;
  }
};

export default WebhookCustomSynapse;
