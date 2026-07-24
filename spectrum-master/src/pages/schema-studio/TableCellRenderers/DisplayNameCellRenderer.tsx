import * as React from 'react';

import { HStack } from 'components/layout';
import StatusBadge from 'components/StatusBadge';

import { EntityModel, FieldModel } from '../types';
import './DisplayNameCell.less';

interface DisplayNameCellProps {
  children?: React.ReactNode;
  record: EntityModel | FieldModel;
}

const DisplayNameCell = ({ children, record }: DisplayNameCellProps) => {
  return (
    <div className="schema-studio-entity-name-cell">
      <HStack>
        <span className="entity-name">{children}</span>
        {'type' in record && record.type === 'custom' && (
          <StatusBadge className="entity-badge type-badge">Custom</StatusBadge>
        )}
      </HStack>
    </div>
  );
};

const DisplayNameCellRenderer = (text: string, record: FieldModel | EntityModel) => (
  <DisplayNameCell record={record}>{text}</DisplayNameCell>
);

export default DisplayNameCellRenderer;
