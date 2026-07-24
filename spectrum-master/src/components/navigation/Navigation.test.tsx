//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { createHistory, createMemorySource, LocationProvider } from '@reach/router';
import { fireEvent } from '@testing-library/react';

import { render, screen } from 'tests/helpers';
import RouteConstants from 'utils/RouteConstants';

import Navigation, { COLLAPSED_WIDTH, EXPANDED_WIDTH, PERSISTED_NAVIGATION_COLLAPSED } from './Navigation';

describe('SideNavigationMenu', () => {
  it('renders SideMenuItems', async () => {
    render(
      <LocationProvider>
        <Navigation />
      </LocationProvider>
    );

    const menu = screen.getByTestId('main-nav-items-container');
    expect(menu).toBeVisible();
  });

  test('always renders the Synapse Studio menu item', async () => {
    render(
      <LocationProvider>
        <Navigation />
      </LocationProvider>
    );

    expect(await screen.findByText('Synapse Studio')).toBeInTheDocument();
  });

  it('does not render dashboard when disabled', async () => {
    render(
      <LocationProvider>
        <Navigation />
      </LocationProvider>
    );

    expect(screen.queryByText('Dashboard')).toBeNull();
  });

  it('collapse/expands when menu toggle button is clicked', () => {
    // Menu starts off expanded
    render(
      <LocationProvider>
        <Navigation />
      </LocationProvider>
    );

    const drawer = screen.getByTestId('main-nav-menu-container') as Element;
    const drawerToggleButton = screen.getByTestId('main-nav-expand') as Element;

    // First click is to collapse
    fireEvent.click(drawerToggleButton);
    expect(drawer).toHaveStyle({
      width: `${COLLAPSED_WIDTH}px`,
    });

    // Second click to expand back
    fireEvent.click(drawerToggleButton);
    expect(drawer).toHaveStyle({
      width: `${EXPANDED_WIDTH}px`,
    });
  });

  it('persists collapse and expand state', () => {
    // Menu starts off collapsed
    localStorage.setItem(PERSISTED_NAVIGATION_COLLAPSED, 'false');
    render(
      <LocationProvider>
        <Navigation />
      </LocationProvider>
    );

    const drawer = screen.getByTestId('main-nav-menu-container') as Element;

    expect(drawer).toHaveStyle({
      width: `${EXPANDED_WIDTH}px`,
    });
    localStorage.removeItem(PERSISTED_NAVIGATION_COLLAPSED);
  });
  it('does not change the current route when using click with metaKey', () => {
    const route = RouteConstants.HOME;
    const history = createHistory(createMemorySource(route));
    render(
      <LocationProvider history={history}>
        <Navigation />
      </LocationProvider>
    );

    const schemaStudioButton = screen.getByTestId('nav-menu-item-Schema Studio') as Element;

    fireEvent.click(schemaStudioButton, { metaKey: true });

    const currentLocation = history.location.pathname;

    expect(currentLocation).toBe(RouteConstants.HOME);
  });

  it('does not navigate away when pipeline change is active', () => {
    const route = '/sync-studio/entity/test/pipeline';
    const history = createHistory(createMemorySource(route));
    render(
      <LocationProvider history={history}>
        <Navigation />
      </LocationProvider>,
      {
        testState: {
          pipeline: {
            changed: true,
          },
        },
      }
    );

    const schemaStudioButton = screen.getByTestId('nav-menu-item-Schema Studio') as Element;
    fireEvent.click(schemaStudioButton);

    const currentLocation = history.location.pathname;

    expect(currentLocation).toBe(route);
  });
});
