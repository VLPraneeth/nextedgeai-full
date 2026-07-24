import * as Types from './types';

export function _getDefaultState(): Types.ImportedFilesState {
  return {
    drawerOpen: false,
    drawerVariant: Types.DrawerVariants.upload,
    selectedFolderId: '',
  };
}
