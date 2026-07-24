import { createHistory, createMemorySource } from '@reach/router';

import { renderWithRouter, screen, userEvent } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

import SyncStudioSearch from './SyncStudioSearch';

const DEFAULT_SEARCH_TIMEOUT = 2000;

describe('SyncStudioSearch', () => {
  const tn = tNamespaced('SyncStudio');
  it('displays search results when a user types something in', async () => {
    renderWithRouter(<SyncStudioSearch />);
    const searchInput = screen.getByRole('textbox', { name: 'sync-studio-search-input' });
    await userEvent.type(searchInput, 'lead');
    setTimeout(() => {
      expect(screen.getByLabelText('sync-studio-search__menu-item-Lead')).toBeVisible();
    }, DEFAULT_SEARCH_TIMEOUT);
  });

  it('displays no items found message if there are no results', async () => {
    renderWithRouter(<SyncStudioSearch />);
    const searchInput = screen.getByRole('textbox', { name: 'sync-studio-search-input' });
    await userEvent.type(searchInput, 'no-data');

    setTimeout(() => {
      expect(screen.getByText(tn('search_no_data'))).toBeVisible();
    }, DEFAULT_SEARCH_TIMEOUT);
  });

  it('displays no items found message if there is no text in the search input', async () => {
    renderWithRouter(<SyncStudioSearch />);
    const searchInput = screen.getByRole('textbox', { name: 'sync-studio-search-input' });
    await userEvent.clear(searchInput);

    setTimeout(() => {
      expect(screen.getByText(tn('search_no_data'))).toBeVisible();
    }, DEFAULT_SEARCH_TIMEOUT);
  });

  it('redirects to page when search item is clicked and no change on graph', async () => {
    const route = RouteConstants.SYNC_STUDIO;
    const history = createHistory(createMemorySource(route));

    renderWithRouter(<SyncStudioSearch />);
    const searchInput = screen.getByRole('textbox', { name: 'sync-studio-search-input' });
    await userEvent.type(searchInput, 'lead');

    setTimeout(async () => {
      const selectedItem = screen.getByLabelText('sync-studio-search__menu-item-Lead');
      await userEvent.click(selectedItem);
    }, DEFAULT_SEARCH_TIMEOUT);

    setTimeout(async () => {
      const pathName = history.location.pathname;
      expect(pathName).toContain('enitity');
    }, DEFAULT_SEARCH_TIMEOUT);
  });

  it('shows modal if change is present in pipeline', async () => {
    const route = RouteConstants.SYNC_STUDIO;
    const history = createHistory(createMemorySource(route));

    renderWithRouter(<SyncStudioSearch />, {
      testState: {
        pipeline: {
          changed: true,
        },
      },
    });
    const searchInput = screen.getByRole('textbox', { name: 'sync-studio-search-input' });
    await userEvent.type(searchInput, 'lead');

    setTimeout(async () => {
      const selectedItem = screen.getByLabelText('sync-studio-search__menu-item-Lead');
      await userEvent.click(selectedItem);
    }, DEFAULT_SEARCH_TIMEOUT);

    setTimeout(async () => {
      const pathName = history.location.pathname;
      expect(pathName).toNotContain('entity');
    }, 1000);
  });
});
