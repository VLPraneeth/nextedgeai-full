import { LocationProvider } from '@reach/router';
import { noop } from 'lodash';

import { render, screen, userEvent } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import EmptyState from './EmptyState';
import Sidebar from './Sidebar';
import UploadSideDrawer from './UploadSideDrawer';

interface RenderComponentProps {
  sidebar?: undefined | boolean;
  uploadSideDrawer?: boolean | undefined;
  emptyState?: boolean | undefined;
  drawerOpen?: boolean;
}

const renderComponent = ({ sidebar, uploadSideDrawer, emptyState, drawerOpen = false }: RenderComponentProps) =>
  render(
    <LocationProvider>
      {sidebar && <Sidebar />}
      {uploadSideDrawer && <UploadSideDrawer />}
      {emptyState && <EmptyState onClick={noop} currentFolder="folder 1" hasFolders />}
    </LocationProvider>,
    {
      testState: {
        importedFiles: {
          drawerOpen,
        },
        user: {
          privileges: [AllPermissions.READ_FILE_DATA, AllPermissions.WRITE_FILE_DATA, AllPermissions.DELETE_FILE_DATA],
        },
      },
    }
  );

describe('UploadSideDrawer', () => {
  it('opens when clicking on the "upload file" button', async () => {
    renderComponent({ sidebar: true, uploadSideDrawer: true });
    await userEvent.click(screen.getByRole('button', { name: 'Upload icon Upload file' }));
    expect(await screen.findByText('New file')).toBeVisible();
  });
  it('opens when clicking on the empty state "upload file" button', async () => {
    renderComponent({ emptyState: true, uploadSideDrawer: true });
    await userEvent.click(screen.getByRole('button', { name: '+ Upload file' }));
    expect(await screen.findByText('New file')).toBeVisible();
  });

  // TODO: Add testing for file upload
  it('displays an error when trying to upload a csv file that has no data', noop);
  it('displays an error when upload is pressed and file name is empty', noop);
  it('displays an error when upload is pressed and folder name is empty', noop);
});
