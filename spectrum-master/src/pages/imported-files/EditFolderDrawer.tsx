import { message } from 'antd';
import { useEffect, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { DrawerTextInput } from 'components/inputs/drawer/DrawerInput';
import { Stack } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useEditFolderMutation } from 'store/imported-files/api';
import { closeDrawer } from 'store/imported-files/slice';
import { UploadFolder } from 'store/imported-files/types';
import { tNamespaced } from 'utils/i18nUtil';

export interface EditSideDrawerProps {
  currentFolder: UploadFolder | undefined;
}

const EditSideDrawer = ({ currentFolder }: EditSideDrawerProps) => {
  const tn = tNamespaced('ImportedFiles');

  const drawerOpen = useEnhancedSelector((state) => state.importedFiles.drawerOpen);
  const dispatch = useEnhancedDispatch();

  const [editFolderMutation] = useEditFolderMutation();

  const [isUploading, setIsUploading] = useState(false);

  const [description, setDescription] = useState(currentFolder?.description || '');

  const resetForm = () => {
    setDescription(currentFolder?.description || '');
  };

  const resetFormAndCloseDrawer = () => {
    resetForm();
    setIsUploading(false);
    dispatch(closeDrawer());
  };

  const cancel = () => {
    resetFormAndCloseDrawer();
  };

  const uploadChanges = async () => {
    setIsUploading(true);
    editFolderMutation({
      ...currentFolder,
      id: currentFolder?.id as string,
      name: currentFolder?.name as string,
      description,
      files: [],
    })
      .unwrap()
      .then((res) => {
        resetFormAndCloseDrawer();
        message.success(tn('changes_saved'));
      })
      .catch((err) => {
        message.error(err.data.message);
        setIsUploading(false);
      });
  };

  useEffect(() => {
    if (currentFolder) {
      setDescription(currentFolder?.description);
    }
  }, [currentFolder]);

  return (
    <DrawerPanel
      className="upload-side-drawer"
      title={tn('edit_folder')}
      mask
      onClose={cancel}
      visible={drawerOpen}
      footer={
        <>
          <Button key="cancel" onClick={cancel}>
            {tn('cancel')}
          </Button>
          <Button loading={isUploading} key="ok" type="primary" onClick={uploadChanges}>
            {tn('save')}
          </Button>
        </>
      }>
      <form>
        <Stack>
          <DrawerTextInput
            disabled
            labelActionDisabled
            label={tn('folder_name')}
            labelActionText={tn('select_existing_folder')}
            value={currentFolder?.name as string}
          />
          <DrawerTextInput
            label={tn('description')}
            textArea
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setDescription(e.target.value)}
            value={description}
          />
        </Stack>
      </form>
    </DrawerPanel>
  );
};
export default EditSideDrawer;
