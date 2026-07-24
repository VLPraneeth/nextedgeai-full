import Radio from 'antd/lib/radio';
import { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import { useDispatch } from 'react-redux';

import { useI18nContext } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useQueryParams from 'hooks/useQueryParams';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { DeleteType } from 'pages/data-studio/Batch/types';
import { ColumnItem, mergeConfiguredAndDefaultColumns } from 'pages/schema-studio/ConfigureTableColumnsModal';
import { deleteRecordData } from 'store/data-studio';
import {
  selectFiltersStatusForEntityId,
  selectDeleteRecordDataStatus,
  selectDeleteRecordDataErrors,
} from 'store/data-studio/selectors';
import { EntityFilterWithHash } from 'store/data-studio/types';
import { decodeFactorId, useDataScoreForEntity } from 'store/datascore';
import { selectDataStudioColumnsForEntity } from 'store/user/selectors';
import { updateDataStudioColumnsForEntityId } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';

import { makeFakeEntityFilter } from './utils';

export type UseUserConfiguredColumnsForEntityResult = [ColumnItem[], (columns: ColumnItem[]) => void];

export const useUserConfiguredColumnsForEntity = (
  entityId: SyncariID,
  defaultColumns: ColumnItem[] = []
): UseUserConfiguredColumnsForEntityResult => {
  const dispatch = useDispatch();
  const configuredColumns = useEnhancedSelector((state) => selectDataStudioColumnsForEntity(state, entityId));

  const columns = useMemo(() => {
    return Array.isArray(configuredColumns) && configuredColumns.length > 0
      ? mergeConfiguredAndDefaultColumns(configuredColumns, defaultColumns)
      : defaultColumns;
  }, [configuredColumns, defaultColumns]);

  const updateConfiguredColumnsForEntity = useCallback(
    (newColumns: ColumnItem[]) => {
      dispatch(updateDataStudioColumnsForEntityId(entityId, newColumns));
    },
    [entityId, dispatch]
  );

  return [columns, updateConfiguredColumnsForEntity];
};

export const useFakeFilterFromDataScoreFactor = (entityId: string, factorId?: string) => {
  const { data, status } = useDataScoreForEntity(entityId);

  return useMemo(() => {
    if (!factorId) {
      return;
    }

    const [, fieldName, ruleId] = decodeFactorId(factorId);
    const contributingFactor = data?.factors?.find(
      (factor) => factor.fieldName === fieldName && factor.ruleId === ruleId
    );

    return {
      data: makeFakeEntityFilter(contributingFactor?.filterCondition, { name: contributingFactor?.label }),
      status,
    };
  }, [factorId, data, status]);
};

export interface QueryParamValues {
  filterId?: string;
  // factorId is a composed key made of entityId:fieldName:ruleId, base64 encoded
  factorId?: string;
}

export interface UseFilterOrFactorFilterParams {
  entityId: string;
  filterId?: string;
  factorId?: string;
}

export interface UseFilterOrFactorFilterResult {
  data?: Partial<EntityFilterWithHash>;
  loading: boolean;
}

export const useFilterOrFactorFilter = ({
  entityId,
  filterId,
  factorId,
}: UseFilterOrFactorFilterParams): UseFilterOrFactorFilterResult | undefined => {
  const factorResponse = useFakeFilterFromDataScoreFactor(entityId, factorId);
  const filterResponse = useEnhancedSelector((state) =>
    filterId
      ? {
          data: state.dataStudio.filtersData[filterId],
          status: selectFiltersStatusForEntityId(state, entityId),
        }
      : undefined
  );

  const filter = filterResponse || factorResponse;

  if (!filter || filter.data?.syncariEntityId !== entityId) {
    // If no filter, or if returned filter is not for currently selected
    // entity, return undefined
    return undefined;
  }

  const { data, status } = filter;

  return {
    data,
    // if we already have our filter value, we don't need to watch for other filters being
    // loaded for this entity later
    loading: data ? false : status === AppConstants.FETCH_STATUS.LOADING,
  };
};

export const useFilterFromQueryString = (entityId: string) => {
  const [{ filterId: paramFilterId, factorId }, setParams] = useQueryParams<QueryParamValues>();

  // If no filter is in query string, check localStorage for an active filter
  // to support persisting filter after viewing a single record
  const filterId = paramFilterId || localStorage.getItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID) || undefined;

  const filter = useFilterOrFactorFilter({
    entityId,
    filterId,
    factorId,
  });

  if (filterId && filter?.data?.syncariEntityId === entityId) {
    if (!paramFilterId) {
      // If filter exists for current entity
      // but did not come from params, add it to the params
      setParams({ filterId });
    }

    if (paramFilterId) {
      // If filter exists for current entity
      // and came from params, add it to localStorage
      localStorage.setItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID, paramFilterId);
    }
  }
  if (!filter?.data && filterId) {
    // if filter doesn't exist but ID is in query params
    // remove it from URL and localStorage
    localStorage.removeItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID);
    setParams('');
  }

  return filter;
};

