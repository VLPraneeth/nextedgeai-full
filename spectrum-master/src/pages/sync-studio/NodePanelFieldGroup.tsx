import { Tooltip } from 'antd';
import * as React from 'react';

import { withI18n, useI18nContext } from 'components/I18nProvider';
import FieldGroup from 'components/inputs/FieldGroup';
import { conditionToString } from 'components/inputs/filter/utils';
import { isConditionValue } from 'components/inputs/types';
import { Text } from 'components/typography';
import AppConstants from 'utils/AppConstants';
import { capitalize } from 'utils/Fp';

import './NodePanelFieldGroup.less';

export type NodePanelFieldGroupProps = {
  className?: string;
  title: string;
  dataType?: string;
  children: React.ReactNode;
  helpText?: string;
  required?: boolean;
};

const NodePanelFieldGroup = ({
  className,
  helpText,
  title,
  dataType,
  required = false,
  children: value,
}: NodePanelFieldGroupProps) => {
  const { tn } = useI18nContext();

  return (
    <FieldGroup className={className} label={title} required={required} tooltip={helpText}>
      {typeof value === 'boolean' || Boolean(value) || value === 0 ? (
        <NodePanelFieldValue dataType={dataType}>{value}</NodePanelFieldValue>
      ) : (
        <Text color="light-gray">{tn('missing_value', { title, interpolation: { escapeValue: false } })}</Text>
      )}
    </FieldGroup>
  );
};

export type NodePanelFieldValueProps = {
  dataType?: string;
  children?: React.ReactNode;
};

const NodePanelFieldValue = ({ dataType, children: value }: NodePanelFieldValueProps) => {
  const { tc } = useI18nContext();

  if (typeof value === 'string') {
    return (
      <Tooltip title={value}>
        <Text color="gray-1000" breakWord beDangerous={dataType === AppConstants.INPUT_TYPE.EMAILBODY}>
          {value}
        </Text>
      </Tooltip>
    );
  }

  if (typeof value === 'boolean') {
    return <Text color="gray-1000">{capitalize(value ? tc('true') : tc('false'))}</Text>;
  }

  if (typeof value === 'number') {
    return <Text color="gray-1000">{value.toString()}</Text>;
  }

  if (React.isValidElement(value)) {
    return value;
  }

  if (isConditionValue(value)) {
    return (
      <Text beDangerous color="gray-1000">
        {conditionToString(value)}
      </Text>
    );
  }

  return null;
};

export default withI18n(NodePanelFieldGroup, 'NodePanel');
