import BreadcrumbMenu from 'components/BreadcrumbMenu';
import ChangeAwareLink, { ChangeAwareLinkProps } from 'components/ChangeAwareLink';
import { CopyToClipboardTooltip } from 'components/copy-to-clipboard/CopyToClipboardTooltip';
import { DropdownDisclosureArrow } from 'components/dropdown-disclosure-arrow/DropdownDisclosureArrow';
import { FilteredDropdownItemProps } from 'components/FilteredDropdown/FilteredDropdownItem';
import './BreadcrumbLink.scss';

export interface BreadcrumbLinkProps extends ChangeAwareLinkProps {
  breadcrumbMenuItems?: FilteredDropdownItemProps[];
  tooltipClipboard?: string;
}

export const BreadcrumbLink = ({ breadcrumbMenuItems, tooltipClipboard = '', ...props }: BreadcrumbLinkProps) => {
  return (
    <BreadcrumbMenu items={breadcrumbMenuItems} className="breadcrumb-link">
      <div className="breadcrumb-link--menu-trigger">
        <CopyToClipboardTooltip textToCopy={tooltipClipboard}>
          <ChangeAwareLink {...props} />
          {breadcrumbMenuItems && <DropdownDisclosureArrow isOpen={false} closeMargin />}
        </CopyToClipboardTooltip>
      </div>
    </BreadcrumbMenu>
  );
};