export type DeleteRecordDataModalOptions = {
  showToasts?: boolean;
  onSuccess?: () => void;
  onError?: (message: string) => void;
};

export const useDeleteRecordDataModal = (
  entityId: string,
  recordId: string,
  options: DeleteRecordDataModalOptions = { showToasts: false }
) => {
  const showDeleteModal = useUserInputConfirmationModal();
  const modalRef = useRef<ReturnType<typeof showDeleteModal> | null>(null);
  const [deleteType, setDeleteType] = useState(DeleteType.LOCAL);

  const { t, tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const status = useEnhancedSelector((state) => selectDeleteRecordDataStatus(state, recordId));
  const error = useEnhancedSelector((state) => selectDeleteRecordDataErrors(state, recordId));

  const handleDeleteRecordData = useCallback(() => {
    dispatch(deleteRecordData(entityId, recordId, deleteType === DeleteType.GLOBAL))
      .then(() => {
        options.onSuccess?.();
      })
      .catch((err) => {
        options.onError?.(err.message);
      });
  }, [dispatch, entityId, recordId, deleteType, options]);

  const errorMessage = !error
    ? ''
    : 'errorMessage' in error
    ? error.errorMessage
    : 'message' in error
    ? error.message
    : error.toString();

  // if we're not showing toasts, just pass a constant
  useToastForFetchStatusChange(options.showToasts ? status : AppConstants.FETCH_STATUS.IDLE, {
    success:
      deleteType === DeleteType.GLOBAL
        ? tn('delete_record_in_end_systems_toast_success')
        : tn('delete_record_toast_success'),
    error: errorMessage,
  });

  const deleteModalContent = useMemo(
    () => (
      <Stack>
        <TranslatedText namespace="DataStudio" text="delete_entity_record_message" args={{ recordId }} />
        <Radio.Group
          className="delete-type-radio-group"
          onChange={(evt) => setDeleteType(evt.target.value)}
          value={deleteType}>
          <Stack spacing="xs">
            <Radio value={DeleteType.LOCAL}>{t('DataStudio.BatchOperation.local_delete')}</Radio>
            <Radio value={DeleteType.GLOBAL}>{t('DataStudio.BatchOperation.global_delete')}</Radio>
          </Stack>
        </Radio.Group>
      </Stack>
    ),
    [recordId, deleteType, t]
  );

  useEffect(() => {
    if (modalRef.current) {
      modalRef.current.update({
        content: deleteModalContent,
        onOk: handleDeleteRecordData,
      });
    }
  }, [deleteModalContent, handleDeleteRecordData]);

  return () => {
    modalRef.current = showDeleteModal({
      title: tn('delete_entity_record_title'),
      content: deleteModalContent,
      onOk: handleDeleteRecordData,
    });
  };
};
