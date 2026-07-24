import { message } from 'antd';
import { RcFile } from 'antd/lib/upload';
import { first, orderBy, sortBy } from 'lodash';
import { parse, ParseResult } from 'papaparse';
import { useEffect, useMemo, useReducer, useState } from 'react';

import Button from 'components/Button';
import { CheckboxChangeEvent } from 'components/Checkbox';
import DrawerPanel from 'components/DrawerPanel';
import {
  DrawerCheckboxInput,
  DrawerFileInput,
  DrawerSelectInput,
  DrawerTagInput,
  DrawerTextInput,
} from 'components/inputs/drawer/DrawerInput';
import { TagValueModel } from 'components/inputs/Tag';
import { Stack } from 'components/layout';
import { ChangesInProgressModalVariants } from 'components/modals/ChangesInProgressModal';
import { filterEmptyCsvRows } from 'components/SingleFileUploadBox';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useNavigateTo from 'hooks/useNavigateTo';
import { setChangesInProgress, setChangesInProgressModal } from 'store/app/actions';
import { EMPTY_ARRAY } from 'store/constants';
import { useCreateFolderMutation } from 'store/imported-files/api';
import { closeDrawer } from 'store/imported-files/slice';
import { createImportFile } from 'store/imported-files/thunks';
import { AlertDataType, AlertVariants, UploadFolder, UploadFolderRejected } from 'store/imported-files/types';
import { tNamespaced } from 'utils/i18nUtil';
import { alphaNumericRegEx, validFileNameRegEx } from 'utils/RegexUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

// Input entry types
const SELECT = 'SELECT';
const TEXT_ENTRY = 'TEXT_ENTRY';
const FILE_TYPE_RESTRICTION = '.csv';

// 200mb Max csv upload size
const MAX_FILE_UPLOAD_SIZE = 200000000;

interface InitialErrorStates {
  file: string;
  fileName: string;
  folderName: string;
}

const initialErrorStates = {
  file: '',
  fileName: '',
  folderName: '',
};

enum ErrorActionTypes {
  file = 'file',
  fileName = 'fileName',
  folderName = 'folderName',
}

type ErrorAction =
  | { type: ErrorActionTypes.file; payload: string }
  | { type: ErrorActionTypes.fileName; payload: string }
  | { type: ErrorActionTypes.folderName; payload: string };

function errorStatesReducer(state: InitialErrorStates, action: ErrorAction) {
  switch (action.type) {
    case ErrorActionTypes.file:
      return { ...state, file: action.payload };
    case ErrorActionTypes.fileName:
      return { ...state, fileName: action.payload };
    case ErrorActionTypes.folderName:
      return { ...state, folderName: action.payload };
    default:
      throw new Error();
  }
}
export interface UploadSideDrawerProps {
  folders?: UploadFolder[];
  setAlertData?: (data: AlertDataType) => void;
}

