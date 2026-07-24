// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render } from '@testing-library/react';
import { Provider } from 'react-redux';

import AuthenticationWrapper from 'pages/authentication/AuthenticationWrapper';
import configureAppStore from 'store/configureStore';

const store = configureAppStore();

describe('AuthenticationWrapper', () => {
  it('renders the authentication wrapper correctly', () => {
    // Render the component with React Testing Library
    const { container } = render(
      <Provider store={store}>
        <AuthenticationWrapper />
      </Provider>
    );

    // Check if the rendered output matches the snapshot
    expect(container).toMatchSnapshot();
  });
});
