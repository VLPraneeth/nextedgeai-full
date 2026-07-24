import { useEnhancedSelector } from 'hooks/redux';
import { selectEntityById } from 'store/entity/selectors';

// TODO: remove this, use metatdata to determine when to show
const SHOW_DATASCORE_FOR_ENTITIES = ['account', 'contact', 'lead'];
export const shouldShowDataScoreForEntityName = (entityApiName: string) =>
  SHOW_DATASCORE_FOR_ENTITIES.includes(entityApiName.toLowerCase());

export const useShouldShowDataFitnessForEntity = (entityId: string) => {
  const entity = useEnhancedSelector((state) => selectEntityById(state, entityId));
  return entity && shouldShowDataScoreForEntityName(entity.apiName);
};
