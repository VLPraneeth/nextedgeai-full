import React from 'react';
import { renderWithRouter, screen } from 'tests/helpers';
import { DataQuality } from '../DataQuality';

const mockUseGetDFIProvisionStatusQuery = jest.fn();
const mockUseGetRulesListQuery = jest.fn();
const mockUseGetCategoriesListQuery = jest.fn();
const mockUseSaveCategoriesMutation = jest.fn();
const mockUseDeleteCategoryMutation = jest.fn();
const mockUseSaveRuleMutation = jest.fn();
const mockUseGetRulesMetadataQuery = jest.fn();
const mockUsePatchPipelineSettingsMutation = jest.fn();
const mockUseGetReferenceDataSetsQuery = jest.fn();
jest.mock('store/data-quality-v2/api', () => ({
  useGetDFIProvisionStatusQuery: () => mockUseGetDFIProvisionStatusQuery(),
  useGetRulesListQuery: () => mockUseGetRulesListQuery(),
  useGetCategoriesListQuery: () => mockUseGetCategoriesListQuery(),
  useSaveCategoriesMutation: () => [mockUseSaveCategoriesMutation, {}],
  useDeleteCategoryMutation: () => [mockUseDeleteCategoryMutation, {}],
  useSaveRuleMutation: () => [mockUseSaveRuleMutation, {}],
  useGetRulesMetadataQuery: () => mockUseGetRulesMetadataQuery(),
  usePatchPipelineSettingsMutation: () => [mockUsePatchPipelineSettingsMutation, {}],
  useGetReferenceDataSetsQuery: () => mockUseGetReferenceDataSetsQuery(),
}));

jest.mock('components/I18nProvider', () => ({
  withI18n: (Component: React.ComponentType) => Component,
}));

// Mock the translation function
jest.mock('utils/i18nUtil', () => ({
  tNamespaced: () => (key: string) => {
    const translations: Record<string, string> = {
      loading: 'loading',
      data_quality_in_progress: 'Data Quality is being provisioned, please wait...',
      data_quality_disabled: 'Data Quality is currently disabled',
      enable_data_quality: 'Enable Data Quality',
      create_rule: 'Create Rule',
      manage_categories: 'Manage Categories',
    };
    return translations[key] || key;
  },
  tc: (key: string) => key,
}));

describe('DataQuality', () => {
  const mockEntityId = 'test-entity-id';

  beforeEach(() => {
    jest.clearAllMocks();
    mockUseGetRulesListQuery.mockReturnValue({
      data: [],
      isLoading: false,
      isFetching: false,
    });
    mockUseGetCategoriesListQuery.mockReturnValue({
      data: [],
      isLoading: false,
      isFetching: false,
    });
    mockUseGetRulesMetadataQuery.mockReturnValue({
      data: [],
      isLoading: false,
      isFetching: false,
    });
    mockUseGetReferenceDataSetsQuery.mockReturnValue({
      data: { referenceDataSets: [] },
      isLoading: false,
      isFetching: false,
    });
  });

  test('should render in progress state', async () => {
    mockUseGetDFIProvisionStatusQuery.mockReturnValue({
      data: { status: 'inProgress' },
      isLoading: false,
      isFetching: false,
    });

    renderWithRouter(<DataQuality entityId={mockEntityId} />);
    expect(await screen.findByText('Data Quality is being provisioned, please wait...')).toBeInTheDocument();
  });

  test('should render disabled state', async () => {
    mockUseGetDFIProvisionStatusQuery.mockReturnValue({
      data: { status: 'disabled' },
      isLoading: false,
      isFetching: false,
    });

    renderWithRouter(<DataQuality entityId={mockEntityId} />);
    expect(await screen.findByText('Data Quality is currently disabled')).toBeInTheDocument();
    expect(await screen.findByText('Enable Data Quality')).toBeInTheDocument();
  });

  test('should render enabled state', async () => {
    mockUseGetDFIProvisionStatusQuery.mockReturnValue({
      data: { status: 'enabled' },
      isLoading: false,
      isFetching: false,
    });

    renderWithRouter(<DataQuality entityId={mockEntityId} />);
    expect(await screen.findByText('Create Rule')).toBeInTheDocument();
    expect(await screen.findByText('Manage Categories')).toBeInTheDocument();
  });
});
