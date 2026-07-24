import { dashboards as mockDashboards } from 'mocks/fixtures/insights';

import * as hooks from 'hooks/useSyncariEntities';
import * as InstanceFeature from 'store/instance-feature/api';
import { renderHook, waitFor } from 'tests/helpers';

import { ExampleDashboards } from './ExampleDashboards';
import { useDashboards } from './useDashboards';

jest.mock('store/insights-studio/api', () => {
  return {
    useLazyGetDashboardsQuery: () => [jest.fn(), { data: mockDashboards, isLoading: false }],
  };
});

jest
  .spyOn(hooks, 'useEntityRecordsCount')
  // @ts-expect-error - only using values needed to test
  .mockImplementation(() => ({ hasSyncedData: true, fetchStatus: { entity: 'success' } }));

const useGetFeatureStatusQuery = jest
  .spyOn(InstanceFeature, 'useGetFeatureStatusQuery')
  // @ts-expect-error - only using values needed to test
  .mockImplementation(() => ({ data: { status: 'inactive' }, isLoading: false }));

jest.spyOn(hooks, 'default').mockImplementation(() => ({ data: [], loading: false }));

describe('useDashboards hook', () => {
  it('returns only Example dashboards when insights is not enabled', () => {
    const dashboardList = renderHook(() => useDashboards());

    expect(dashboardList).toHaveLength(ExampleDashboards.length);

    for (const dash of dashboardList) {
      expect(dash.isExample).toBe(true);
    }
  });

  it('returns only Example dashboards when system has no synced data', async () => {
    const dashboardList = renderHook(() => useDashboards());

    expect(dashboardList).toHaveLength(ExampleDashboards.length);

    for (const dash of dashboardList) {
      expect(dash.isExample).toBe(true);
    }
  });

  it('adds api dashboards to beginning of list when insights is enabled and system has synced data', async () => {
    // @ts-expect-error - only using values needed to test
    useGetFeatureStatusQuery.mockReturnValue({ data: { status: 'active' }, isLoading: false });

    const dashboardList = renderHook(() => useDashboards());

    await waitFor(() => expect(dashboardList).toHaveLength(ExampleDashboards.length + mockDashboards.length));

    dashboardList.forEach((dash, i) => {
      if (i < 3) {
        // first 3 should be the "live" dashboards from the api
        expect(dash.isExample).toBe(undefined);
      } else {
        // rest are examples
        expect(dash.isExample).toBe(true);
      }
    });
  });
});
