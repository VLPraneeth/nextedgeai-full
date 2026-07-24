import { ReactComponent as HelpOutlineIcon } from 'assets/icons/help-outline.svg';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import './HelpLink.scss';

const DefaultHelpLinkIcon = <HelpOutlineIcon width={16} height={16} />;

type AnchorProps = Omit<JSX.IntrinsicElements['a'], 'href' | 'rel'>;

export type HelpLinkProps = AnchorProps & {
  helpPath?: string;
  href?: string;
};

const HelpLink = ({
  className = 'help-link',
  children = DefaultHelpLinkIcon,
  helpPath,
  target = '_blank',
  href,
  ...props
}: HelpLinkProps) => {
  function getHref() {
    if (helpPath) {
      return makeUrl(DataUrlConstants.SUPPORT_LINK, { helpPath });
    }
    if (href) {
      return href;
    }
    return '#';
  }

  return (
    <a className={className} rel="noreferrer" target={target} href={getHref()} {...props}>
      {children}
      Help
    </a>
  );
};

export default HelpLink;
