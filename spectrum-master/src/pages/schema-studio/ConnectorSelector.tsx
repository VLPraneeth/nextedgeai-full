import cx from 'classnames';
import { ReactElement, useMemo } from 'react';

import Select, { Option } from 'components/inputs/Select';
import { OptionItem } from 'components/toolbar/Dropdown';
import { useConnectorMetadataMap } from 'store/connectors';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { tCommon, tNamespaced } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { Connector } from './types';

import './ConnectorSelector.scss';

interface ConnectorSelectorProps {
  selected?: Connector;
  connectors: Connector[];
  onChange: (connector: Connector) => void;
}

const tn = tNamespaced('Connector');

const ConnectorSelector = ({ selected, connectors, onChange }: ConnectorSelectorProps) => {
  const connectorMetadataMap = useConnectorMetadataMap();

  const dropdownOptions = connectors.map((connector) => {
    const isDraft = connectorIsCustomDraft(connectorMetadataMap[connector.metadataId]);
    return {
      ...connector,
      value: connector.id,
      label: `${connector.displayName} (${connector.name})`,
      textTag: isDraft ? tCommon('draft') : '',
      isDraft,
      isStandard: !Boolean(connectorMetadataMap[connector.metadataId]?.custom),
    };
  });

  const isSelectedStandard = useMemo(() => !Boolean(selected && connectorMetadataMap[selected.metadataId]?.custom), [
    connectorMetadataMap,
    selected,
  ]);

  return (
    <Select
      className={cx('connector-selector', {
        'connector-selector--standard': isSelectedStandard,
      })}
      value={selected?.id}
      data-userflow-tag={UserflowTags.SchemaStudio.SynapseSelector}
      placeholder={tn('select_a_connector')}
      showSearch
      filterOption={(input, option: ReactElement) => {
        const displayValue = option.props.children?.props?.title;
        if (displayValue) {
          return displayValue?.toLowerCase().includes(input?.toLowerCase());
        }
        return false;
      }}
      options={dropdownOptions.map((item) => {
        return (
          <Option value={item.value} key={item.value}>
            <OptionItem
              title={item.name}
              icon={item.icon}
              isSelected={false}
              textTag={item.textTag}
              tagColor={item.isDraft ? 'orange' : 'dark-gray'}
            />
          </Option>
        );
      })}
      onChange={(value) => {
        const connector = connectors.find((connector) => connector.id === value);
        if (connector) {
          onChange(connector);
        }
      }}
    />
  );
};

export default ConnectorSelector;
