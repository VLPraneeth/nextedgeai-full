import { message } from 'antd';
import { useEffect, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { DrawerTextInput, DrawerTagInput } from 'components/inputs/drawer/DrawerInput';
import { TagValueModel } from 'components/inputs/Tag';
import { Stack } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { useEditFileMutation, useGetImportedFileQuery } from 'store/imported-files/api';
import { closeDrawer } from 'store/imported-files/slice';
import { UploadFolder } from 'store/imported-files/types';
import { tNamespaced } from 'utils/i18nUtil';
import { alphaNumericRegEx } from 'utils/RegexUtil';

export interface EditSideDrawerProps {
  currentFolder: UploadFolder | undefined;
  fileId: string;
}

const EditFileDrawer = ({ currentFolder, fileId }: EditSideDrawerProps) => {
  const tn = tNamespaced('ImportedFiles');

  const { data: fileData, refetch } = useGetImportedFileQuery({ fileId });

  const drawerOpen = useEnhancedSelector((state) => state.importedFiles.drawerOpen);

  const dispatch = useEnhancedDispatch();

  const [editFileMutation, { isLoading }] = useEditFileMutation();

  const [fileName, setFileName] = useState('');
  const [tags, setTags] = useState<TagValueModel>(EMPTY_ARRAY);

  const resetFormAndCloseDrawer = () => {
    dispatch(closeDrawer());
    refetch();
    if (fileData) {
      setFileName(fileData.name);
      setTags(fileData.tags);
    }
  };

  const cancel = () => {
    resetFormAndCloseDrawer();
  };

  const uploadChanges = async () => {
    editFileMutation({
      fileId,
      fileName,
      tags,
    })
      .unwrap()
      .then((res) => {
        resetFormAndCloseDrawer();
        message.success(tn('changes_saved'));
      })
      .catch((err) => {
        message.error(err.data.message);
      });
  };

  const errorStates = { fileName: '' };

  useEffect(() => {
    if (fileData) {
      setTags(fileData.tags);
      setFileName(fileData.name);
    }
  }, [fileData, fileId, currentFolder]);

  return (
    <DrawerPanel
      className="upload-side-drawer"
      title={tn('edit_file')}
      mask
      onClose={cancel}
      visible={drawerOpen}
      footer={
        <>
          <Button key="cancel" onClick={cancel}>
            {tn('cancel')}
          </Button>
          <Button loading={isLoading} key="ok" type="primary" onClick={uploadChanges}>
            {tn('save')}
          </Button>
        </>
      }>
      <form>
        <Stack>
          <DrawerTextInput
            label={tn('file_name')}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setFileName(e.target.value.replace(alphaNumericRegEx, ''))
            }
            value={fileName}
            error={errorStates.fileName}
          />
          <DrawerTagInput
            id="upload-side-drawer-tags"
            label={tn('tags')}
            value={tags}
            onChange={(values) => setTags(values)}
          />
        </Stack>
      </form>
    </DrawerPanel>
  );
};
export default EditFileDrawer;