const UploadSideDrawer = ({ folders, setAlertData }: UploadSideDrawerProps) => {
  const tn = tNamespaced('ImportedFiles');
  const fileNameError = tn('file_name_error');
  const folderNameError = tn('folder_name_error');

  const dispatch = useEnhancedDispatch();

  const importedFilesStore = useEnhancedSelector((state) => state.importedFiles);
  const changesInProgress = useEnhancedSelector((state) => state.app.changesInProgress);

  const [createImportFolder] = useCreateFolderMutation();

  const [errorStates, errorsDispatch] = useReducer(errorStatesReducer, initialErrorStates);
  const [isUploading, setUploading] = useState(false);

  //form controls
  const [folderNameInputType, setFolderNameInputType] = useState(SELECT);
  const [folderName, setFolderName] = useState('');
  const [folderId, setFolderId] = useState('');
  const [withTrim, setWithTrim] = useState(true);
  const [description, setDescription] = useState('');
  const [fileHeaders, setFileHeaders] = useState(['']);
  const [fileName, setFileName] = useState('');
  const [fileId, setFileId] = useState('');
  const [uploadFile, setUploadFile] = useState<RcFile>();
  const [tags, setTags] = useState<TagValueModel>(EMPTY_ARRAY);

  const navigate = useNavigateTo();

  const sortedFolders = sortBy(folders, 'name');

  const folderOptions = sortedFolders?.map((folder) => {
    return {
      value: folder.id,
      label: folder.name,
    };
  });

  // Set the fileId to match the idColumn of the first file uploaded so we don't
  // change it on subsequent uploads
  useEffect(() => {
    const selectedFolderIdColumn = first(
      orderBy(sortedFolders?.find((folder) => folder.id === folderId)?.files, 'uploadedAt', 'asc')
    )?.idColumn;

    if (selectedFolderIdColumn && selectedFolderIdColumn !== fileId) {
      setFileId(selectedFolderIdColumn);
    }
  }, [fileId, folderId, sortedFolders]);

  const fileHeaderOptions = useMemo(() => {
    const options = fileHeaders.map((header) => {
      const parsedHeader = header.replace(/['"]+/g, '');
      return {
        value: parsedHeader,
        label: parsedHeader,
      };
    });

    return orderBy(options, 'label');
  }, [fileHeaders]);

  useEffect(() => {
    if (importedFilesStore.selectedFolderId) {
      setFolderId(importedFilesStore.selectedFolderId);
      setFolderNameInputType(SELECT);
    } else {
      setFolderId('');
      setFolderNameInputType(TEXT_ENTRY);
    }
  }, [importedFilesStore.selectedFolderId]);

  const clearFormValidationErrors = () => {
    errorsDispatch({ type: ErrorActionTypes.file, payload: initialErrorStates.file });
    errorsDispatch({ type: ErrorActionTypes.fileName, payload: initialErrorStates.fileName });
    errorsDispatch({ type: ErrorActionTypes.folderName, payload: initialErrorStates.folderName });
  };

  const parseFile = (file: RcFile) => {
    clearFormValidationErrors();

    if (file.size > MAX_FILE_UPLOAD_SIZE) {
      errorsDispatch({ type: ErrorActionTypes.file, payload: tn('file_too_large') });
      return true;
    }
    parse(file, {
      header: true,
      dynamicTyping: true,
      error: (err) => {
        errorsDispatch({ type: ErrorActionTypes.file, payload: tn('file_error_generic') });
      },
      complete: (results: ParseResult<RcFile>) => {
        const headers = results.meta.fields;
        const hasHeaders = headers?.every((item) => item !== '') && headers?.length !== 0;

        const emptyRowData = filterEmptyCsvRows(results.data).length === 0;

        // no headers
        if (!emptyRowData && !hasHeaders) {
          errorsDispatch({ type: ErrorActionTypes.file, payload: tn('file_no_headers') });
          return false;
        }
        // empty csv
        if (emptyRowData && !hasHeaders) {
          errorsDispatch({ type: ErrorActionTypes.file, payload: tn('file_empty') });
          return false;
        }
        // successful upload
        if (headers) {
          if (folderName === '') {
            setFolderName(file.name.replace(alphaNumericRegEx, ''));
          }
          setUploadFile(file);
          setFileName(file.name.replace(validFileNameRegEx, ''));
          setFileId(headers[0]);
          setFileHeaders(headers.map((header) => header.trim()));
          errorsDispatch({ type: ErrorActionTypes.file, payload: '' });
        }
      },
    });
    return true;
  };

  const clearFormFields = () => {
    setDescription('');
    setFileName('');
    setFileId('');
    setFolderName('');
    setWithTrim(true);
    setUploadFile(undefined);
    setFileHeaders(EMPTY_ARRAY);
  };

  const resetForm = () => {
    clearFormValidationErrors();
    clearFormFields();
  };

  const resetFormAndCloseDrawer = () => {
    resetForm();
    dispatch(setChangesInProgress(false));
    dispatch(closeDrawer());
  };

  const fieldsAreValid = () => {
    if (folderName === '' && fileName === '') {
      errorsDispatch({ type: ErrorActionTypes.fileName, payload: fileNameError });
      errorsDispatch({ type: ErrorActionTypes.folderName, payload: folderNameError });
      return false;
    }
    if (folderNameInputType === TEXT_ENTRY && folderName === '') {
      errorsDispatch({ type: ErrorActionTypes.folderName, payload: folderNameError });
      return false;
    }
    if (fileName === '') {
      errorsDispatch({ type: ErrorActionTypes.fileName, payload: fileNameError });
      return false;
    }
    return true;
  };

  const handleUpload = async () => {
    setUploading(true);

    // Reset alert data
    const alertData: AlertDataType = {
      alertEnabled: false,
      type: AlertVariants.INFO,
      message: '',
    };

    if (fieldsAreValid()) {
      let fileFolderId: string | false = folderId;
      // Create the folder if the user typed a folder name
      if (folderNameInputType === TEXT_ENTRY) {
        await createImportFolder({
          name: folderName,
          description,
        })
          .unwrap()
          .then((res) => {
            fileFolderId = res.id;
          })
          .catch((err) => {
            message.error(err?.data?.message);
            fileFolderId = false;
          });
      }

      // We'll always have an uploadFile, this check is for typescript
      if (uploadFile !== undefined && fileFolderId) {
        await dispatch(
          createImportFile({
            name: fileName,
            file: uploadFile,
            folderId: fileFolderId,
            idColumn: fileId,
            withTrim,
            tags,
          })
        ).then((result) => {
          if (result.meta.requestStatus === 'rejected') {
            message.error((result.payload as UploadFolderRejected)?.message);
          } else {
            message.success(tn('success_message'));
            navigate(makeUrl(RouteConstants.IMPORTED_FILES_FOLDER, { folderId: fileFolderId }));
            resetFormAndCloseDrawer();
            // Set alert data to display warning message
            if (result?.payload?.message) {
              alertData.alertEnabled = true;
              alertData.type = AlertVariants.WARNING;
              alertData.message = result.payload.message;
            }
          }
        });
      }
      setAlertData?.(alertData);
      setUploading(false);
    }
  };

  const toggleFolderNameInput = () => {
    if (folderNameInputType === TEXT_ENTRY && folders) {
      setFolderNameInputType(SELECT);
      setFolderName(sortedFolders[0]?.name);
      setFolderId(sortedFolders[0]?.id);
      return;
    }

    setFolderNameInputType(TEXT_ENTRY);
    setFolderName('');
    setFolderId('');
  };

  const cancel = () => {
    if (changesInProgress) {
      dispatch(
        setChangesInProgressModal({
          visible: true,
          variant: ChangesInProgressModalVariants.upload,
          discardChangesAction: () => {
            resetFormAndCloseDrawer();
          },
          keepEditingAction: () => {},
        })
      );
    } else {
      resetFormAndCloseDrawer();
    }
  };

  useEffect(() => {
    if ((folderName !== '' && folderNameInputType !== SELECT) || description !== '' || fileName !== '') {
      dispatch(setChangesInProgress(true));
      dispatch(setChangesInProgressModal({ variant: ChangesInProgressModalVariants.upload }));
    }
  }, [description, dispatch, fileName, folderName, folderNameInputType]);

  return (
    <DrawerPanel
      className="upload-side-drawer"
      title={tn('new_file')}
      mask
      onClose={cancel}
      visible={importedFilesStore.drawerOpen}
      footer={
        <>
          <Button key="cancel" onClick={cancel}>
            {tn('cancel')}
          </Button>
          <Button loading={isUploading} key="ok" type="primary" onClick={handleUpload} disabled={!uploadFile}>
            {tn('upload')}
          </Button>
        </>
      }>
      <form>
        <Stack>
          {folderNameInputType === SELECT ? (
            <DrawerSelectInput
              options={folderOptions || EMPTY_ARRAY}
              label={tn('folder_name')}
              labelActionText={tn('upload_to_new_folder')}
              onLabelActionClick={toggleFolderNameInput}
              onChange={(folderId: string) => setFolderId(folderId)}
              value={folderId}
            />
          ) : (
            <DrawerTextInput
              label={tn('folder_name')}
              labelActionText={tn('select_existing_folder')}
              labelActionDisabled={folders?.length === 0}
              onLabelActionClick={folders && toggleFolderNameInput}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setFolderName(e.target.value.replace(alphaNumericRegEx, ''))
              }
              value={folderName}
              error={errorStates.folderName}
            />
          )}
          <DrawerTextInput
            label={tn('description')}
            textArea
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setDescription(e.target.value)}
            value={description}
          />
          <DrawerCheckboxInput
            label={tn('trim_values')}
            onChange={(e: CheckboxChangeEvent) => setWithTrim(e.target.checked)}
            value={withTrim}
          />
          <DrawerFileInput
            helpText={tn('file_limit_help_text')}
            file={uploadFile || undefined}
            beforeUpload={(file: RcFile) => parseFile(file)}
            label={tn('upload_data_file')}
            fileTypeRestriction={FILE_TYPE_RESTRICTION}
            showMetaData
            onRemove={resetForm}
            error={errorStates.file}
          />
          {uploadFile && (
            <Stack>
              <DrawerTextInput
                label={tn('file_name')}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setFileName(e.target.value.replace(validFileNameRegEx, ''))
                }
                value={fileName}
                error={errorStates.fileName}
              />
              <DrawerSelectInput
                options={fileHeaderOptions}
                label={tn('id')}
                onChange={(item: string) => setFileId(item)}
                value={fileId}
                disabled={Boolean(folderId)}
              />
              <DrawerTagInput
                id="upload-side-drawer-tags"
                label={tn('tags')}
                value={tags}
                onChange={(values) => setTags(values)}
              />
            </Stack>
          )}
        </Stack>
      </form>
    </DrawerPanel>
  );
};
export default UploadSideDrawer;
