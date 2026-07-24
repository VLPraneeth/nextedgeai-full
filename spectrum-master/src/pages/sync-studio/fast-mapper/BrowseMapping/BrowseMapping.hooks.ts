import { uniq } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { getConnectorEntities, getEntityFields } from 'store/entity/actions';
import { selectEditMappingsStatus, selectGetMappingsStatus, selectMappings } from 'store/fast-mapper/selectors';
import { resetBrowseMappingModal, setEditMappingError } from 'store/fast-mapper/slice';
import { editMappings, getMappings } from 'store/fast-mapper/thunks';
import { Mapping } from 'store/fast-mapper/types';
import AppConstants from 'utils/AppConstants';

import { useFastMapper } from '../FastMapperModal';
import { getSerializedEditedValues, validate } from '../Mapper';
import { EditedMapping } from '../types';
import { serverMappingsToMappings } from './BrowseMapping.utils';

export const useBrowseMapping = () => {
  const { entityId, visible } = useFastMapper();

  const reduxDispatch = useEnhancedDispatch();
  const serverMappings = useEnhancedSelector(selectMappings);
  const mappingsStatus = useEnhancedSelector(selectGetMappingsStatus);
  const editMappingStatus = useEnhancedSelector(selectEditMappingsStatus);

  const [hasFetched, setHasFetched] = useState(false);

  const isRemapping = editMappingStatus === AppConstants.FETCH_STATUS.LOADING;

  useEffect(() => {
    entityId &&
      !serverMappings &&
      mappingsStatus !== AppConstants.FETCH_STATUS.LOADING &&
      visible &&
      reduxDispatch(getMappings({ entityId }));
  }, [entityId, reduxDispatch, serverMappings, mappingsStatus, visible]);

  useEffect(() => {
    if (serverMappings && !hasFetched) {
      let connectorIds: string[] = [];
      let entityIds: string[] = [];

      serverMappings.forEach((mapping) => {
        connectorIds.push(mapping.synapseId);
        entityIds.push(mapping.synapseEntityId);
      });

      connectorIds = uniq(connectorIds);
      entityIds = uniq(entityIds);

      connectorIds.forEach((id) => {
        reduxDispatch(getConnectorEntities(id, false));
      });

      entityIds.forEach((id) => {
        reduxDispatch(getEntityFields(id));
      });

      setHasFetched(true);
    }
  }, [reduxDispatch, hasFetched, serverMappings]);

  const mappings = useMemo(() => {
    if (serverMappings) {
      return serverMappingsToMappings(serverMappings);
    }
  }, [serverMappings]);

  const validateAndRemapFields = useCallback(
    async (mappings: Mapping[], editedMappings: EditedMapping[]) => {
      const error = validate(mappings);
      if (!error.length && entityId) {
        reduxDispatch(resetBrowseMappingModal());
        const result = await reduxDispatch(
          editMappings({
            entityId,
            editedMappings: getSerializedEditedValues(editedMappings) || [],
          })
        );
        return result;
      } else {
        reduxDispatch(setEditMappingError({ error }));
      }
    },
    [entityId, reduxDispatch]
  );

  const refreshServerMappings = useCallback(() => {
    reduxDispatch(getMappings({ entityId }));
  }, [entityId, reduxDispatch]);

  return {
    isRemapping,
    mappings,
    mappingsStatus,
    serverMappings,
    validateAndRemapFields,
    refreshServerMappings,
  };
};
