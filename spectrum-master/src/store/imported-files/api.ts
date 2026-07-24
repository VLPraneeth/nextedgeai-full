//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { ImportedFile, UploadFolder, UploadFolderPayload, ImportedFilePreview } from './types';

const importedFilesApi = injectEndpoints({
  endpoints: (builder) => ({
    // Get the list of custom synapse the user have authoring access
    getImportedFoldersList: builder.query<UploadFolder[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.IMPORTED_FILES_FOLDERS }),
      providesTags: (result) => [
        ...(result || []).map((importedFolder: UploadFolder) => tags.ImportedFiles(importedFolder.id)),
        tags.ImportedFilesList,
      ],
    }),
    // Get the list of custom synapse the user have authoring access
    getImportedFile: builder.query<ImportedFile, { fileId: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.GET_IMPORT_FILE, params) }),
    }),
    getImportedFilePreview: builder.query<ImportedFilePreview, { fileId: string }>({
      query: ({ fileId }) => ({ url: makeUrl(DataUrlConstants.GET_IMPORTED_FILE_PREVIEW, { fileId }) }),
    }),
    // Create a new folder with a file
    createFolder: builder.mutation<UploadFolder, UploadFolderPayload>({
      query: (params) => {
        return {
          url: DataUrlConstants.IMPORTED_FILES_FOLDERS,
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.ImportedFilesList],
    }),
    // Edit folder
    editFolder: builder.mutation<UploadFolder, UploadFolder>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.EDIT_IMPORTED_FOLDER, { folderId: params.id }),
          method: 'PUT',
          body: params,
        };
      },
      invalidatesTags: [tags.ImportedFilesList],
    }),
    deleteFolder: builder.mutation<{ status: string; message: string }, { folderId: string }>({
      query: ({ folderId }) => {
        return {
          url: makeUrl(DataUrlConstants.EDIT_IMPORTED_FOLDER, { folderId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.ImportedFilesList],
    }),
    editFile: builder.mutation<ImportedFile, { tags: string[]; fileId: string; fileName: string }>({
      query: ({ tags, fileId, fileName }) => {
        return {
          url: makeUrl(DataUrlConstants.GET_IMPORT_FILE, { fileId }),
          method: 'PUT',
          body: { tags, name: fileName },
        };
      },
      invalidatesTags: [tags.ImportedFilesList],
    }),
    deleteFile: builder.mutation<{ status: string; message: string }, { fileId: string }>({
      query: ({ fileId }) => {
        return {
          url: makeUrl(DataUrlConstants.GET_IMPORT_FILE, { fileId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.ImportedFilesList],
    }),
  }),
});

export const {
  useCreateFolderMutation,
  useEditFolderMutation,
  useDeleteFolderMutation,
  useEditFileMutation,
  useDeleteFileMutation,
  useGetImportedFoldersListQuery,
  useGetImportedFileQuery,
  useGetImportedFilePreviewQuery,
  util: importedFilesApiUtil,
} = importedFilesApi;
