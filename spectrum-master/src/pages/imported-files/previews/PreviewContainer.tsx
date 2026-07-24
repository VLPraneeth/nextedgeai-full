import { useMatch } from '@reach/router';

import { AlertDataType, UploadFolder } from 'store/imported-files/types';

import FilePreview from './FilePreview';
import FolderPreview from './FolderPreview';

const PreviewContainer = ({ folder, alertData }: { folder: UploadFolder; alertData: AlertDataType }) => {
  const match = useMatch('folder/:folderId/file/:fileId');

  if (match) {
    const fileName = folder.files.find((file) => file.id === match.fileId)?.name;
    return <FilePreview folderId={folder.id} fileName={fileName!} fileId={match.fileId} />;
  }

  return <FolderPreview folder={folder} alertData={alertData} />;
};

export default PreviewContainer;
