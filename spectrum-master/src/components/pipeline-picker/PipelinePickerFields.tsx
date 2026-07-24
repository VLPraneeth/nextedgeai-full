import Search from 'antd/lib/input/Search';
import * as React from 'react';
import { useMemo, useState } from 'react';

import { useI18nContext } from 'components/I18nProvider';
import Select, { Option } from 'components/inputs/Select';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import useDataTypeOptionsFromFields from 'hooks/useDataTypeOptionsFromFields';
import AppConstants from 'utils/AppConstants';

import { DATA_TYPE_ALL_FILTER_VALUE } from './PipelinePicker';
import { filterFieldsByTextAndDataType } from './PipelinePicker.hooks';
import { PipelinePickerEntity } from './PipelinePicker.types';
import PipelinePickerField from './PipelinePickerField';

export interface PipelinePickerFieldsProps {
  entity: PipelinePickerEntity;
  selectedItems: Record<string, boolean>;
  updateSelectedItem: (checked: boolean, entityId: string, fieldId?: string | undefined, fieldAlias?: string) => void;
  hasTopFilter?: boolean;
  disabled?: boolean;
  hideOpenInNewTab?: boolean;
  hideDataTypeFilter?: boolean;
  withFieldAlias?: boolean;
}

const PipelinePickerFields = ({
  entity,
  selectedItems,
  updateSelectedItem,
  hasTopFilter = false,
  hideOpenInNewTab = false,
  hideDataTypeFilter = false,
  withFieldAlias = false,
  disabled = false,
}: PipelinePickerFieldsProps) => {
  const { tc, tn } = useI18nContext();

  const [filterText, setFilter] = useState('');
  const [filterDataType, setDataFilter] = useState(DATA_TYPE_ALL_FILTER_VALUE);
  const hasFilter = !!filterText || filterDataType !== DATA_TYPE_ALL_FILTER_VALUE;

  const dataTypeOptions = useDataTypeOptionsFromFields(entity.fields);

  const filteredFields = useMemo(() => {
    return hasTopFilter ? entity.fields : filterFieldsByTextAndDataType(entity.fields, filterText, filterDataType);
  }, [filterDataType, filterText, entity.fields, hasTopFilter]);

  return (
    <div className="synri-field-pipeline-container">
      {!hasTopFilter && (
        <HStack className="synri-field-pipeline-filter-container" spacing="md">
          <Search
            className="synri-field-pipeline-filter"
            value={filterText}
            disabled={disabled}
            onKeyDown={(e) => {
              if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.escape) {
                setFilter('');
              }
            }}
            placeholder={tc('filter')}
            onChange={(e) => setFilter(e.target.value)}
          />

          {!hideDataTypeFilter && (
            <Select
              showSearch
              className="synri-field-pipeline-data-filter"
              value={filterDataType}
              disabled={disabled}
              onChange={setDataFilter}
              filterOption={(input, option) =>
                !!option.props.value?.toString().toLowerCase().includes(input.toLowerCase())
              }>
              <Option value="all">{tn('all_datatypes')}</Option>
              {dataTypeOptions}
            </Select>
          )}

          {hasFilter && (
            <button
              className="synri-link-button"
              onClick={() => {
                setFilter('');
                setDataFilter(DATA_TYPE_ALL_FILTER_VALUE);
              }}>
              <TranslatedText noWrap size="sm" text="clear_filters" />
            </button>
          )}
        </HStack>
      )}
      {filteredFields.map((field) => (
        <PipelinePickerField
          key={field.id}
          entity={entity}
          field={field}
          selected={selectedItems[field.id]}
          disabled={disabled}
          updateSelectedItem={updateSelectedItem}
          hideOpenInNewTab={hideOpenInNewTab}
          withFieldAlias={withFieldAlias}
        />
      ))}
    </div>
  );
};

export default PipelinePickerFields;
