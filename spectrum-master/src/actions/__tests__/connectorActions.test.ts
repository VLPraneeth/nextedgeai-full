//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { mockedAjaxUtils } from 'tests/helpers';
import { createAppTestStore } from 'tests/helpers/StoreHelper';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/StringUtil';

import { ActionTypes, oauthenticate } from '../connectorActions';

jest.mock('utils/AjaxUtil');
jest.useFakeTimers();

const AjaxUtils = mockedAjaxUtils();
const mockedGet = AjaxUtils.get;

describe('Connector Actions', () => {
  let windowSpy: jest.SpyInstance, consoleSpy: jest.SpyInstance;

  beforeEach(() => {
    windowSpy = jest.spyOn(window, 'open');
    consoleSpy = jest.spyOn(console, 'info');
  });

  afterEach(() => {
    windowSpy.mockRestore();
    consoleSpy.mockRestore();
    jest.clearAllMocks();
  });

  test('Success open OAuth window', () => {
    const connectorId = 'connectorTestId';

    windowSpy.mockImplementation(() => ({
      location: {
        href: 'https://oauth.syncari.com',
      },
    }));

    mockedGet.mockImplementation((url, _data) => {
      expect(url).toBe(replaceToken(DataUrlConstants.OAUTH_INITIATE, { connectorId }));
      return Promise.resolve({ data: { location: 'https://oauth.syncari.com' } });
    });

    const expectedActions = [{ type: ActionTypes.OAUTHENTICATE_PENDING }];

    const store = createAppTestStore();
    return store.dispatch(oauthenticate({ connectorId })).then(() => {
      expect(windowSpy).toHaveBeenCalledWith(
        'https://oauth.syncari.com',
        '_target',
        'toolbar=yes,scrollbars=yes,resizable=yes,top=150,left=500,width=650,height=750'
      );
      jest.runOnlyPendingTimers();
      expect(setInterval).toHaveBeenCalledTimes(1);
      expect(store.getActions()).toEqual(expectedActions);
    });
  });

  test('Fail open oAuth window', () => {
    const connectorId = 'connectorTestId';

    windowSpy.mockImplementation(() => void 0);
    consoleSpy.mockImplementation(() => void 0);

    // const windowOpenSpy = jest.spyOn(window, 'open');
    mockedGet.mockImplementation((url, _data) => {
      expect(url).toBe(replaceToken(DataUrlConstants.OAUTH_INITIATE, { connectorId }));
      return Promise.resolve({ data: { location: 'https://oauth.syncari.com' } });
    });

    const expectedActions = [{ type: ActionTypes.OAUTHENTICATE_PENDING }];

    const store = createAppTestStore();
    return store.dispatch(oauthenticate({ connectorId })).then(() => {
      expect(windowSpy).toHaveBeenCalledWith(
        'https://oauth.syncari.com',
        '_target',
        'toolbar=yes,scrollbars=yes,resizable=yes,top=150,left=500,width=650,height=750'
      );
      jest.runOnlyPendingTimers();
      expect(setInterval).toHaveBeenCalledTimes(1);
      expect(clearInterval).toHaveBeenCalledTimes(1);
      expect(consoleSpy).toHaveBeenLastCalledWith('oAuthWindow not found, closing monitor…');
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
