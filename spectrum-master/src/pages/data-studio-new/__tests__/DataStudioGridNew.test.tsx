import React from 'react';
import { renderWithRouter, screen, fireEvent, waitFor } from '../../../tests/helpers';
import DataStudioGrid from '../DataStudioGrid';
import DataStudioRecordDetail from '../RecordDetail';
import { useEntityRecordsList, useEntityFiltersList } from '../../../store/data-studio/hooks';
import { useFilterFromQueryString, useUserConfiguredColumnsForEntity } from '../hooks';

// Mocks
jest.mock('../../../store/data-studio/hooks', () => ({
  useEntityRecordsList: jest.fn(),
  useEntityFiltersList: jest.fn(),
  useEntityRecord: jest.fn(),
}));

jest.mock('../hooks', () => ({
  useFilterFromQueryString: jest.fn(),
  useUserConfiguredColumnsForEntity: jest.fn(),
  useDeleteRecordDataModal: jest.fn(),
}));

jest.mock('../../../utils/i18nUtil', () => ({
  t: (key: string) => key,
  tNamespaced: (ns: string) => (key: string) => `${ns}.${key}`,
  tc: (key: string) => key,
}));

jest.mock('../../../components/I18nProvider', () => ({
  default: ({ children }: any) => <>{children}</>,
  withI18n: (Component: any) => Component,
  useI18nContext: () => ({
    tn: (key: string) => key,
    tc: (key: string) => key,
  }),
}));

