//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { RcFile } from 'antd/lib/upload';

import { TagValueModel } from 'components/inputs/Tag';

export enum DrawerVariants {
  upload = 'upload',
  editFile = 'editFile',
  editFolder = 'editFolder',
}
export interface ImportedFilesState {
  drawerOpen: boolean;
  drawerVariant: DrawerVariants;
  selectedFolderId: string;
}
export interface ImportedFile {
  folderId: string;
  id: string;
  idColumn: string;
  name: string;
  tags: TagValueModel;
  fileType: string;
  uploadedAt: string;
  uploadedBy: string;
  rowsCount: string;
}

type Row = string[];
export interface ImportedFilePreview {
  headerColumns: string[];
  rows: Row[];
}

export interface UploadFolderPayload {
  name: string;
  description: string;
  // Not using this for now since not sure how to get file array in form data
  files?: RcFile[];
}

export interface UploadFolder {
  description: string;
  files: ImportedFile[];
  id: string;
  name: string;
}

export interface UploadFilePayload {
  name: string;
  file: RcFile;
  folderId?: string;
  idColumn: string;
  withTrim: boolean;
  tags: TagValueModel;
}

export interface UploadFileResponse {
  description: string;
  files: ImportedFile[];
  id: string;
  name: string;
  message?: string;
}

export interface UploadFolderRejected {
  message?: string;
}

export enum AlertVariants {
  SUCCESS = 'success',
  INFO = 'info',
  WARNING = 'warning',
  ERROR = 'error',
}

export interface AlertDataType {
  alertEnabled: boolean;
  message: string;
  type: 'success' | 'info' | 'warning' | 'error';
}
