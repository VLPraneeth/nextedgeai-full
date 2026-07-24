import { EntitiesRenderComponents } from 'pages/connector/custom-synapse/http/entities/Entity.skull';

import { EntityBasicStep, EntityBasicStepProps } from './EntityBasicStep';
import { EntityConfigStep } from './EntityConfigStep';
import { EntityReviewStep } from './EntityReviewStep';

export interface EntityProps extends EntityBasicStepProps {
  componentName: EntitiesRenderComponents;
}

export const Entity = ({ componentName, ...rest }: EntityProps) => {
  switch (componentName) {
    case EntitiesRenderComponents.BASIC:
      return <EntityBasicStep {...rest} />;

    case EntitiesRenderComponents.CONFIG:
      return <EntityConfigStep {...rest} />;

    case EntitiesRenderComponents.REVIEW:
      return <EntityReviewStep {...rest} />;
  }
};
