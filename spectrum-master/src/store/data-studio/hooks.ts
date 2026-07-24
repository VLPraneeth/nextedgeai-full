import produce from 'immer';
import { useCallback, useEffect, useMemo } from 'react';

import { DEFAULT_PAGE_SIZE } from 'hooks/pagination';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectEntity } from 'selectors/entitySelectors';
import { Entity } from 'store/entity/types';
import { FetchStatus, PaginatedApi, ResponseError } from 'store/types';
import AppConstants from 'utils/AppConstants';

import {
  SelectListDataForEntityIdResult,
  selectFieldsMetadataForEntity,
  selectFiltersDataForEntityId,
  selectFiltersStatusForEntityId,
  selectListDataForEntityId,
  selectListErrorForEntityId,
  selectListStatusForEntityId,
  selectRecordForEntity,
  selectRecordStatus,
  selectSelectedColumnsForEntity,
} from './selectors';
import { getEntityFilters, getEntityRecords, getRecordDetail } from './thunks';
import { DataStudioFiltersResponse, EnhancedFieldMetadata, EntityRecord } from './types';

export interface UseEntityRecordsListParams extends PaginatedApi {
  entityId?: string;
  filter?: any;
  orderBy?: string;
  sortDirection?: string;
  page?: number;
}

export interface UseEntityRecordsListResult {
  data: SelectListDataForEntityIdResult;
  error: ResponseError | undefined;
  loading: boolean;
  metadata: Record<string, EnhancedFieldMetadata>;
  refetch: () => void;
  selectedColumns: string[];
  status: FetchStatus;
}

export const useEntityRecordsList = ({
  entityId,
  count = DEFAULT_PAGE_SIZE,
  cursor,
  direction = 'next',
  filter,
  orderBy,
  sortDirection,
  page,
}: UseEntityRecordsListParams): UseEntityRecordsListResult => {
  const dispatch = useEnhancedDispatch();
  const data = useEnhancedSelector((state) => selectListDataForEntityId(state, entityId));
  const error = useEnhancedSelector((state) => selectListErrorForEntityId(state, entityId));
  const selectedColumns = useEnhancedSelector((state) => selectSelectedColumnsForEntity(state, entityId));
  const metadata = useEnhancedSelector((state) => selectFieldsMetadataForEntity(state, entityId));
  const status = useEnhancedSelector((state) => selectListStatusForEntityId(state, entityId));

  const fetchData = useCallback(() => {
    if (!entityId) {
      return;
    }

    dispatch(
      getEntityRecords({
        entityId,
        cursor,
        count,
        direction,
        predicate: filter,
        orderBy,
        sortDirection,
        page: page !== undefined ? page.toString() : undefined,
      })
    );
  }, [dispatch, entityId, cursor, count, direction, filter, orderBy, sortDirection, page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return useMemo(
    () => ({
      data,
      error: error as ResponseError | undefined,
      metadata,
      selectedColumns,
      status,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      refetch: fetchData,
    }),
    [data, error, selectedColumns, metadata, status, fetchData]
  );
};

export interface UseEntityFiltersListParams extends PaginatedApi {
  entityId?: string | null;
  bookmarked?: boolean;
}

export interface UseEntityFiltersListResult {
  data: DataStudioFiltersResponse | undefined;
  error: boolean;
  loading: boolean;
  status: FetchStatus;
  refetch: () => void;
}

export const useEntityFiltersList = ({
  entityId,
  bookmarked,
  count = DEFAULT_PAGE_SIZE,
  cursor,
  direction = 'next',
}: UseEntityFiltersListParams): UseEntityFiltersListResult => {
  const dispatch = useEnhancedDispatch();
  const status = useEnhancedSelector((state) => selectFiltersStatusForEntityId(state, entityId, bookmarked));
  const data = useEnhancedSelector((state) => selectFiltersDataForEntityId(state, entityId, bookmarked));

  const error = status === AppConstants.FETCH_STATUS.ERROR;
  const loading = status === AppConstants.FETCH_STATUS.LOADING;

  const fetchData = useCallback(() => {
    if (loading) {
      return;
    }

    dispatch(
      getEntityFilters({
        entityId,
        bookmarked,
        count,
        cursor,
        direction,
      })
    );
  }, [dispatch, loading, entityId, bookmarked, count, cursor, direction]);

  // We'll need to update this in the future if we need to paginate filters
  useEffect(() => {
    if (status !== AppConstants.FETCH_STATUS.SUCCESS) {
      fetchData();
    }
  }, [fetchData, status]);

  return useMemo(
    () => ({
      data,
      error,
      loading,
      status,
      refetch: fetchData,
    }),
    [status, fetchData, data, error, loading]
  );
};

export interface UseEntityRecordDetailParams extends PaginatedApi {
  entityId?: string;
  recordId?: string;
}

export interface UseEntityRecordDetailResult {
  data: EntityRecord | undefined;
  entity: Entity;
  idle: boolean;
  loading: boolean;
  metadata: Record<string, EnhancedFieldMetadata>;
  refetch: () => void;
  status: FetchStatus;
}

export const useEntityRecord = ({ entityId, recordId }: UseEntityRecordDetailParams): UseEntityRecordDetailResult => {
  const dispatch = useEnhancedDispatch();
  const data = useEnhancedSelector((state) => selectRecordForEntity(state, entityId, recordId));
  const entity = useEnhancedSelector((state) => selectEntity(state, { entityId }) as Entity);
  const selectedColumns = useEnhancedSelector((state) => selectSelectedColumnsForEntity(state, entityId));
  const metadata = useEnhancedSelector((state) => selectFieldsMetadataForEntity(state, entityId));
  const status = useEnhancedSelector((state) => selectRecordStatus(state, entityId, recordId));

  // Add the apiName to fields based on the entity fields
  const enhancedMetadata = useMemo(() => {
    if (entity && metadata) {
      return produce<Record<string, EnhancedFieldMetadata>>(metadata, (draft) => {
        Object.keys(metadata).forEach((fieldKey) => {
          const matchingField = entity.fields.find((entityField) => entityField.id === metadata[fieldKey].fieldId);
          draft[fieldKey].apiName = matchingField?.apiName || fieldKey;
        });
      });
    }
    return metadata;
  }, [entity, metadata]);

  const fetchData = useCallback(() => {
    if (!entityId || !recordId) {
      return;
    }

    // Skip fetching when recordId is 'create' (for creating new records)
    // Metadata will be available from the Redux state (loaded from entity records list)
    if (recordId === 'create') {
      return;
    }

    dispatch(
      getRecordDetail({
        entityId,
        recordId,
      })
    );
  }, [dispatch, entityId, recordId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return useMemo(
    () => ({
      data,
      entity,
      idle: status === AppConstants.FETCH_STATUS.IDLE,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      metadata: enhancedMetadata,
      refetch: fetchData,
      selectedColumns,
      status,
    }),
    [data, entity, fetchData, enhancedMetadata, selectedColumns, status]
  );
};
