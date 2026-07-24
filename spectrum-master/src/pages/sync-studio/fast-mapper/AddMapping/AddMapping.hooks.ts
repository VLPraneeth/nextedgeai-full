import { useCallback } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { getEntities } from 'store/entity/thunks';
import { setAddMappingError } from 'store/fast-mapper/slice';
import { saveMappings } from 'store/fast-mapper/thunks';
import { Mapping } from 'store/fast-mapper/types';
import AppConstants from 'utils/AppConstants';

import { useFastMapper } from '../FastMapperModal';
import { validate, getSerializedValues } from '../Mapper';

export const useAddMapping = () => {
  const reduxDispatch = useEnhancedDispatch();
  const { entityId, saveMappingsStatus } = useFastMapper();

  const isSaving = saveMappingsStatus === AppConstants.FETCH_STATUS.LOADING;

  const validateAndSave = useCallback(
    (mappings: Mapping[]) => {
      const error = validate(mappings);
      if (!error.length && entityId) {
        reduxDispatch(
          saveMappings({
            entityId,
            mappings: getSerializedValues(mappings) || [],
          })
        ).then(() => reduxDispatch(getEntities()));
      } else {
        reduxDispatch(setAddMappingError({ error }));
      }
    },
    [entityId, reduxDispatch]
  );

  return {
    isSaving,
    validateAndSave,
  };
};
