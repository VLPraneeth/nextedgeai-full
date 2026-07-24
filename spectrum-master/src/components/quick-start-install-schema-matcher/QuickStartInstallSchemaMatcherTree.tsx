import produce from 'immer';
import { find, forEach, sortBy } from 'lodash';
import { useCallback, useEffect, useMemo } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import useFieldOptions, { FieldItem, FieldOptionProps } from 'components/inputs/FieldOptions/useFieldOptions';
import Select from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import TreeSkeleton from 'components/tree-skeleton';
import { Text } from 'components/typography';
import usePreviousValue from 'hooks/usePreviousValue';
import { EMPTY_ARRAY } from 'store/constants';
import useSetState from 'utils/useSetState';

import { FieldMatchMap, QuickStartSchemaMatcherItem, SchemaMatchMap } from './QuickStartInstallSchemaMatcher.types';

const borderOptions = { labelBottom: true, contentBottom: true };

export interface SchemaMatcherFieldsProps {
  field: FieldOptionProps;
  fieldOptions: FieldOptionProps[];
  setValue: (val: string) => void;
  value?: string;
}

const SchemaMatcherFields = ({ field, fieldOptions, value, setValue }: SchemaMatcherFieldsProps) => {
  const { tn } = useI18nContext();

  const selectOptions = useFieldOptions(fieldOptions);
  const noAvailableOptions = selectOptions.options.length === 0;

  return (
    <HStack key={field.apiName}>
      <div className="synri-qs-pipeline-matcher-entity">
        <FieldItem enableTooltip {...field} />
      </div>
      <Select
        {...selectOptions}
        showSearch
        className="synri-qs-pipeline-matcher-select"
        placeholder={tn(noAvailableOptions ? 'no_fields_available' : 'select_field')}
        disabled={noAvailableOptions}
        value={value}
        onChange={setValue}
      />
    </HStack>
  );
};

export interface QuickStartInstallSchemaMatcherProps {
  items: QuickStartSchemaMatcherItem[];
  onChange: (value: SchemaMatchMap) => void;
  defaultValue?: SchemaMatchMap;
}

const QuickStartInstallSchemaMatcherTree = ({ items, onChange, defaultValue }: QuickStartInstallSchemaMatcherProps) => {
  const { tn } = useI18nContext();

  const [formData, setFormData] = useSetState(() => {
    if (defaultValue) {
      return defaultValue;
    }
    const record: SchemaMatchMap = {};

    // Build a flat object with all entity and field keys
    return items.reduce((prev, entity) => {
      const fields: FieldMatchMap = entity.fields.reduce((previous, field) => {
        previous[field.id] = null;
        return previous;
      }, {} as FieldMatchMap);

      prev[entity.entityId] = {
        matchValue: null,
        fields,
      };

      return prev;
    }, record);
  });

  const previousDefaultValue = usePreviousValue(defaultValue);

  // Sometimes the component renders with an undefined defaultValue on the first render
  useEffect(() => {
    if (!previousDefaultValue && defaultValue) {
      setFormData(defaultValue);
    }
  }, [defaultValue, previousDefaultValue, setFormData]);

  useEffect(() => {
    onChange(formData);
    // onChange is not a stable function
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formData]);

  const setMatch = useCallback(
    (entityId: string, matchId: string, fieldId?: string) => {
      setFormData((currentFormData) => {
        return produce(currentFormData, (draft) => {
          if (fieldId) {
            draft[entityId].fields[fieldId] = matchId;
          } else {
            draft[entityId] = draft[entityId] || {
              matchValue: null,
              fields: {},
            };
            if (draft[entityId]) {
              draft[entityId].matchValue = matchId;
              forEach(draft[entityId].fields, (_value, key) => {
                draft[entityId].fields[key] = null;
              });
            }
          }
        });
      });
    },
    [setFormData]
  );

  const reviewItems = useMemo(() => {
    return items.map((entity) => {
      const selectedEntity = find(entity.entityOptions, { value: formData[entity.entityId]?.matchValue || '' });
      const fieldOptions = selectedEntity?.fieldOptions;

      return {
        key: entity.entityId,
        label: (
          <HStack>
            <Text size="md" color="gray-850" className="synri-qs-pipeline-matcher-entity">
              {entity.entityName}
            </Text>
            <Select
              showSearch
              className="synri-qs-pipeline-matcher-select"
              optionData={entity.entityOptions}
              placeholder={tn('select_entity')}
              value={formData[entity.entityId]?.matchValue || undefined}
              onChange={(val: string) => {
                setMatch(entity.entityId, val);
              }}
            />
          </HStack>
        ),
        children: (
          <Stack className="synri-qs-pipeline-matcher-field-stack" spacing="xs">
            {sortBy(entity.fields, 'displayName').map((field) => {
              return (
                <SchemaMatcherFields
                  key={field.apiName}
                  field={field}
                  fieldOptions={fieldOptions || EMPTY_ARRAY}
                  value={formData[entity.entityId]?.fields[field.id] || undefined}
                  setValue={(val) => {
                    setMatch(entity.entityId, val, field.id);
                  }}
                />
              );
            })}
          </Stack>
        ),
      };
    });
  }, [formData, items, setMatch, tn]);

  return <TreeSkeleton items={reviewItems} borderOptions={borderOptions} expandIconsOffset={4} />;
};

export default withI18n(QuickStartInstallSchemaMatcherTree, 'QuickStart');