describe('DataStudioGrid — Filter Button & Drawer Behavior', () => {
  const entityId = 'ASDF12345';

  const renderGrid = (overrides: any = {}) => {
    const mockState = {
      dataStudio: {
        filterCreatingStatus: { [entityId]: 'idle' },
        filterDeletingStatus: {},
        filterUpdatingStatus: {},
        entityDeletingStatus: {},
      },
      ...overrides,
    };
    return renderWithRouter(<DataStudioGrid entityId={entityId} />, { testState: mockState });
  };

  const renderRecordDetail = (pathname: string) => {
    // Mock the location.pathname for @reach/router's useLocation
    jest.spyOn(require('@reach/router'), 'useLocation').mockReturnValue({ pathname });

    const mockState = {
      dataStudio: {
        filterCreatingStatus: { [entityId]: 'idle' },
        filterDeletingStatus: {},
        filterUpdatingStatus: {},
        entityDeletingStatus: {},
        updateRecordDataErrors: {},
        updateRecordDataStatus: {},
        createRecordErrors: {},
        createRecordStatus: {},
      },
    };

    return renderWithRouter(<DataStudioRecordDetail entityId={entityId} />, { testState: mockState });
  };

  beforeEach(() => {
    jest.clearAllMocks();

    (useEntityRecordsList as jest.Mock).mockReturnValue({
      loading: false,
      error: null,
      refetch: jest.fn(),
      data: { records: [], pageInfo: { start: 'start', end: 'end' } },
      metadata: {},
    });

    (useEntityFiltersList as jest.Mock).mockReturnValue({
      data: { filters: [] },
      isLoading: false,
    });

    const useEntityRecord = require('../../../store/data-studio/hooks').useEntityRecord;
    (useEntityRecord as jest.Mock).mockReturnValue({
      data: null,
      metadata: {},
      idle: false,
      loading: false,
      entity: null,
    });

    (useUserConfiguredColumnsForEntity as jest.Mock).mockReturnValue([[], jest.fn()]);
    (useFilterFromQueryString as jest.Mock).mockReturnValue({
      appliedFilter: null,
      setAppliedFilter: jest.fn(),
    });

    const useDeleteRecordDataModal = require('../hooks').useDeleteRecordDataModal;
    (useDeleteRecordDataModal as jest.Mock).mockReturnValue(jest.fn());
  });

  it('renders the grid and shows the Filter button', async () => {
    renderGrid();

    const filterButton = await screen.findByRole('button', { name: /Filter/i });
    expect(filterButton).toBeInTheDocument();
  });

  it('opens filter dropdown with saved filters and "Create a New Filter" option', async () => {
    const mockFilters = [
      { id: '1', name: 'Test Filter 1', filter: {}, syncariEntityId: entityId },
      { id: '2', name: 'Test Filter 2', filter: {}, syncariEntityId: entityId },
    ];
    (useEntityFiltersList as jest.Mock).mockReturnValue({ data: { filters: mockFilters }, isLoading: false });

    renderGrid();
    const filterButton = await screen.findByRole('button', { name: /Filter/i });
    expect(filterButton).toBeInTheDocument();

    fireEvent.click(filterButton);
    const dropdown = await waitFor(() => {
      const el = document.body.querySelector('.ant-dropdown.filter-dropdown-overlay');
      return el;
    });

    expect(dropdown).toBeInTheDocument();

    expect(screen.getByText('Saved Filters')).toBeInTheDocument();
    expect(screen.getByText('Create a New Filter')).toBeInTheDocument();

    mockFilters.forEach(({ name }) => expect(screen.getByText(name)).toBeInTheDocument());
  });

  it.each([
    { action: 'View Filter', expectedTitle: 'Test Filter' },
    { action: 'Edit Filter', expectedTitle: 'Test Filter' },
  ])('opens "%s" Drawer when is selected from kebab menu', async ({ action, expectedTitle }) => {
    const mockFilters = [{ id: '1', name: 'Test Filter', filter: {}, syncariEntityId: entityId }];
    (useEntityFiltersList as jest.Mock).mockReturnValue({ data: { filters: mockFilters }, isLoading: false });

    renderGrid();

    // Open filter dropdown
    fireEvent.click(await screen.findByRole('button', { name: /Filter/i }));
    const dropdown = await waitFor(() => {
      const el = document.body.querySelector('.ant-dropdown.filter-dropdown-overlay');
      expect(el).toBeInTheDocument();
      return el;
    });

    // Open kebab menu
    const kebabButton = dropdown!.querySelector('.filter-line-item-actions .synri-icon-button')!;
    fireEvent.click(kebabButton);

    const kebabMenu = await waitFor(() => document.body.querySelector('.synri-kebab-menu-dropdown'));
    expect(kebabMenu).toBeTruthy();

    // Select target menu item
    const normalize = (t: string) => t.trim().toLowerCase().replace(/\s+/g, '_');
    const targetItem = Array.from(kebabMenu!.querySelectorAll('li[role="menuitem"]')).find(
      (el) => normalize(el.textContent || '') === normalize(action)
    );
    expect(targetItem).toBeTruthy();

    fireEvent.click(targetItem!);

    // Verify drawer opens with correct title
    const drawerTitle = await screen.findByText(expectedTitle, { selector: '.ant-drawer-title' });
    expect(drawerTitle).toBeInTheDocument();
  });

  // Record Detail Drawer Tests
  it('renders "View Record" header when pathname includes /view', () => {
    renderRecordDetail(`/data-studio/${entityId}/record/123/view`);
    expect(screen.getByText('View Record')).toBeInTheDocument();
    expect(screen.getByTestId('record-detail-drawer')).toHaveAttribute('data-drawer-visible', 'true');
  });

  it('renders "Edit Record" header when pathname includes /fields', () => {
    renderRecordDetail(`/data-studio/${entityId}/record/123/fields`);
    expect(screen.getByText('Edit Record')).toBeInTheDocument();
    expect(screen.getByTestId('record-detail-drawer')).toHaveAttribute('data-drawer-visible', 'true');
  });

  it('renders "Create New Record" header when pathname includes /create', () => {
    renderRecordDetail(`/data-studio/${entityId}/record/create`);
    expect(screen.getByText('Create New Record')).toBeInTheDocument();
    expect(screen.getByTestId('record-detail-drawer')).toHaveAttribute('data-drawer-visible', 'true');
  });
});
