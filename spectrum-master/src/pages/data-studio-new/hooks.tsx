import Checkbox from 'antd/lib/checkbox';
import Input from 'antd/lib/input';
import { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import { useDispatch } from 'react-redux';
import Modal, { ModalFuncProps } from 'antd/lib/modal';

import { useI18nContext } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import { Text, TranslatedText } from 'components/typography';
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
import { tNamespaced } from 'utils/i18nUtil';

export type UseUserConfiguredColumnsForEntityResult = [ColumnItem[], (columns: ColumnItem[]) => void];

type AntModal = ReturnType<typeof Modal.confirm>;

type EnhancedModal = AntModal & {
  _originalUpdate?: AntModal['update'];
};

export interface EnhancedModalFuncProps extends ModalFuncProps {
  title?: React.ReactNode;
  additionalContent?: React.ReactNode;
}

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

  // If no filter is in query string, check sessionStorage for an active filter
  // to support persisting filter after viewing a single record
  // Using sessionStorage instead of localStorage ensures each tab has independent filter state
  const filterId = paramFilterId || sessionStorage.getItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID) || undefined;

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
      // and came from params, add it to sessionStorage (tab-specific)
      sessionStorage.setItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID, paramFilterId);
    }
  }
  if (!filter?.data && filterId) {
    // if filter doesn't exist but ID is in query params
    // remove it from URL and sessionStorage
    sessionStorage.removeItem(AppConstants.ACTIVE_DATA_STUDIO_FILTER_ID);
    setParams('');
  }

  return filter;
};

export type DeleteRecordDataModalOptions = {
  showToasts?: boolean;
  onSuccess?: () => void;
  onError?: (message: string) => void;
};

export const useDeleteRecordInputConfig = (
  confirmationText = 'DELETE',
  confirmationInputPlaceholder?: string,
  additionalContent?: React.ReactNode
) => {
  const tn = tNamespaced('DataStudio');
  const modalRef = useRef<EnhancedModal | null>(null);
  const placeholder = confirmationInputPlaceholder || tn('confirmation_text_placeholder', { confirmationText });
  const [userInput, setUserInput] = useState('');
  const [modalKey, setModalKey] = useState(0);

  useEffect(() => {
    if (userInput === confirmationText) {
      modalRef.current?.update({ okButtonProps: { disabled: false, type: 'danger' } });
    } else {
      modalRef.current?.update({ okButtonProps: { disabled: true, type: 'danger' } });
    }
  }, [userInput, confirmationText]);

  const createModalTitle = (
    title: React.ReactNode,
    options?: {
      icon?: React.ReactNode;
      color?: 'default' | 'danger' | 'warning' | 'info';
    }
  ) => {
    if (!title) return undefined;

    const colorMap = {
      default: 'gray-1000',
      danger: 'red-500',
      warning: 'orange-700',
      info: 'blue-600',
    } as const;

    const textColor = colorMap[options?.color || 'default'];

    return (
      <HStack spacing="xs" align="center">
        {options?.icon && <span className="modal-title-icon">{options.icon}</span>}
        <Text size="lg" weight="bold" color={textColor as any}>
          {title}
        </Text>
      </HStack>
    );
  };

  const getModalContent = useCallback(
    (content: React.ReactNode, extraContent?: React.ReactNode) => (
      <Stack spacing="md">
        <div>{content}</div>
        <Stack spacing="sm">
          <TranslatedText
            className="delete-confirmation-text"
            beDangerous
            namespace="DataStudio.BatchOperation"
            text="delete_confirmation_warning"
          />
          <Input
            key={modalKey}
            id="delete-confirmation"
            required
            placeholder={placeholder}
            onChange={(evt) => setUserInput(evt.target.value)}
          />
        </Stack>
        {extraContent && <div>{extraContent}</div>}
      </Stack>
    ),
    [placeholder, modalKey]
  );

  const updateModal = useCallback(
    ({ content, ...newProps }: ModalFuncProps & { additionalContent?: React.ReactNode }) => {
      if (modalRef.current) {
        const updateFn = modalRef.current._originalUpdate || modalRef.current.update;

        if (content) {
          updateFn({
            content: getModalContent(content, newProps.additionalContent || additionalContent),
            ...newProps,
          });
        } else {
          updateFn(newProps);
        }
      }
    },
    [getModalContent, additionalContent]
  );

  const showModal = useCallback(
    ({
      content: givenContent,
      title: givenTitle,
      okButtonProps: givenOkButtonProps,
      ...modalProps
    }: EnhancedModalFuncProps) => {
      // Reset input state when opening a new modal
      setUserInput('');
      setModalKey((prev) => prev + 1);

      const content = getModalContent(givenContent, modalProps.additionalContent || additionalContent);
      const title = givenTitle ? createModalTitle(givenTitle) : '';

      const modal: EnhancedModal = Modal.confirm({
        content,
        title,
        className: 'data-studio-enhanced-modal',
        okButtonProps: { disabled: true, ...givenOkButtonProps },
        width: 640,
        ...modalProps,
      });

      // monkey-patching the modal instance so that we can call `update` on the returned ref
      // in order to update the contents of the modal from a consumer. Helpful if you need to
      // add other controls or dynamic items to the modal content
      modal._originalUpdate = modal.update;
      modal.update = updateModal;

      modalRef.current = modal;
      return modal;
    },
    [getModalContent, updateModal, additionalContent]
  );

  return showModal;
};

export const useDeleteRecordDataModal = (
  entityId: string,
  recordId: string,
  options: DeleteRecordDataModalOptions = { showToasts: false }
) => {
  const { t, tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const [deleteType, setDeleteType] = useState(DeleteType.LOCAL);

  const deleteConfirmationPlaceholder = t('DataStudio.BatchOperation.delete_confirmation_placeholder');

  const checkboxContent = useMemo(
    () => (
      <Checkbox
        checked={deleteType === DeleteType.GLOBAL}
        onChange={(evt) => setDeleteType(evt.target.checked ? DeleteType.GLOBAL : DeleteType.LOCAL)}>
        {t('DataStudio.BatchOperation.global_delete', { count: 1 })}
      </Checkbox>
    ),
    [deleteType, t]
  );

  const showDeleteModal = useDeleteRecordInputConfig(
    deleteConfirmationPlaceholder,
    deleteConfirmationPlaceholder,
    checkboxContent
  );

  const modalRef = useRef<ReturnType<typeof showDeleteModal> | null>(null);

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
    () => <TranslatedText namespace="DataStudio" text="delete_entity_record_message" args={{ recordId }} />,
    [recordId]
  );

  useEffect(() => {
    if (modalRef.current) {
      modalRef.current.update({
        content: deleteModalContent,
        additionalContent: checkboxContent,
        onOk: handleDeleteRecordData,
      } as EnhancedModalFuncProps);
    }
  }, [deleteModalContent, checkboxContent, handleDeleteRecordData]);

  return () => {
    setDeleteType(DeleteType.LOCAL);
    modalRef.current = showDeleteModal({
      title: tn('delete_entity_record_modal_title'),
      content: deleteModalContent,
      additionalContent: checkboxContent,
      onOk: handleDeleteRecordData,
      okText: tn('delete_entity_record_title'),
      okButtonProps: {
        type: 'danger',
        disabled: true,
      },
    });
  };
};
