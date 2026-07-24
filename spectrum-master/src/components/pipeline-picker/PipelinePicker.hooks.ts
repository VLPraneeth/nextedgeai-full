import produce from 'immer';
import { find, includes, pick } from 'lodash';
import { useEffect, useMemo, useState } from 'react';
import { useDispatch } from 'react-redux';

import { useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { selectPublishedEntities } from 'store/entity/selectors';
import { getSchemaForEntityPipelinePicker } from 'store/schema/thunks';
import { FieldWithStatusModel, SchemaContainer } from 'store/schema/types';
import { makeSchemaKey } from 'store/schema/utils';
import AppConstants from 'utils/AppConstants';

import { DATA_TYPE_ALL_FILTER_VALUE } from './PipelinePicker';
import { PipelinePickerEntity, PipelinePickerEntityField } from './PipelinePicker.types';

export const filterFieldsByTextAndDataType = (
  fields: PipelinePickerEntityField[],
  filterText: string,
  filterDataType: string
) => {
  return fields.filter((field) => {
    if (filterDataType !== DATA_TYPE_ALL_FILTER_VALUE) {
      if (filterDataType !== field.dataType) {
        return false;
      }
    }
    if (filterText) {
      const matchesDisplayName = field.displayName.toLowerCase().indexOf(filterText.toLowerCase()) !== -1;
      const matchesApiName = field.apiName.toLowerCase().indexOf(filterText.toLowerCase()) !== -1;
      if (!matchesDisplayName && !matchesApiName) {
        return false;
      }
    }

    return true;
  });
};

/**
 * Fetches the schema for all published entities in state.entity.entities. This
 * returns the entities and adds the fields to the entities as they are returned
 * from the backend.
 */
export const usePipelinePickerEntities = (): PipelinePickerEntity[] => {
  const dispatch = useDispatch();

  const publishedEntities = useEnhancedSelector(selectPublishedEntities);

  const formattedEntities: PipelinePickerEntity[] = useMemo(() => {
    return publishedEntities
      ? publishedEntities.map((entity) => ({
          id: entity.id,
          apiName: entity.apiName,
          displayName: entity.displayName,
          loading: true,
          // Fields will be included once they're fetched from the schema and we
          // can filter out the unmapped fields
          fields: EMPTY_ARRAY,
        }))
      : EMPTY_ARRAY;
  }, [publishedEntities]);

  const [entities, setEntities] = useState(formattedEntities);

  const [fetchedEntities, setFetchedEntities] = useState<string[]>([]);

  const [entitySchemas, setEntitySchemas] = useState<
    Record<string, SchemaContainer<FieldWithStatusModel[]> | { unavailableMessage: string }>
  >({});

  useEffect(() => {
    formattedEntities.forEach((entity) => {
      const schemaKey = makeSchemaKey({ entityId: entity.id, graphVersion: AppConstants.GRAPH_STATUS.APPROVED });
      const schema = entitySchemas?.[schemaKey];

      if (schema) {
        // Add fields to the existing entities and set loading = false
        setEntities((prev) =>
          produce(prev, (draft) => {
            const stateEntity = find(draft, { id: entity.id });
            if (stateEntity) {
              if ('unavailableMessage' in schema) {
                stateEntity.unavailableMessage = schema.unavailableMessage;
                stateEntity.loading = false; // Set loading to false even on error
              } else {
                stateEntity.fields = schema.fields
                  // Only mapped fields in the published pipeline are available to
                  // select in a quick start
                  .filter((field) => field.isMapped)
                  .map((field) => pick(field, ['id', 'apiName', 'displayName', 'dataType']));
                stateEntity.loading = false;
              }
            }
          })
        );
      } else if (!includes(fetchedEntities, entity.id)) {
        // Fetch the schema for each published entity
        getSchemaForEntityPipelinePicker({
          entityId: entity.id,
          graphVersion: AppConstants.GRAPH_STATUS.APPROVED,
        })
          .then((response) => {
            setEntitySchemas((current) => {
              if (response?.data?.errorMessage) {
                return {
                  ...current,
                  [schemaKey]: {
                    unavailableMessage: response?.data?.errorMessage,
                  },
                };
              } else if (response?.data) {
                return { ...current, [schemaKey]: response?.data };
              }
              return current;
            });
          })
          .catch((error) => {
            setEntitySchemas((current) => ({
              ...current,
              [schemaKey]: {
                unavailableMessage: `Failed to load fields: ${error?.message || 'Unknown error'}`,
              },
            }));
          });

        setFetchedEntities((prev) => [...prev, entity.id]);
      }
    });
  }, [dispatch, entitySchemas, fetchedEntities, formattedEntities]);

  return entities;
};
