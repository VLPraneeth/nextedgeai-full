import { filePreview } from 'mocks/fixtures/importedFiles/filePreview';
import { folderPreview } from 'mocks/fixtures/importedFiles/folderPreview';
import { rest } from 'msw';

import DataUrlConstants from 'utils/DataUrlConstants';

const handlers = [
  rest.post(DataUrlConstants.IMPORTED_FILES_FOLDERS, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(folderPreview));
  }),
  rest.post(DataUrlConstants.IMPORT_FILE, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(filePreview));
  }),
  rest.post(DataUrlConstants.GET_IMPORTED_FILE_PREVIEW, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(filePreview));
  }),
];

export default handlers;
