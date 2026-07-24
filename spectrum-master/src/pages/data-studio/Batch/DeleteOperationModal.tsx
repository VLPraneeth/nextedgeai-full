import Input from 'antd/lib/input';
import message from 'antd/lib/message';
import Radio from 'antd/lib/radio';
import { useMemo, useState } from 'react';
import { useDispatch } from 'react-redux';

import { useI18nContext } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import { Divider, Stack } from 'components/layout';
import { TranslatedText, Text } from 'components/typography';
import { useDeleteRecordsMutation, util as dataStudioBatchApiUtil } from 'store/data-studio-batch';
import { BatchOperation } from 'store/data-studio-batch/types';
import { packageData } from 'utils/ErrorUtils';

import EntityFilterPreview from './EntityFilterPreview';
import { EnhancedModal, useOperationModalContext } from './OperationModalProvider';
import { BatchOperationMode, DeleteType, CommonOperationModalProps } from './types';

import './OperationModal.less';

type DeleteOperationModalProps = CommonOperationModalProps & {
  mode: BatchOperationMode.DELETE | BatchOperationMode.PURGE;
};

const DeleteModal = ({ commonI18nArgs: i18nArgs, mode, entity, fieldValues, filter }: DeleteOperationModalProps) => {
  const { closeModal } = useOperationModalContext();
  const { tc, tn } = useI18nContext();
  const [confirmationText, setConfirmationText] = useState('');
  const [deleteType, setDeleteType] = useState(DeleteType.LOCAL);
  const [formError, setFormError] = useState<null | string>(null);

  const [deleteRecords, { isLoading: isDeleting }] = useDeleteRecordsMutation();
  const dispatch = useDispatch();

  const { displayName: entityName } = entity;
  const deleteConfirmationPlaceholder = tn('delete_confirmation_placeholder');
  const deletionConfirmed = confirmationText === deleteConfirmationPlaceholder;

  const deleteWarningText = useMemo(() => {
    if (mode === BatchOperationMode.PURGE) {
      return tn('purge_records_warning_text', i18nArgs);
    }
    if (mode === BatchOperationMode.DELETE) {
      if (deleteType === DeleteType.LOCAL) {
        return tn('delete_records_warning_text', i18nArgs);
      }
      return tn('delete_records_global_warning_text', i18nArgs);
    }

    return '';
  }, [deleteType, i18nArgs, mode, tn]);

  const okButtonTitle = useMemo(() => {
    if (mode === BatchOperationMode.PURGE) {
      return tn('purge_records_btn');
    }
    if (mode === BatchOperationMode.DELETE) {
      return tn('delete_records_btn', i18nArgs);
    }

    return tc('ok');
  }, [mode, tc, tn, i18nArgs]);

  const actionIsDisabled = isDeleting || !deletionConfirmed;

  const handleRequestDelete = async () => {
    setFormError(null);

    try {
      if (!deletionConfirmed || (mode !== BatchOperationMode.DELETE && mode !== BatchOperationMode.PURGE)) {
        throw new Error(tn('confirmation_text_required'));
      }

      if (mode === BatchOperationMode.DELETE && !filter) {
        throw new Error(tn('filter_is_required'));
      }

      const batch = await deleteRecords(
        mode === BatchOperationMode.PURGE
          ? // When purging the entity Data, we don't allow a filter, and we only delete "local" data
            {
              entityId: entity.id,
              deleteInEndSystem: false,
            }
          : // otherwise, when deleting data, we require a filter and the user can select
            {
              entityId: entity.id,
              deleteInEndSystem: deleteType === DeleteType.GLOBAL,
              predicate: filter ? packageData(filter.criteria) : undefined,
            }
      ).unwrap();

      // batch should be guaranteed here, but let's be extra safe
      if (batch) {
        // optimistically update our general query as well as the DELETE specific query
        dispatch(
          dataStudioBatchApiUtil.updateQueryResult('getBatchesForEntity', { entityId: entity.id }, (draft) => {
            draft.push(batch);
            return draft;
          })
        );

        dispatch(
          dataStudioBatchApiUtil.updateQueryResult(
            'getBatchesForEntity',
            { entityId: entity.id, operation: BatchOperation.DELETE },
            (draft) => {
              draft.push(batch);
              return draft;
            }
          )
        );
        message.success(tn('delete_request_successful', i18nArgs));
        closeModal();
      } else {
        throw new Error(tn('generic_error'));
      }
    } catch (err) {
      if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError((err as any)?.message || (err as any)?.data?.message || tn('generic_error'));
      }

      return;
    }
  };

  const modalTitle = tn(mode === BatchOperationMode.PURGE ? 'purge_modal_title' : 'delete_modal_title', { entityName });

  return (
    <EnhancedModal
      title={modalTitle}
      onOk={() => handleRequestDelete()}
      okButtonProps={{
        title: okButtonTitle,
        type: 'danger',
        disabled: actionIsDisabled,
        loading: isDeleting,
      }}>
      <Stack>
        <InlineMessage className="synri-inline-message-no-bottom-margin" type="error">
          {formError && <Text>{formError}</Text>}
        </InlineMessage>

        <Stack spacing="lg">
          {
            // if we're in DELETE mode, show scope and the user can select the type of deletion
            mode === BatchOperationMode.DELETE && [
              <Stack key="scope" spacing="sm">
                <TranslatedText text="scope_title" weight="semibold" />
                {filter ? (
                  [
                    <TranslatedText key="label" beDangerous text="scope_with_filter" args={i18nArgs} />,
                    <EntityFilterPreview key="filter" fieldValues={fieldValues} filter={filter} />,
                  ]
                ) : (
                  <TranslatedText beDangerous text="scope_without_filter" args={i18nArgs} />
                )}
              </Stack>,
              <Stack key="deleteType" spacing="sm">
                <TranslatedText text="settings_title" weight="semibold" />
                <Radio.Group
                  className="delete-type-radio-group"
                  onChange={(evt) => setDeleteType(evt.target.value)}
                  value={deleteType}>
                  <Stack spacing="xs">
                    <Radio value={DeleteType.LOCAL}>{tn('local_delete', { count: i18nArgs?.count })}</Radio>
                    <Radio value={DeleteType.GLOBAL}>{tn('global_delete', { count: i18nArgs?.count })}</Radio>
                  </Stack>
                </Radio.Group>
              </Stack>,
              <Divider key="divider" />,
            ]
          }

          <Stack spacing="sm">
            <Text beDangerous>{deleteWarningText}</Text>

            <label htmlFor="delete-confirmation">
              <TranslatedText beDangerous text="delete_confirmation_warning" />
            </label>
            <Input
              id="delete-confirmation"
              required
              placeholder={deleteConfirmationPlaceholder}
              onChange={(evt) => setConfirmationText(evt.target.value)}
            />
          </Stack>
        </Stack>
      </Stack>
    </EnhancedModal>
  );
};

export default DeleteModal;
