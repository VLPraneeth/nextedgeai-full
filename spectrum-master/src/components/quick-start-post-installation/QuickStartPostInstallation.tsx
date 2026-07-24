import { withI18n } from 'components/I18nProvider';
import InlineSVG from 'components/icons/InlineSvg';
import { HStack, Stack } from 'components/layout';
import { Text, TranslatedText } from 'components/typography';

import SuccessCheck from '../../assets/icons/success-check.svg';

export interface QuickStartPostInstallationProps {
  postInstallMessage: string;
}

const QuickStartPostInstallation = ({ postInstallMessage }: QuickStartPostInstallationProps) => {
  return (
    <Stack spacing="sm">
      <Stack spacing="xs" className="synri-post-install-bottom-border">
        <HStack spacing="xs" align="center">
          <InlineSVG className="synri-issue-resolution-check-icon" title="success-check" src={SuccessCheck} />
          <TranslatedText color="syncari-blue" size="xl" text="notify_when_ready" />
        </HStack>
        <TranslatedText color="black" text="post_install_description" />
      </Stack>
      {postInstallMessage && (
        <Stack spacing="z" className="synri-post-install-bottom-border">
          <TranslatedText color="gray-750" text="post_install_message" />
          <Text color="black" beDangerous>
            {postInstallMessage}
          </Text>
        </Stack>
      )}
    </Stack>
  );
};

export default withI18n(QuickStartPostInstallation, 'QuickStart');
