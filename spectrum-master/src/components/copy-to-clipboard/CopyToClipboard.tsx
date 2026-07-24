import { message, Tooltip } from 'antd';
import { TooltipPlacement } from 'antd/lib/tooltip';
import cx from 'classnames';
import { MouseEvent } from 'react';

import { ReactComponent as ClipboardIcon } from 'assets/icons/record-transactions.svg';
import { IconButton } from 'components/Button';
import { tNamespaced } from 'utils/i18nUtil';
import { copyStringToClipboard } from 'utils/StringUtil';

import './CopyToClipboard.scss';

const t = tNamespaced('CopyToClipboard');

interface CopyToClipboardProps {
  iconColor?: 'white';
  textLabel?: string;
  textToCopy: string;
  tooltipPlacement?: TooltipPlacement;
  customClipboardIcon?: React.FC<React.SVGProps<SVGSVGElement>>;
}

/**
 * React component to copy text to the system clipboard
 * @prop iconColor 'white' or undefined for now, defaults to black
 * @prop textLabel optional descriptor of the copied text that appears in the confirmation message, e.g. "Client ID".
 * @prop textToCopy String to be copied
 * @prop tooltipPlacement antd TooltipPlacement
 */
export const CopyToClipboard = ({
  iconColor,
  textLabel = 'text',
  textToCopy,
  tooltipPlacement,
  customClipboardIcon,
}: CopyToClipboardProps) => {
  const copyToClipboard = async (e: MouseEvent<HTMLButtonElement>) => {
    try {
      await copyStringToClipboard(textToCopy);
      message.success(t('confirmation', { text: textLabel }));
    } catch (err) {
      message.error(t('error'));
    }
  };

  const classes = cx('copy-to-clipboard', {
    'copy-to-clipboard--white': iconColor === 'white',
  });

  return (
    <Tooltip title={t('tooltip')} placement={tooltipPlacement}>
      <IconButton className={classes} icon={customClipboardIcon ?? ClipboardIcon} onClick={copyToClipboard} />
    </Tooltip>
  );
};
