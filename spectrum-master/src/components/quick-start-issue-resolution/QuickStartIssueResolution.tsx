import Button from 'components/Button';
import { withI18n } from 'components/I18nProvider';
import InlineSVG from 'components/icons/InlineSvg';
import { HStack, Stack } from 'components/layout';
import { Text, TranslatedText } from 'components/typography';

import SuccessCheck from '../../assets/icons/success-check.svg';

export interface QuickStartIssueResolutionProps {
  successTitle: string;
  successMessage: string;
  navigateToStep: () => void;
}

const QuickStartIssueResolution = ({
  successTitle,
  successMessage,
  navigateToStep,
}: QuickStartIssueResolutionProps) => {
  return (
    <Stack spacing="lg">
      <Stack spacing="xs">
        <HStack spacing="xs">
          <InlineSVG className="synri-issue-resolution-check-icon" title="success-check" src={SuccessCheck} />
          <Text color="gray-900" size="lg" weight="semibold">
            {successTitle}
          </Text>
        </HStack>
        <Text color="gray-850">{successMessage}</Text>
      </Stack>
      <Button type="primary" onClick={navigateToStep}>
        <TranslatedText namespace="Common" text="continue" />
      </Button>
    </Stack>
  );
};

export default withI18n(QuickStartIssueResolution, 'QuickStart');
