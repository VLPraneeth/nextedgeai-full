import { Tooltip } from 'antd';
import Icon from 'antd/lib/icon';
import { useUID } from 'react-uid';

import FieldTypeBadge from 'components/FieldTypeBadge';
import Filter, { FilterProps } from 'components/inputs/filter';
import { HStack, Stack } from 'components/layout';
import Popover from 'components/Popover';
import { EnhancedAgCellRendererParams } from 'components/renderers';
import TransactionDate from 'components/renderers/TransactionDate';
import { FieldDataType } from 'components/types';
import { Text } from 'components/typography';
import { EntityField } from 'store/entity/types';
import { usePicklistValues } from 'store/picklists/hooks';
import {
  Transaction,
  TransactionChange,
  TransactionExternalValue,
  TransactionFieldMergePolicy,
} from 'store/transactions';
import AppConstants from 'utils/AppConstants';
import { noop } from 'utils/AppUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { safeJoinWithComma } from 'utils/StringUtil';

import '../DataStudioLineagePanel.less';

const tn = tNamespaced('Transaction');
export type TransactionMergePredicateProps = {
  name: string;
  value: FilterProps['value'];
};

export const TransactionMergePredicate = ({ name, value }: TransactionMergePredicateProps) => {
  const [picklistValues, fetchPicklistValues] = usePicklistValues();

  return (
    <Filter
      name={name}
      displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
      fetchPicklistValues={fetchPicklistValues}
      fieldValues={[]}
      onChange={noop}
      onDelete={undefined}
      picklistValues={picklistValues}
      value={value}
    />
  );
};

export const TransactionMergePredicateRenderer = ({
  value,
}: EnhancedAgCellRendererParams<TransactionFieldMergePolicy | undefined>) => {
  const uid = useUID();

  if (!value) {
    return null;
  }

  return (
    <HStack className="transaction-merge-predicate-cell">
      <TransactionMergePredicate name={`predicate-${uid}`} value={value.expressionMap || {}} />
    </HStack>
  );
};

export type TransactionFieldUpdate = {
  field: {
    apiName: string;
    displayName: string;
    dataType: FieldDataType;
  };
  value: unknown;
  mergePolicy: TransactionFieldMergePolicy | undefined;
};

export const TransactionMergeFieldRenderer = ({
  value,
}: EnhancedAgCellRendererParams<TransactionFieldUpdate['field']>) => {
  return <FieldWithDataType dataType={value.dataType} apiName={value.apiName} displayName={value.displayName} />;
};

export const FieldWithDataType = ({
  dataType,
  displayName,
  apiName,
}: Pick<EntityField, 'dataType' | 'apiName' | 'displayName'>) => {
  return (
    <HStack spacing="xxs">
      <FieldTypeBadge dataType={dataType} />
      <Text style={{ display: 'flex' }}>
        {displayName}
        <span style={{ fontFamily: 'SF Mono,monospace' }}>{apiName && <Text color="gray-700"> [{apiName}]</Text>}</span>
      </Text>
    </HStack>
  );
};

export const FieldNameRenderer = ({ value }: EnhancedAgCellRendererParams<TransactionChange, TransactionChange>) => {
  return (
    <div className="field-name-renderer-wrapper">
      <FieldWithDataType dataType={value.dataType} apiName={value.apiName} displayName={value.displayName} />
    </div>
  );
};

export const ConnectorFieldPopover = ({ value }: { value: TransactionExternalValue }) => {
  return (
    <Popover
      overlayClassName="connector-field-popover"
      content={
        <Stack divider>
          <Text weight="bold">{value.connectorName}</Text>
          <FieldWithDataType dataType={value.dataType} apiName={value.apiName} displayName={value.displayName} />
        </Stack>
      }>
      <Icon type="info-circle" />
    </Popover>
  );
};

const ValueRenderer = (val: TransactionExternalValue | string | string[] | undefined, idx: number) => {
  if (!val) {
    return null;
  }

  if (typeof val === 'string' || typeof val === 'number') {
    return <Text key={idx}>{val}</Text>;
  }

  if (Array.isArray(val)) {
    return <Text key={idx}>{safeJoinWithComma(...val)}</Text>;
  }

  let displayValue = val.value;
  if (typeof displayValue !== 'string') {
    // If the value is not a string it is assumed to be an array of
    // NetsuiteLineItems.
    displayValue = JSON.stringify(val.value);
  }

  return (
    <HStack key={`${val.connectorId}-${val.fieldId}`} spacing="xxs">
      <Text color="gray-700" weight="bold">
        {val.connectorName}&nbsp;
        <ConnectorFieldPopover value={val} />
        &nbsp;:
      </Text>
      <Tooltip
        title={
          val.timestamp ? (
            <Text>
              {tn('destination_updated_at')} <TransactionDate text={val.timestamp} />
            </Text>
          ) : null
        }>
        <Text>{displayValue}</Text>
      </Tooltip>
    </HStack>
  );
};

export const ExternalValuesRenderer = ({
  value: externalValues = [],
}: EnhancedAgCellRendererParams<TransactionExternalValue[], Transaction>) => {
  if (!externalValues?.length) {
    return null;
  }

  return (
    <Stack className="external-value-renderer" spacing={externalValues?.length <= 1 ? 'z' : 'xxxs'}>
      {externalValues.map(ValueRenderer)}
    </Stack>
  );
};

export const OldNewValuesRenderer = ({
  value = [],
}: EnhancedAgCellRendererParams<TransactionExternalValue[], Transaction>) => {
  const displayValue = typeof value === 'string' ? value : JSON.stringify(value);
  return (
    <Text color="gray-700" weight="bold">
      {displayValue}
    </Text>
  );
};
