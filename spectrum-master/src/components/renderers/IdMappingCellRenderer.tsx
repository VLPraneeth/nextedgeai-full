import Icon from 'antd/lib/icon';

import { CopyToClipboard } from 'components/copy-to-clipboard/CopyToClipboard';
import { HStack, Stack } from 'components/layout';
import Popover from 'components/Popover';
import ShowWhiteSpaceChars from 'components/ShowWhiteSpaceChars';
import { tNamespaced } from 'utils/i18nUtil';

import './IdMappingCellRenderer.less';

const tn = tNamespaced('TableCellRenderers.IdMappingCell');

type EntityIdMapping = Record<string, string>;

export interface IdMappingListItemProps {
  synapseName: string;
  externalId: string;
}

const IdMappingListItem = ({ synapseName, externalId }: IdMappingListItemProps) => (
  <li className="syncari-id-mapping-list-item">
    <Stack spacing="xxxs">
      <span className="id-mapping-synapse-name">{synapseName}:&nbsp;</span>
      <HStack align="center" spacing="xxxs">
        <ShowWhiteSpaceChars className="id-mapping-external-id">{externalId}</ShowWhiteSpaceChars>
        <CopyToClipboard textToCopy={externalId} />
      </HStack>
    </Stack>
  </li>
);

export interface IdMappingListProps {
  mapping: EntityIdMapping;
}

const IdMappingList = ({ mapping }: IdMappingListProps) => (
  <ul className="syncari-id-mapping-list">
    {Object.entries(mapping).map(([synapseName, externalId]) => (
      <IdMappingListItem key={externalId} synapseName={synapseName} externalId={externalId} />
    ))}
  </ul>
);

export interface IdMappingCellProps {
  mapping?: EntityIdMapping;
}

export const IdMappingCell = ({ mapping: maybeMapping }: IdMappingCellProps) => {
  const mapping = maybeMapping || {};
  const mappingEntries = Object.entries(mapping);

  if (mappingEntries.length < 1) {
    return null;
  }

  const MappingNode = <IdMappingList mapping={mapping} />;

  return (
    <Popover title="Id Mappings" content={MappingNode}>
      {tn('id_mappings_count', { count: mappingEntries.length })}
      <Icon className="id-mapping-info" type="info-circle" />
    </Popover>
  );
};

const IdMappingCellRenderer = (mapping: EntityIdMapping) => <IdMappingCell mapping={mapping} />;

export default IdMappingCellRenderer;
