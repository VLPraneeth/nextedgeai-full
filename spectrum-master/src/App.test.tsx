import React from 'react';
import ReactDOM from 'react-dom';

import App from './App';

it('renders without crashing', () => {
  const div = document.createElement('div');
  ReactDOM.render(<App />, div);
  ReactDOM.unmountComponentAtNode(div);
});

describe('Global environment test', () => {
  it('Timezone should always be UTC', () => {
    expect(new Date().getTimezoneOffset()).toBe(0);
  });
});
