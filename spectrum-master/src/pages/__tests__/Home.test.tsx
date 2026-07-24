//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import Home from 'pages/Home';
import { getEmptyUserState } from 'store/user';
import { render, screen } from 'tests/helpers';
import * as AjaxUtils from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';

jest.mock('utils/AjaxUtil');
const mockedAjaxUtils = AjaxUtils as jest.Mocked<typeof AjaxUtils>;

describe('Home', () => {
  beforeAll(() => {
    mockedAjaxUtils.get.mockImplementation((url) => {
      if (url === DataUrlConstants.PROFILE) {
        return Promise.resolve(getEmptyUserState());
      }

      return Promise.resolve({});
    });
  });

  afterAll(() => {
    jest.clearAllMocks();
  });

  it('Home renders loading page when no user.id is present', async () => {
    render(<Home />);

    expect(await screen.findByTestId('loading-page')).toBeInTheDocument();
  });
});
