import { useMemo } from 'react';

import { useSyncariEntities } from 'hooks/useSyncariEntities';
import { Entity } from 'store/entity/types';

interface UseSyncariEntitiesResult {
  data: Entity | undefined;
  loading: boolean;
}

export const useEntity = (entityId?: string): UseSyncariEntitiesResult => {
  const { loading, data } = useSyncariEntities();

  return useMemo(() => {
    const entity = !entityId ? undefined : data.find((e) => e.id === entityId);

    return {
      data: entity,
      loading,
    };
  }, [data, entityId, loading]);
};

export const useEntityByApiName = (apiName: string) => {
  const { loading, data } = useSyncariEntities();

  return useMemo(() => {
    const entity = !apiName ? undefined : data.find((e) => e.apiName === apiName);

    return {
      data: entity,
      loading,
    };
  }, [data, apiName, loading]);
};
