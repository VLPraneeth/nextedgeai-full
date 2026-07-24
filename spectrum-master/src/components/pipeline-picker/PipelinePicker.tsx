import { Alert, Icon, Spin, Tooltip } from 'antd';
import Search from 'antd/lib/input/Search';
import produce from 'immer';
import { filter, find, keyBy, mapValues, merge } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { NoRowsOverlay } from 'components/AgTable';
import Button from 'components/Button';
import Checkbox from 'components/Checkbox';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import Select, { Option } from 'components/inputs/Select';
import { HStack } from 'components/layout';
import { TextTag } from 'components/text-tag';
import { TranslatedText } from 'components/typography';
import useDataTypeOptionsFromFields from 'hooks/useDataTypeOptionsFromFields';
import { createIdWithAlias } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import AppConstants from 'utils/AppConstants';

import TreeSkeleton from '../tree-skeleton';
import { SelectedStatBlocks } from './PipelinePicker.components';
import { filterFieldsByTextAndDataType, usePipelinePickerEntities } from './PipelinePicker.hooks';
import { PipelinePickerEntity, ppEntityGuard, ppFieldGuard } from './PipelinePicker.types';
import PipelinePickerFields from './PipelinePickerFields';

const getDefaultSelectedFieldsForEntities = (entities: PipelinePickerEntity[], selected = false) => {
  const entityObject = keyBy(entities, 'id');
  return mapValues(entityObject, (entity) => {
    const fields = mapValues(keyBy(entity.fields, 'id'), () => selected);
    return {
      selected,
      fields,
    };
  });
};

export const DATA_TYPE_ALL_FILTER_VALUE = 'all';

export type PipelinePickerValue = {
  entities: PipelinePickerEntity[];
};

export interface PipelinePickerProps {
  entities: PipelinePickerEntity[];
  hasChanges?: boolean;
  value?: PipelinePickerValue;
  defaultValue?: PipelinePickerValue;
  onChange?: (value: PipelinePickerValue) => void;
  hideOpenInNewTab?: boolean;
  hideSelectionSummary?: boolean;
  hideDataTypeFilter?: boolean;
  withFieldAlias?: boolean;
}

