import { Spin } from 'antd';
import { sumBy } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { getEntityMapping } from 'store/entity/actions';

import { QuickStartSchemaMatcherItem, SchemaMatchMap } from './QuickStartInstallSchemaMatcher.types';
import QuickStartInstallSchemaMatcherTree from './QuickStartInstallSchemaMatcherTree';

export interface QuickStartInstallSchemaMatcherProps extends SkullRenderTypeBaseProps {
  synapseName: string;
  items: QuickStartSchemaMatcherItem[];
  connectorId: string;
  defaultValue?: SchemaMatchMap;
}

const QuickStartInstallSchemaMatcher = ({
  synapseName,
  items,
  defaultValue,
  onChange,
  refreshStep,
  connectorId,
}: QuickStartInstallSchemaMatcherProps) => {
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const [loading, setLoading] = useState(false);

  const [initialDefaultValue, setInitialDefaultValue] = useState(defaultValue);

  // Sometimes the component renders with an undefined defaultValue on the first render
  useEffect(() => {
    if (!initialDefaultValue && defaultValue) {
      setInitialDefaultValue(defaultValue);
    }
  }, [defaultValue, initialDefaultValue, setInitialDefaultValue]);

  const { allFieldsHaveDefaultValue, entityCount, fieldsCount } = useMemo(() => {
    const allFieldsHaveDefaultValue =
      initialDefaultValue &&
      items.every((entity) => {
        if (initialDefaultValue[entity.entityId]?.matchValue) {
          return entity.fields.every((field) => initialDefaultValue[entity.entityId]?.fields[field.id]);
        }
        return false;
      });

    if (allFieldsHaveDefaultValue) {
      return {
        allFieldsHaveDefaultValue: true,
        entityCount: items.length,
        fieldsCount: items.flatMap((entity) => entity.fields).length,
      };
    }

    const entityCount = sumBy(items, (entity) => (initialDefaultValue?.[entity.entityId]?.matchValue ? 0 : 1));
    const fieldsCount = sumBy(items, (entity) =>
      sumBy(entity.fields, (field) => (initialDefaultValue?.[entity.entityId]?.fields[field.id] ? 0 : 1))
    );

    return {
      allFieldsHaveDefaultValue: false,
      entityCount,
      fieldsCount,
    };
  }, [initialDefaultValue, items]);

  const refreshSchema = async () => {
    setLoading(true);
    await dispatch(getEntityMapping(connectorId));
    setLoading(false);
    refreshStep();
  };

  return (
    <Spin spinning={loading} tip={tn('refreshing_schema')}>
      <Stack>
        <Stack spacing="md">
          <TranslatedText
            color="gray-900"
            text={allFieldsHaveDefaultValue ? 'review_schema_matcher' : 'match_pipelines_title'}
            size="xl"
            weight="semibold"
            args={{ name: synapseName }}
          />
          <TranslatedText
            size="lg"
            color="gray-850"
            text={allFieldsHaveDefaultValue ? 'match_pipelines_description_complete' : 'match_pipelines_description'}
            args={{ name: synapseName }}
          />
          <HStack className="synri-install-pipeline-gray-box" justify="space-between">
            <TranslatedText
              size="lg"
              color="gray-800"
              text={allFieldsHaveDefaultValue ? 'count_mapped_pipelines' : 'count_required_pipelines'}
              args={{
                entities: tn('count_required_entities', { count: entityCount }),
                fields: tn('count_required_fields', { count: fieldsCount }),
              }}
            />
            <Button type="primary" onClick={refreshSchema}>
              <TranslatedText color="white" text="refresh" />
            </Button>
          </HStack>
        </Stack>
        <QuickStartInstallSchemaMatcherTree items={items} onChange={onChange} defaultValue={defaultValue} />
      </Stack>
    </Spin>
  );
};

export default withI18n(QuickStartInstallSchemaMatcher, 'QuickStart');
