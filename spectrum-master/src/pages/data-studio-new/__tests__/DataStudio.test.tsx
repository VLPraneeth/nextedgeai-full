import React from 'react';
import { screen } from '@testing-library/react';
import DataStudio from '../index';
import { useEntityFiltersList } from '../../../store/data-studio/hooks';
import { useUserHasPermission } from '../../../hooks/useUserHasPermission';
import { useReferenceDataList } from '../../../store/reference-data/hooks';
import { useGetDataStoreQuery } from '../../../store/datastore/api';
import { useGetDataStoreLagQuery } from '../../../store/datastore/api';
import { renderWithRouter, userEvent } from '../../../tests/helpers';
import I18nProvider from '../../../components/I18nProvider';
import { getDataStudioTestState } from '../../../store/data-studio';
// import * as dataStudioThunks from 'store/data-studio/thunks';
// import { getDataScoreTestState } from 'store/datascore';
import AppConstants from '../../../utils/AppConstants';
import { tc, tNamespaced } from '../../../utils/i18nUtil';

import DataStudioGrid from '../DataStudioGrid';

jest.mock('hooks/useUserHasPermission', () => ({
  useUserHasPermission: jest.fn(),
}));
jest.mock('store/data-studio/hooks', () => ({
  useEntityFiltersList: jest.fn(),
}));
jest.mock('store/reference-data/hooks', () => ({
  useReferenceDataList: jest.fn(),
}));
jest.mock('store/datastore/api', () => ({
  useGetDataStoreQuery: jest.fn(),
  useGetDataStoreLagQuery: jest.fn(),
}));

jest.mock('store/data-quality/slice', () => ({
  getDfiRulesForEntity: {
    pending: 'getDfiRulesForEntity/pending',
    fulfilled: 'getDfiRulesForEntity/fulfilled',
    rejected: 'getDfiRulesForEntity/rejected',
  },
}));

jest.mock('hooks/useForbiddenRedirect', () => ({
  useForbiddenRedirect: jest.fn(() => null),
}));

jest.mock('utils/PermissionsConstants', () => ({
  AllPermissions: { READ_DATA_STUDIO: 'READ_DATA_STUDIO' },
  operator: 'AND',
}));

jest.mock('hooks/useUserHasPermission', () => ({
  PermissionsComparisonOperator: { AND: 'AND' },
  useUserHasPermission: jest.fn(() => ({
    userHasPermission: jest.fn(() => true),
  })),
}));

jest.mock('../../data-studio-new/ReferenceData/ReferenceDataUpsertPanel', () => () => (
  <div data-testid="mock-reference-upsert-modal" />
));

describe('DataStudioRoot section is rendered', () => {
  beforeEach(() => {
    // Mock data for all hooks safely
    jest.mock('hooks/useSyncariEntities', () => ({
      useSyncariEntities: jest.fn(() => ({
        loading: false,
        data: [
          { id: '1', displayName: 'Customer', apiName: 'Customer' },
          { id: '2', displayName: 'Order', apiName: 'Order' },
        ],
      })),
      useEntityRecordsCount: jest.fn(() => ({
        recordCounts: { Customer: 10, Order: 5 },
        fetchStatus: {},
        fetchEntitiesCount: jest.fn(),
      })),
    }));

    (useEntityFiltersList as jest.Mock).mockReturnValue({
      data: { filters: [] },
    });

    (useReferenceDataList as jest.Mock).mockReturnValue({
      data: [],
    });

    (useUserHasPermission as jest.Mock).mockReturnValue({
      userHasPermission: jest.fn(() => true),
    });

    (useGetDataStoreQuery as jest.Mock).mockReturnValue({ data: {} });
    (useGetDataStoreLagQuery as jest.Mock).mockReturnValue({ data: [] });
  });

  it('Renders the entity dropdown to select entity or reference data', () => {
    renderWithRouter(<DataStudio />);

    const input = screen.getByTestId('data-studio-select-input');
    expect(input).toBeInTheDocument();
  });
});
