import { LocationProvider } from '@reach/router';
import { fileMetaData } from 'mocks/fixtures/importedFiles/fileMetaData';

import { render, screen } from 'tests/helpers';

import PreviewContainer from './PreviewContainer';

const mockFolder = {
  description: '',
  files: [fileMetaData],
  id: '',
  name: '',
};

const mockFolderNoFiles = {
  description: '',
  files: [],
  id: '',
  name: '',
};

const mockAlertData = {
  alertEnabled: false,
  message: '',
  type: 'info',
};

describe('PreviewContainer', () => {
  it("displays folder preview if the url does not contain 'file'", async () => {
    render(
      <LocationProvider>
        <PreviewContainer folder={mockFolderNoFiles} alertData={mockAlertData} />
      </LocationProvider>
    );

    expect(await screen.findByText('0 Files')).toBeVisible();
  });
  it("displays file preview if the url contains 'file'", async () => {
    render(
      <LocationProvider>
        <PreviewContainer folder={mockFolder} alertData={mockAlertData} />
      </LocationProvider>
    );

    expect(await screen.findByText(fileMetaData.name)).toBeVisible();
  });
});