const PipelinePicker = ({
  entities,
  value,
  hasChanges,
  defaultValue,
  onChange,
  hideOpenInNewTab = false,
  hideSelectionSummary = false,
  hideDataTypeFilter = false,
  withFieldAlias = false,
}: PipelinePickerProps) => {
  const { tc, tn } = useI18nContext();
  value = useMemo(() => value || defaultValue, [value, defaultValue]);

  const [topLevelFilter, setTopLevelFilter] = useState('');
  const [topLevelDataFilter, setTopLevelDataFilter] = useState(DATA_TYPE_ALL_FILTER_VALUE);

  const hasTopFilter = !!topLevelFilter || topLevelDataFilter !== DATA_TYPE_ALL_FILTER_VALUE;

  const hasAtLeastOnePublishedPipeline = Boolean(entities.length);

  // If there are no published pipelines then the pipelines for this QS have
  // definitely changed. If there are no published pipelines we disable setting
  // changesNeedAck to false.

  const [changesNeedAck, setChangesNeedAck] = useState(hasChanges || !hasAtLeastOnePublishedPipeline);

  const availableEntities = (changesNeedAck ? value?.entities : entities) || EMPTY_ARRAY;

  const allFields = availableEntities.flatMap((entity) => entity.fields);

  const dataTypeOptions = useDataTypeOptionsFromFields(allFields);

  const [selectedItems, setSelectedItems] = useState(() => {
    return getDefaultSelectedFieldsForEntities(availableEntities);
  });

  // Initialize the selected items if the new entity
  // was never initialized.
  useEffect(() => {
    let newSelectedItems = {};
    entities.forEach((entity) => {
      if (!selectedItems[entity.id]) {
        newSelectedItems = { ...getDefaultSelectedFieldsForEntities([entity]), ...newSelectedItems };
      }
    });
    if (Object.keys(newSelectedItems).length) {
      setSelectedItems((current) => ({ ...newSelectedItems, ...current }));
    }
  }, [entities, selectedItems]);

  const [fieldAliases, setFieldAliases] = useState<Record<string, string>>({});

  const [entityCount, fieldCount] = useMemo(() => {
    const selectedEntities = filter(selectedItems, 'selected');
    const selectedFields = selectedEntities.flatMap(({ fields }) => filter(fields));

    return [selectedEntities.length, selectedFields.length];
  }, [selectedItems]);

  const updateSelectedItem = useCallback(
    (checked: boolean, entityId: string, fieldId?: string, fieldAlias?: string) => {
      setSelectedItems((currentItems) => {
        if (fieldId && fieldAlias) {
          setFieldAliases((current) => ({
            ...current,
            [createIdWithAlias(entityId, fieldId)]: fieldAlias || '',
          }));
        }
        return produce(currentItems, (draft) => {
          if (!draft[entityId]) {
            return;
          }
          if (fieldId) {
            draft[entityId].fields[fieldId] = checked;
            if (checked) {
              draft[entityId].selected = true;
            }
          } else {
            draft[entityId].selected = checked;
            // All field pipelines should match the `checked` value for the entity
            draft[entityId].fields = mapValues(draft[entityId].fields, () => checked);
          }
        });
      });
    },
    []
  );

  const [initialized, setInitialized] = useState(false);
  /**
   * On the first render we show the list of available entities. Once the schema
   * data is loaded for all entities we know what fields are mapped. This hook
   * updates the selectedItems state with a combination of the mapped fields and
   * the previously selected fields from `value.entities`
   */
  const allEntitiesAreLoaded = !availableEntities.map((entity) => entity.loading).some(Boolean);
  useEffect(() => {
    if (allEntitiesAreLoaded && !initialized) {
      setInitialized(true);
      const availableEntitiesAfterLoading = getDefaultSelectedFieldsForEntities(availableEntities);
      const selectedEntities = value ? getDefaultSelectedFieldsForEntities(value.entities, true) : EMPTY_OBJECT;
      const updatedSelectedItems = merge({}, availableEntitiesAfterLoading, selectedEntities);
      setSelectedItems(updatedSelectedItems);
    }
  }, [allEntitiesAreLoaded, availableEntities, initialized, value]);

  // Trigger `onChange` whenever the selectItems change
  useEffect(() => {
    if (!hasAtLeastOnePublishedPipeline || !allEntitiesAreLoaded || !initialized) {
      // Trigger onChange only when all the needed entities are loaded and our selected items are initialized
      return;
    }

    const values: PipelinePickerEntity[] = Object.keys(selectedItems)
      .map((key) => {
        if (selectedItems[key].selected) {
          const entity = find(entities, { id: key });
          if (!entity) {
            return undefined;
          }

          const fields = selectedItems[key].fields;
          const selectedFields = Object.keys(fields)
            .map((fieldKey) => {
              if (fields[fieldKey]) {
                const field = find(entity?.fields, { id: fieldKey });
                if (field) {
                  if (fieldAliases[createIdWithAlias(entity.id, field.id)]) {
                    return { ...field, fieldAlias: fieldAliases[createIdWithAlias(entity.id, field.id)] };
                  }
                  return { ...field };
                }
              }
              return undefined;
            })
            .filter(ppFieldGuard);
          return {
            id: key,
            apiName: entity.apiName,
            displayName: entity.displayName,
            fields: selectedFields,
          };
        }
        return undefined;
      })
      .filter(ppEntityGuard);

    onChange?.({ entities: values });
    // TODO: Entities always trigger this effect
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fieldAliases, hasAtLeastOnePublishedPipeline, onChange, selectedItems]);

  const deselectAllFields = useCallback((entityId: string) => {
    setSelectedItems((currentItems) => {
      return produce(currentItems, (draft) => {
        draft[entityId].fields = mapValues(draft[entityId].fields, () => false);
      });
    });
  }, []);

  const filteredEntities: PipelinePickerEntity[] = useMemo(() => {
    return availableEntities.map((entity) => {
      const filteredFields = filterFieldsByTextAndDataType(entity.fields, topLevelFilter, topLevelDataFilter);
      return {
        ...entity,
        fields: filteredFields,
      };
    });
  }, [availableEntities, topLevelDataFilter, topLevelFilter]);

  const treeItems = useMemo(() => {
    return filteredEntities.map((entity, index) => {
      const hidden = hasTopFilter && entity.fields.length === 0;
      const unfilteredFieldsCount = availableEntities[index].fields.length;
      const selectedFieldCount = filter((selectedItems[entity.id] || {}).fields).length;

      return {
        key: entity.id,
        hidden,
        label: (
          <Checkbox
            key={entity.id}
            className="synri-checkbox-wrapper-aligned"
            name={`checkbox-${entity.apiName}`}
            checked={(selectedItems[entity.id] || {}).selected}
            disabled={entity.loading || changesNeedAck}
            onChange={(e) => updateSelectedItem(e.target.checked, entity.id)}>
            <div className="synri-checkbox-label-row-container">
              <div className="synri-checkbox-label-container">
                <span className="synri-checkbox-label">{entity.displayName}</span>
                <span className="synri-checkbox-sublabel">({entity.apiName})</span>
                <TextTag
                  text={[
                    tn('fields_count', { count: unfilteredFieldsCount }),
                    tn('selected_count', { count: selectedFieldCount }),
                  ]}
                  color="gray"
                />
              </div>
              {!changesNeedAck && !entity.loading && (
                <div className="synri-checkbox-label-buttons">
                  <button className="synri-link-button" onClick={() => updateSelectedItem(true, entity.id)}>
                    <TranslatedText size="sm" text="select_all_fields" />
                  </button>
                  <button className="synri-link-button" onClick={() => deselectAllFields(entity.id)}>
                    <TranslatedText size="sm" text="deselect_all_fields" />
                  </button>
                </div>
              )}
            </div>
          </Checkbox>
        ),
        children: entity.unavailableMessage ? (
          <div className="synri-unavailable-pipeline-message">
            <Alert type="info" message={entity.unavailableMessage} />
          </div>
        ) : entity.loading ? (
          <div className="synri-loading-fields-spinner">
            <Spin spinning size="small" tip={tn('loading_fields')} />
          </div>
        ) : (
          <PipelinePickerFields
            entity={entity}
            hideOpenInNewTab={hideOpenInNewTab}
            withFieldAlias={withFieldAlias}
            selectedItems={(selectedItems[entity.id] || {}).fields}
            updateSelectedItem={updateSelectedItem}
            hasTopFilter={hasTopFilter}
            disabled={changesNeedAck}
            hideDataTypeFilter={hideDataTypeFilter}
          />
        ),
      };
    });
  }, [
    availableEntities,
    changesNeedAck,
    deselectAllFields,
    filteredEntities,
    hasTopFilter,
    hideOpenInNewTab,
    hideDataTypeFilter,
    selectedItems,
    tn,
    updateSelectedItem,
    withFieldAlias,
  ]);

  return (
    <div>
      {changesNeedAck && (
        <div className="synri-changes-ack-container">
          <HStack justify="space-between">
            <HStack shrink spacing="sm">
              <Icon type="lock" theme="filled" />
              <TranslatedText color="gray-850" beDangerous text="changed_entities_description" />
            </HStack>
            <Tooltip title={!hasAtLeastOnePublishedPipeline && tn('publish_pipeline_to_update_qs')} placement="left">
              <div>
                <Button
                  type="primary"
                  disabled={!hasAtLeastOnePublishedPipeline}
                  onClick={() => {
                    setChangesNeedAck(false);
                    setSelectedItems(getDefaultSelectedFieldsForEntities(entities));
                  }}>
                  <TranslatedText text="reset_and_edit" />
                </Button>
              </div>
            </Tooltip>
          </HStack>
        </div>
      )}

      {!hideSelectionSummary && <SelectedStatBlocks entityCount={entityCount} fieldCount={fieldCount} />}

      <div className="synri-field-pipeline-top-filter-container">
        <HStack justify="space-between" grow>
          <HStack spacing="md">
            <Search
              className="synri-field-pipeline-filter"
              value={topLevelFilter}
              onKeyDown={(e) => {
                if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.escape) {
                  setTopLevelFilter('');
                }
              }}
              placeholder={tc('filter')}
              onChange={(e) => setTopLevelFilter(e.target.value)}
              disabled={changesNeedAck}
            />

            {!hideDataTypeFilter && (
              <Select<string>
                showSearch
                className="synri-field-pipeline-data-filter"
                defaultValue="all"
                onChange={setTopLevelDataFilter}
                value={topLevelDataFilter}
                disabled={changesNeedAck}
                filterOption={(input, option) =>
                  !!option.props.value?.toString().toLowerCase().includes(input.toLowerCase())
                }>
                <Option value="all">{tn('all_datatypes')}</Option>
                {dataTypeOptions}
              </Select>
            )}

            {hasTopFilter && (
              <button
                className="synri-link-button"
                onClick={() => {
                  setTopLevelFilter('');
                  setTopLevelDataFilter(DATA_TYPE_ALL_FILTER_VALUE);
                }}>
                <TranslatedText size="sm" noWrap text="clear_filters" />
              </button>
            )}
          </HStack>
        </HStack>
      </div>
      <TreeSkeleton filterActive={hasTopFilter} items={treeItems} defaultOpen={changesNeedAck} expandIconsOffset={10} />
      {treeItems.filter(({ hidden }) => !hidden).length === 0 && (
        <NoRowsOverlay description={tn('no_data_matches_filter')} />
      )}
    </div>
  );
};

export const TranslatedPipelinePicker = withI18n(PipelinePicker, 'PipelinePicker');

const PipelinePickerWrapper = (props: Omit<PipelinePickerProps, 'entities'>) => {
  const entities = usePipelinePickerEntities();
  return <TranslatedPipelinePicker {...props} entities={entities} />;
};

export default PipelinePickerWrapper;
