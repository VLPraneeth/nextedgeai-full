import { message } from 'antd';
import { useCallback } from 'react';

import { useUserInputConfirmationModal } from 'hooks/modal';
import { useDeleteFileMutation, useDeleteFolderMutation } from 'store/imported-files/api';
import { navigateTo } from 'utils/AppUtil';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export const useDeleteFolder = () => {
  const tn = tNamespaced('ImportedFiles');

  const showConfirmDeleteModal = useUserInputConfirmationModal();
  const [deleteFolderMutation] = useDeleteFolderMutation();

  const deleteFolder = useCallback(
    ({ folderId, folderName }: { folderId: string; folderName: string }) => {
      deleteFolderMutation({ folderId })
        .unwrap()
        .then(() => {
          message.success(tn('folder_delete_success', { folderName }));
          navigateTo(RouteConstants.IMPORTED_FILES);
        })
        .catch(({ data }) => {
          message.error(data.message);
        });
    },
    [deleteFolderMutation, tn]
  );

  return ({ folderId, folderName }: { folderId: string; folderName: string }) => {
    showConfirmDeleteModal({
      title: tn('folder_delete_confirm_title'),
      content: tn('folder_delete_confirm_body', { folderName }),
      okText: tn('folder_delete_confirm_ok_btn'),
      okType: 'danger',
      onOk: () => deleteFolder({ folderId, folderName }),
    });
  };
};

export const useDeleteFile = () => {
  const tn = tNamespaced('ImportedFiles');

  const showConfirmDeleteModal = useUserInputConfirmationModal();
  const [deleteFileMutation] = useDeleteFileMutation();

  const deleteFile = useCallback(
    ({ fileId, fileName, folderId }: { fileId: string; fileName: string; folderId: string }) => {
      deleteFileMutation({ fileId })
        .unwrap()
        .then(({ message: response }) => {
          if (response) {
            message.success(response);
            navigateTo(makeUrl(RouteConstants.IMPORTED_FILES_FOLDER, { folderId }));
          } else {
            message.success(tn('file_delete_success', { fileName }));
            navigateTo(makeUrl(RouteConstants.IMPORTED_FILES_FOLDER, { folderId }));
          }
        })
        .catch(({ data }) => {
          message.error(data.message);
        });
    },
    [deleteFileMutation, tn]
  );

  return ({ fileId, fileName, folderId }: { fileId: string; fileName: string; folderId: string }) => {
    showConfirmDeleteModal({
      title: tn('file_delete_confirm_title'),
      content: tn('file_delete_confirm_body', { fileName }),
      okText: tn('file_delete_confirm_ok_btn'),
      okType: 'danger',
      onOk: () => deleteFile({ fileId, fileName, folderId }),
    });
  };
};
