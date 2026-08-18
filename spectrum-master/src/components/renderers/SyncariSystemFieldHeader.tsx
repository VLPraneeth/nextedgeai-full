import { IHeaderParams } from 'ag-grid-community';

import BrandLogo from 'components/brand/BrandLogo';
import { HStack } from 'components/layout';

import './SyncariSystemFieldHeader.less';

const SyncariSystemFieldHeader = ({ displayName, ...props }: IHeaderParams) => {
  return (
    <HStack spacing="xs">
      <span className="header-syncari-icon">
        <BrandLogo variant="mark" height="1rem" width="1rem" />
      </span>
      <div className="system-field-header">{displayName}</div>
    </HStack>
  );
};

export default SyncariSystemFieldHeader;
