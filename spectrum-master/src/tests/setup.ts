import '@testing-library/jest-dom';

import { server, startServer } from 'mocks/server';

import { init } from 'utils/i18nUtil';

// Mock for all tests
// node.focus() call in setTimeout is always null due to DOM teardown after tests
jest.mock('hooks/useFocusRef.ts', () => ({
  useFocusRef: () => ({ element: { current: { focus: jest.fn() } }, refCallback: jest.fn() }),
}));

// Establish API mocking before all tests.
beforeAll(() => {
  init();
  startServer();
});
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
