import { useI18nContext } from 'components/I18nProvider';
import { HStack } from 'components/layout';
import StatBlock from 'components/StatBlock';

export interface SelectedStatBlocksProps {
  entityCount: number;
  fieldCount: number;
}

export const SelectedStatBlocks = ({ entityCount, fieldCount }: SelectedStatBlocksProps) => {
  const { tn } = useI18nContext();

  return (
    <HStack>
      <StatBlock value={entityCount} label={tn('entities_selected', { count: entityCount })} />
      <StatBlock value={fieldCount} label={tn('fields_selected', { count: fieldCount })} />
    </HStack>
  );
};
