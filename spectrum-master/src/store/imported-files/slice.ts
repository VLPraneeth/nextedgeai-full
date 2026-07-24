import { createSlice, PayloadAction } from '@reduxjs/toolkit';

import * as Types from './types';

const initialState: Types.ImportedFilesState = {
  drawerOpen: false,
  drawerVariant: Types.DrawerVariants.upload,
  selectedFolderId: '',
};

type DrawerAction = PayloadAction<{ folderId: string }>;

const importedFilesSlice = createSlice({
  name: 'importedFiles',
  initialState,
  reducers: {
    openUploadDrawer: (state, { payload: { folderId } }: DrawerAction) => {
      state.drawerOpen = true;
      state.selectedFolderId = folderId || '';
      state.drawerVariant = Types.DrawerVariants.upload;
    },
    openEditFolderDrawer: (state, { payload: { folderId } }: DrawerAction) => {
      state.drawerOpen = true;
      state.selectedFolderId = folderId || '';
      state.drawerVariant = Types.DrawerVariants.editFolder;
    },
    openEditFileDrawer: (state, { payload: { folderId } }: DrawerAction) => {
      state.drawerOpen = true;
      state.selectedFolderId = folderId || '';
      state.drawerVariant = Types.DrawerVariants.editFile;
    },
    closeDrawer: () => {
      return initialState;
    },
    setSelectedFolderId: (state, { payload: { folderId } }: DrawerAction) => {
      state.selectedFolderId = folderId || '';
    },
  },
});

export const {
  reducer,
  actions: { openUploadDrawer, openEditFolderDrawer, openEditFileDrawer, closeDrawer, setSelectedFolderId },
} = importedFilesSlice;
