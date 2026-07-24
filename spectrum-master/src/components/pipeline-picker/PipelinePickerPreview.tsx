import { useMemo } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import { TextTag } from 'components/text-tag';
import { EMPTY_ARRAY } from 'store/constants';

import TreeSkeleton from '../tree-skeleton';
import { SelectedStatBlocks } from './PipelinePicker.components';
import { PipelinePickerEntity } from './PipelinePicker.types';
import PipelinePickerFieldsPreview from './PipelinePickerFieldsPreview';

export const DATA_TYPE_ALL_FILTER_VALUE = 'all';

export interface PipelinePickerPreviewProps {
  entities: PipelinePickerEntity[];
}

const PipelinePickerPreview = ({ entities = EMPTY_ARRAY }: PipelinePickerPreviewProps) => {
  const { tn } = useI18nContext();

  const fieldCount = useMemo(() => entities.flatMap(({ fields }) => fields).length, [entities]);

  const treeItems = useMemo(() => {
    return entities.map((entity, index) => {
      const hidden = entity.fields.length === 0;
      const fieldsCount = entities[index].fields.length;

      return {
        key: entity.id,
        hidden,
        label: (
          <HStack spacing="xs" className="synri-tree-skeleton-label-set-height">
            <span className="synri-checkbox-label">{entity.displayName}</span>
            <TextTag text={tn('selected_count', { count: fieldsCount })} color="gray" />
          </HStack>
        ),
        children: <PipelinePickerFieldsPreview entity={entity} />,
      };
    });
  }, [entities, tn]);

  return (
    <Stack>
      <SelectedStatBlocks entityCount={entities.length} fieldCount={fieldCount} />
      <TreeSkeleton items={treeItems} />
    </Stack>
  );
};

export default withI18n(PipelinePickerPreview, 'PipelinePicker');
