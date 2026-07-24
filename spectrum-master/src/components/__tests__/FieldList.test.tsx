//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import FieldList from 'components/FieldList';
import { fireEvent, renderWithRouter } from 'tests/helpers';

const testState = {
  validation: {
    errors: [],
    warnings: [],
  },
  pipelineError: {},
};

test('Field component renders correctly with items', () => {
  const items = [
    {
      apiName: 'BillingCity',
      dataType: 'string',
      description: 'string',
      displayName: 'Billing City',
      id: '5eb1ad8dc559ab3af746365b',
      idField: false,
      link: '/sync-studio/entity/5eb1ad8dc559ab3af746364b/field/5eb1ad8dc559ab3af746365b/pipeline/new',
      status: 'ACTIVE',
      tags: [],
      title: 'Billing City',
      values: [],
      isMapped: true,
      hasChanges: false,
    },
    {
      apiName: 'AboutUs',
      dataType: 'string',
      description: 'string',
      displayName: 'About Us',
      id: '5eb1ad8dc559ab3af7463654',
      idField: false,
      link: '/sync-studio/entity/5eb1ad8dc559ab3af746364b/field/5eb1ad8dc559ab3af7463654/pipeline/new',
      status: 'ACTIVE',
      tags: [],
      title: 'About Us',
      values: [],
      isMapped: true,
      hasChanges: false,
    },
  ];

  // @ts-expect-error: FieldDataType mismatch as string in fixture
  const { getByText } = renderWithRouter(<FieldList items={items} onFieldClick={() => {}} />, { testState });

  items.forEach((item) => {
    // make sure we're rendering each row
    expect(getByText(item.title)).toBeDefined();
  });
});

test("Field Item shows Draft if there's a draft", async () => {
  const items = [
    {
      apiName: 'BillingCity',
      dataType: 'string',
      description: 'string',
      displayName: 'Billing City',
      id: '5eb1ad8dc559ab3af746365b',
      idField: false,
      link: '/sync-studio/entity/5eb1ad8dc559ab3af746364b/field/5eb1ad8dc559ab3af746365b/pipeline/new',
      status: 'ACTIVE',
      tags: [],
      title: 'Billing City',
      values: [],
      hasChanges: true,
      isMapped: true,
    },
  ];

  // @ts-expect-error: FieldDataType mismatch as string in fixture
  const { findByRole, getByRole, getByText } = renderWithRouter(<FieldList items={items} onFieldClick={() => {}} />, {
    testState,
  });

  for await (const item of items) {
    expect(getByText(item.title)).toBeDefined();
    expect(getByText('Draft')).toBeDefined();

    const dataTypeIcon = getByRole('img', { name: item.description });
    expect(dataTypeIcon).toBeDefined();

    await fireEvent.mouseOver(dataTypeIcon);

    const tooltip = await findByRole('tooltip');
    expect(tooltip).toBeDefined();

    // there should be the string for decription now that the
    // tooltip is showing
    expect(getByText(item.description)).toBeDefined();

    // open the kebab menu, expect "Edit Draft" since this item has draft info
    const kebabMenu = await findByRole('button', { name: 'Open Field Menu' });
    await fireEvent.click(kebabMenu);

    await findByRole('menu');
    expect(getByText('Edit Draft')).toBeDefined();
  }
});
