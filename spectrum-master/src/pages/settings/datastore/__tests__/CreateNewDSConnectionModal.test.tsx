import { fireEvent, screen, render, waitFor, within, userEvent } from '../../../../tests/helpers/';
import { tNamespaced } from '../../../../utils/i18nUtil';
import CreateNewDSConnectionModal from '../CreateNewDSConnectionModal';
import * as DataStoreApi from '../../../../store/datastore/api';

const tn = tNamespaced('Settings.DataStore');

const mockOnClose = jest.fn();
const titleText = 'Create New Connection';
const closeLabel = 'icon: close';

const describeMockResponse = [
  {
    id: '1',
    name: 'snowflake_datastore',
    type: 'Datastore',
    displayName: 'Snowflake',
    category: 'Datawarehouse',
    iconUri: '/assets/icons/logos/snowflake.svg',
    capabilities: [
      'create',
      'update',
      'delete',
      'search',
      'getById',
      'getByWatermark',
      'schemaEditInSyncari',
      'userEditableId',
      'userEditableWm',
    ],
    supportedAuthTypes: [
      {
        authType: 'UserPassword',
        label: 'User Password',
        fields: [
          { name: 'userName', dataType: 'text', label: 'User Name', required: true, hidden: false },
          { name: 'password', dataType: 'password', label: 'Password', required: true, hidden: false },
        ],
      },
      {
        authType: 'Oauth',
        label: 'OAuth',
        fields: [
          { name: 'clientId', dataType: 'password', label: 'Client ID', required: true, hidden: false },
          { name: 'clientSecret', dataType: 'password', label: 'Client Secret', required: true, hidden: false },
        ],
      },
    ],
    configureFields: [
      { name: 'endpoint', label: 'Snowflake URL', dataType: 'text', required: true, hidden: false },
      { name: 'accountName', label: 'Account Name', dataType: 'text', required: true, hidden: false },
      { name: 'warehouseName', label: 'Warehouse Name', dataType: 'text', required: true, hidden: false },
      { name: 'dbName', label: 'Database Name', dataType: 'text', required: true, hidden: false },
      { name: 'schemaName', label: 'Schema Name', dataType: 'text', required: true, hidden: false },
      { name: 'role', label: 'User Role', dataType: 'text', required: false, hidden: false },
      { name: 'authType', label: 'Authentication', dataType: 'picklist', required: true, hidden: false },
    ],
    oauthUri:
      '/oauth/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code&state={{state}}',
    creatable: true,
  },
];

// Mock API response for data store describe
beforeEach(() => {
  jest.spyOn(DataStoreApi, 'useGetDataStoreDescribeQuery').mockReturnValue({
    data: describeMockResponse,
    isLoading: false,
    error: null,
    refetch: jest.fn(),
  });
});

// Test helpers
const renderModal = () => {
  render(<CreateNewDSConnectionModal open onClose={mockOnClose} />);
};

const openDataStoreDropdown = async () => {
  const dsDropdown = screen.getByText(tn('select_data_store'));
  fireEvent.click(dsDropdown);
  const dropdown = await waitFor(() => document.body.querySelector('.ant-select-dropdown') as HTMLElement);
  return dropdown;
};

const selectFirstDataStore = async () => {
  const dropdown = await openDataStoreDropdown();
  const options = within(dropdown).getAllByRole('option');
  fireEvent.click(options[0]);
};

const verifyAuthTypeOptionsWithContent = async (authLabel: string, optionLabel: 'User Password' | 'OAuth') => {
  const modal = screen.getByRole('dialog');
  const labelElement = within(modal).getByText(authLabel);
  const labelContainer = labelElement.closest('.synri-container') as HTMLElement;
  const trigger = within(labelContainer).getByRole('combobox');

  await userEvent.click(trigger);

  const allDropdowns = await waitFor(() =>
    Array.from(document.querySelectorAll('.ant-select-dropdown')).filter(
      (el) => window.getComputedStyle(el).display !== 'none'
    )
  );

  const openSelect = labelContainer.querySelector('.ant-select.ant-select-open');
  expect(openSelect).toBeTruthy();

  const targetDropdown = allDropdowns[allDropdowns.length - 1] as HTMLElement;
  expect(targetDropdown).toBeTruthy();

  const options = within(targetDropdown).getAllByRole('option');
  const optionToClick = options.find((opt) => opt.textContent === optionLabel);
  expect(optionToClick).toBeTruthy();
  await userEvent.click(optionToClick!);

  const authTypeMap: Record<string, string[]> = {
    'User Password': ['User Name', 'Password'],
    OAuth: ['Client ID', 'Client Secret'],
  };

  const allFields = Object.values(authTypeMap).flat();
  const expectedFields = authTypeMap[optionLabel];

  for (const label of expectedFields) {
    await waitFor(() => {
      expect(screen.getByText(label)).toBeInTheDocument();
    });
  }

  const unexpectedFields = allFields.filter((label) => !expectedFields.includes(label));
  for (const label of unexpectedFields) {
    expect(screen.queryByLabelText(label)).not.toBeInTheDocument();
  }
};

describe('CreateNewDSConnectionModal', () => {
  it('Should render modal with title, close button, and initial input fields', async () => {
    renderModal();
    expect(screen.getByText(titleText)).toBeInTheDocument();
    expect(screen.getByLabelText(closeLabel)).toBeInTheDocument();
    expect(await screen.findByText(/Name/i)).toBeInTheDocument();
    const select = screen.getByText(tn('select_data_store'));
    expect(select).toBeInTheDocument();
  });

  it('Should close when Cancel or Close is clicked', async () => {
    renderModal();

    fireEvent.click(screen.getByLabelText(closeLabel));
    expect(mockOnClose).toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /Cancel/i }));
    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it('Should list all data store options in dropdown', async () => {
    renderModal();
    const dropdown = await openDataStoreDropdown();

    await waitFor(() => {
      const options = within(dropdown).getAllByRole('option');
      options.forEach((option) => {
        expect(option).toBeInTheDocument();
      });
    });
  });

  it('Should render all configureFields and supported auth fields after selecting a datastore', async () => {
    renderModal();

    const expectedLabels = [
      'Snowflake URL',
      'Account Name',
      'Warehouse Name',
      'Database Name',
      'Schema Name',
      'User Role',
      'Authentication',
    ];

    expectedLabels.forEach((label) => {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    });

    await selectFirstDataStore();

    for (const label of expectedLabels) {
      await waitFor(() => {
        expect(screen.getByText(label)).toBeInTheDocument();
      });
    }
  });

  it('Should render correct fields for User Password', async () => {
    renderModal();
    await selectFirstDataStore();
    await verifyAuthTypeOptionsWithContent('Authentication', 'User Password');
  });

  it('Should render correct fields for OAuth', async () => {
    renderModal();
    await selectFirstDataStore();
    await verifyAuthTypeOptionsWithContent('Authentication', 'OAuth');
  });

  it('Should render redirect input and Generate/Copy button Oauth type is selected', async () => {
    renderModal();
    await selectFirstDataStore();

    await verifyAuthTypeOptionsWithContent('Authentication', 'OAuth');

    const redirectInputLabel = await screen.findByText(tn('generate_register'));
    expect(redirectInputLabel).toBeInTheDocument();

    const labelContainer = redirectInputLabel.closest('.synri-container') as HTMLElement;
    const redirectInput = within(labelContainer).getByRole('textbox');
    expect(redirectInput).toBeDisabled();

    const generateButton = screen.getByRole('button', { name: /generate/i });
    expect(generateButton).toBeInTheDocument();
  });
});
