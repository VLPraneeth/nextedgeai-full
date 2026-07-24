import { renderHook } from 'tests/helpers';

import { getDefaultConnectorState, getEmptyConnector } from '../fixtures';
import { useSynapseRefreshingStatus } from '../hooks';

describe('useSynapseRefreshingStatus', () => {
  const connectorId = 'connectorId';

  test('should return false/null if no connector found', () => {
    const result = renderHook(() => useSynapseRefreshingStatus(connectorId), {
      testState: {
        connector: getDefaultConnectorState({
          connectors: [],
        }),
      },
    });

    expect(result.isRefreshing).toBe(false);
  });

  test('should return unableToUpdate true if synapse is ACTIVATING and schemaRefreshStatus is NEW', () => {
    const connectorId = 'connectorId';

    const result = renderHook(() => useSynapseRefreshingStatus(connectorId), {
      testState: {
        connector: getDefaultConnectorState({
          connectors: [
            getEmptyConnector({
              id: connectorId,
              status: 'ACTIVATING',
              schemaRefreshStatus: 'NEW',
            }),
          ],
        }),
      },
    });

    expect(result.isRefreshing).toBe(false);
    expect(result.unableToUpdate).toBe(true);
  });

  test('should return unableToUpdate false if synapse is ACTIVE and schemaRefreshStatus is NEW', () => {
    const connectorId = 'connectorId';

    const result = renderHook(() => useSynapseRefreshingStatus(connectorId), {
      testState: {
        connector: getDefaultConnectorState({
          connectors: [
            getEmptyConnector({
              id: connectorId,
              status: 'ACTIVE',
              schemaRefreshStatus: 'NEW',
            }),
          ],
        }),
      },
    });

    expect(result.isRefreshing).toBe(false);
    expect(result.unableToUpdate).toBe(false);
  });
});
