//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';

import MainApp from 'pages/MainApp';
import configureAppStore from 'store/configureStore';

const store = configureAppStore();

it('MainApp renders correctly', () => {
  const { asFragment } = render(
    <Provider store={store}>
      <MainApp />
    </Provider>
  );
  expect(asFragment()).toMatchSnapshot();
});
