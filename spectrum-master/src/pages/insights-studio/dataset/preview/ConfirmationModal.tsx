import { Button, message, Modal } from 'antd';
import { useCallback, useMemo } from 'react';

import { useCancelExportMutation, useDeleteExportMutation } from 'store/insights-studio';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { ExportJobActions } from './PreviewModal';

interface Props {
  isModalOpen: boolean;
  setIsModalOpen: (isModalOpen: boolean) => void;
  exportJobId: string | undefined;
  exportJobAction: ExportJobActions | undefined;
}

const tn = tNamespaced('InsightsStudio');

export function ConfirmationModal({ isModalOpen, setIsModalOpen, exportJobAction, exportJobId }: Props) {
  const [deleteExport, { isLoading: isDeleting }] = useDeleteExportMutation();
  const [cancelExport, { isLoading: isCancelling }] = useCancelExportMutation();

  const handleConfirm = useCallback(() => {
    const actionFunction = {
      DELETE: deleteExport,
      CANCEL: cancelExport,
    };
    if (exportJobId && exportJobAction) {
      actionFunction[exportJobAction](exportJobId)
        .unwrap()
        .catch((error) => {
          message.error(getRtkQueryErrorMessage(error));
        })
        .finally(() => {
          setIsModalOpen(false);
        });
    }
  }, [cancelExport, deleteExport, exportJobAction, exportJobId, setIsModalOpen]);

  const modalTitle = useMemo(() => {
    return { DELETE: tn('delete_export_title'), CANCEL: tn('cancel_export_title') };
  }, []);

  const modalBody = useMemo(() => {
    return { DELETE: tn('delete_export_body'), CANCEL: tn('cancel_export_body') };
  }, []);

  if (!exportJobAction) {
    return null;
  }

  return (
    <Modal
      title={modalTitle[exportJobAction]}
      visible={isModalOpen}
      footer={
        <>
          <Button key="cancel" onClick={() => setIsModalOpen(false)}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" loading={isCancelling || isDeleting} onClick={handleConfirm}>
            {tc('yes')}
          </Button>
        </>
      }
      onCancel={() => setIsModalOpen(false)}>
      <p>{modalBody[exportJobAction]}</p>
    </Modal>
  );
}
