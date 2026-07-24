import { HStack } from 'components/layout';
import { Text } from 'components/typography';

export type NavigateToStepHandler = (stepNumber: number) => void;

export interface JumpToStepLabelProps {
  text: string;
  buttonText: string;
  stepNumber: number;
  navigateToStep: NavigateToStepHandler;
}

const JumpToStepLabel = ({ text, stepNumber, buttonText, navigateToStep }: JumpToStepLabelProps) => {
  return (
    <HStack align="baseline" spacing="xs" className="synri-jump-to-step-container">
      <Text color="gray-850" size="md" weight="semibold">
        {text}
      </Text>
      <button className="synri-link-button" onClick={() => navigateToStep(stepNumber)}>
        {buttonText}
      </button>
    </HStack>
  );
};

export default JumpToStepLabel;
