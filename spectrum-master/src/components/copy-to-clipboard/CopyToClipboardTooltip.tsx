import { Tooltip } from 'antd';

import { HStack } from 'components/layout';

import { CopyToClipboard } from './CopyToClipboard';

import './CopyToClipboardTooltip.scss';

/**
 * React component to use CopyToClipboard inside a tooltip
 */
export const CopyToClipboardTooltip = ({
  textToCopy,
  children,
}: {
  textToCopy: string;
  children?: React.ReactNode;
}) => {
  // If no text, just return children with no tooltip
  if (!textToCopy) {
    return <>{children}</>;
  }

  return (
    <Tooltip
      mouseEnterDelay={0.5}
      overlayClassName="copy-to-clipboard-tooltip"
      title={
        <HStack spacing="xs" className="copy-to-clipboard-tooltip__container">
          {textToCopy}
          <CopyToClipboard iconColor="white" textToCopy={textToCopy} tooltipPlacement="right" />
        </HStack>
      }>
      {children}
    </Tooltip>
  );
};
