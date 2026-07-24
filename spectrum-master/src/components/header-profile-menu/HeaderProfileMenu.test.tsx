import { render, screen, userEvent } from 'tests/helpers';

import { HeaderProfileMenu } from './HeaderProfileMenu';

const testUser = { firstName: 'Eetsa', lastName: 'Testman', email: 'eetsa@testman.com' };
const testSyncariUser = { firstName: 'Guy', lastName: 'Hasajob', email: 'guy@syncari.com' };

const renderComponent = (overrides?: any) =>
  render(<HeaderProfileMenu />, {
    testState: { user: { ...testUser, ...overrides } },
  });

describe('HeaderProfileMenu', () => {
  it("renders menu trigger with user's first name", () => {
    renderComponent();
    expect(screen.getByText(testUser.firstName)).toBeVisible();
  });

  it("renders menu trigger with user's email if first name is missing", () => {
    renderComponent({ firstName: '' });
    expect(screen.queryByText(testUser.firstName)).not.toBeInTheDocument();
    expect(screen.queryByText(testUser.email)).toBeVisible();
  });

  it('shows correct menu items in non-prod, non-trial, non-syncari user state', async (done) => {
    renderComponent();

    await userEvent.click(screen.getByText(testUser.firstName));

    const expectedMenuItems = ['Profile', 'About', 'Logout', 'API Documentation', 'Crash Now: Test Phone Home'];

    for (let item of expectedMenuItems) {
      expect(screen.getByText(item)).toBeVisible();
    }

    // Check divider is present
    // Timeout is needed because RTL doesn't provide any way of querying by class name
    setTimeout(() => {
      // eslint-disable-next-line
      expect(document.querySelectorAll('.ant-dropdown-menu-item-divider')).toHaveLength(1);
      done();
    }, 100);
  });

  it('shows correct menu items in non-prod, non-trial, syncari user state', async (done) => {
    renderComponent(testSyncariUser);

    await userEvent.click(screen.getByText(testSyncariUser.firstName));

    const expectedMenuItems = ['Profile', 'About', 'Logout', 'API Documentation', 'Crash Now: Test Phone Home'];

    for (let item of expectedMenuItems) {
      expect(screen.getByText(item)).toBeVisible();
    }

    // Check divider is present
    // Timeout is needed because RTL doesn't provide any way of querying by class name
    setTimeout(() => {
      // eslint-disable-next-line
      expect(document.querySelectorAll('.ant-dropdown-menu-item-divider')).toHaveLength(1);
      done();
    }, 100);
  });

  describe('in production environment', () => {
    beforeEach(() => {
      // simulate prod environment for tests in this describe block
      process.env.NODE_ENV = 'production';
    });
    afterEach(() => {
      // reset env to keep tests isolated
      process.env.NODE_ENV = 'test';
    });
    it('Hides correct menu items in production', async () => {
      renderComponent();

      await userEvent.click(screen.getByText(testUser.firstName));

      const nonProdMenuItems = ['API Documentation', 'Crash Now: Test Phone Home'];

      for (let item of nonProdMenuItems) {
        expect(screen.queryByText(item)).not.toBeInTheDocument();
      }
    });
  });
});
