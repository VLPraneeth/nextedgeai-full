import * as Reach from '@reach/router';

import { render, screen, userEvent } from 'tests/helpers';

import { AuthoringSidebarList } from './AuthoringSidebarList';

jest.spyOn(Reach, 'useMatch').mockImplementation(jest.fn());

const testList = [
  {
    displayName: 'Card 1',
    id: '111',
    seeded: true,
  },
  {
    displayName: 'Card 2',
    id: '222',
    seeded: false,
  },
  {
    displayName: 'Card 3',
    id: '333',
    seeded: false,
  },
];

const renderComponent = () =>
  render(
    <AuthoringSidebarList
      // @ts-expect-error only using properties required for test
      list={testList}
      listType="datacard"
    />
  );

describe('AuthoringSidebarList', () => {
  beforeEach(() => {
    // need to clear due to usePersistedState hook
    window.localStorage.clear();
  });

  it('renders each list item', () => {
    renderComponent();

    for (const item of testList) {
      expect(screen.getByText(item.displayName)).toBeVisible();
    }
  });

  it('can filter to show only seeded data cards', async () => {
    renderComponent();

    await userEvent.click(screen.getByTestId('filter-collapse-button'));
    await userEvent.click(screen.getByLabelText('System data cards'));

    for (const item of testList) {
      if (item.seeded) {
        expect(screen.queryByText(item.displayName)).toBeVisible();
      } else {
        expect(screen.queryByText(item.displayName)).not.toBeInTheDocument();
      }
    }
  });

  it('can filter to show only non-seeded data cards', async () => {
    renderComponent();

    await userEvent.click(screen.getByTestId('filter-collapse-button'));
    await userEvent.click(screen.getByLabelText('User data cards'));

    for (const item of testList) {
      if (item.seeded) {
        expect(screen.queryByText(item.displayName)).not.toBeInTheDocument();
      } else {
        expect(screen.queryByText(item.displayName)).toBeVisible();
      }
    }
  });

  it('can filter by text search', async () => {
    renderComponent();

    await userEvent.type(screen.getByPlaceholderText('Search'), '2');

    for (const item of testList) {
      if (item.displayName.includes('2')) {
        expect(screen.queryByText(item.displayName)).toBeVisible();
      } else {
        expect(screen.queryByText(item.displayName)).not.toBeInTheDocument();
      }
    }
  });
});
