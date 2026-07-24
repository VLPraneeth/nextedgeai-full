import InlineSVG from 'components/icons/InlineSvg';

import SuccessCheck from '../assets/icons/success-check.svg';
import { Stack } from './layout';
import CenterLayout from './layout/CenterLayout';
import Text from './typography/Text';

import './ConfirmationInfoBox.less';

export interface ConfirmationInfoBoxProps {
  message: string;
  description: string;
}

const ConfirmationInfoBox = ({ message, description }: ConfirmationInfoBoxProps) => {
  return (
    <CenterLayout className="confirmation-container">
      <Stack center spacing="sm">
        <InlineSVG className="success-check-icon" title="success-check" src={SuccessCheck} />
        <Text size="xxl" className="success-text">
          {message}
        </Text>
        <Text size="md">{description}</Text>
      </Stack>
    </CenterLayout>
  );
};

export default ConfirmationInfoBox;
