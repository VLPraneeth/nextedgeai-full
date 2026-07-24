//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render } from '@testing-library/react';
import { Provider } from 'react-redux';

import PasswordResetSuccess from 'pages/authentication/PasswordResetSuccess';
import configureAppStore from 'store/configureStore';

const store = configureAppStore(); // Include if tests re-added

it('Password Reset Success Page renders correctly', () => {
  const { asFragment } = render(
    <Provider store={store}>
      <PasswordResetSuccess />
    </Provider>
  );
  expect(asFragment()).toMatchSnapshot();
});
