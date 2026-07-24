import { render } from 'tests/helpers';

import QuickStartInstallSchemaMatcher from '../QuickStartInstallSchemaMatcher';
import {
  installSchemaMatcherDefaultValue,
  installSchemaMatcherItems,
} from '../QuickStartInstallSchemaMatcher.fixtures';
import { SchemaMatchMap } from '../QuickStartInstallSchemaMatcher.types';

describe('QuickStartInstallSchemaMatcher', () => {
  test('should show all entity and fields when all have a default value', async () => {
    const { findByText } = render(
      <QuickStartInstallSchemaMatcher
        connectorId="connector1"
        id="id"
        navigateToStep={() => {}}
        refreshStep={() => {}}
        items={installSchemaMatcherItems}
        synapseName="Hubspot-1"
        onChange={() => {}}
        defaultValue={installSchemaMatcherDefaultValue}
      />
    );

    const matchingString = await findByText('2 entities and 4 fields are automatically mapped.');
    expect(matchingString).toBeVisible();
  });

  test('should show only the unmatched default values when some are unmatched', async () => {
    const partialDefaultValue: SchemaMatchMap = {
      '123': {
        matchValue: '456',
        fields: {
          '2': 'a2',
        },
      },
    };

    const { findByText } = render(
      <QuickStartInstallSchemaMatcher
        connectorId="connector1"
        id="id"
        navigateToStep={() => {}}
        refreshStep={() => {}}
        items={installSchemaMatcherItems}
        synapseName="Hubspot-1"
        onChange={() => {}}
        defaultValue={partialDefaultValue}
      />
    );

    const matchingString = await findByText('1 entity and 3 fields below still require mapping.');
    expect(matchingString).toBeVisible();
  });
});
