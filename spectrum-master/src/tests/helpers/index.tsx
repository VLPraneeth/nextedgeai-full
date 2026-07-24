//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
//
import { createHistory, createMemorySource, History, LocationProvider } from '@reach/router';
import {
  fireEvent,
  MatcherFunction,
  queryByAttribute,
  RenderOptions,
  render as rtlRender,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React, { ReactNode } from 'react';
import { Provider, ProviderProps } from 'react-redux';
import { DeepPartial, Store } from 'redux';

import { renderHook as testingLibraryRenderHook } from '@testing-library/react';
import * as AjaxUtils from 'utils/AjaxUtil';

import { RootState } from '../../reducers';
import { createAppTestStore } from './StoreHelper';

export interface TestRenderOptions extends RenderOptions {
  /**
   * custom Redux store to use instead of our mock redux store
   * */
  store?: Store;

  /**
   * full initialState replacement. if this is provided, we won't use
   * the initialState provided by each slice of the reducer
   */
  initialState?: RootState;

  /**
   * custom state for your test that will be deep merged onto the
   * initialState. This is almost always what you want to use so
   * that the state will be "hydrated" as expected for your test run.
   */
  testState?: DeepPartial<RootState>;
}

/**
 * render wrapping function that will provide Redux context (and any other necessary contexts) to each use
 *
 * Give it the UI you want to test, and optionally provide RenderConfig to customize the store/state
 * for the test run.
 */

interface RenderReturnType extends ReturnType<typeof rtlRender> {
  __reduxStore: Store;
  getByTextWithMarkup: ReturnType<typeof withMarkup>;
}

export function render(
  ui: React.ReactElement,
  { initialState, testState, store, ...renderOptions }: TestRenderOptions = {}
): RenderReturnType {
  const testStore = store || createAppTestStore(testState, initialState);

  const Wrapper = ({ children }: { children?: ReactNode }) =>
    React.createElement(Provider, { store: testStore }, children);

  const { getByText, ...rest } = rtlRender(ui, { wrapper: Wrapper, ...renderOptions });
  return {
    __reduxStore: testStore,
    getByText,
    getByTextWithMarkup: withMarkup(getByText),
    ...rest,
  };
}

interface RenderConfigWithRouterReturnType extends RenderReturnType {
  rerenderWithRouter: (child: React.ReactElement) => void;
  history: History;
}
interface RenderConfigWithRouter extends TestRenderOptions {
  /**
   * Route to render
   */
  route?: string;

  /**
   * History that will be created
   */
  history?: History;
}

export function renderWithRouter(
  ui: React.ReactElement,
  { route = '/', history = createHistory(createMemorySource(route)), ...rest }: RenderConfigWithRouter = {}
): RenderConfigWithRouterReturnType {
  const renderResult = render(<LocationProvider history={history}>{ui}</LocationProvider>, rest);
  return {
    ...renderResult,
    rerenderWithRouter: (child) =>
      renderResult.rerender(<LocationProvider history={history}>{child}</LocationProvider>),
    // adding `history` to the returned utilities to allow us
    // to reference it in our tests (just try to avoid using
    // this to test implementation details).
    history,
  };
}

export function renderHook<HookProps, HookResult>(
  hook: (props: HookProps) => HookResult,
  { initialState, testState, store }: TestRenderOptions = {}
) {
  const testStore = store || createAppTestStore(testState, initialState);

  return renderHookWithProvider<HookProps, HookResult, React.PropsWithChildren<ProviderProps>>(hook, Provider, {
    store: testStore,
  });
}

/** Some hooks need a specific provider wrapper */
export function renderHookWithProvider<
  HookProps,
  HookResult,
  ContextProviderProps extends React.PropsWithChildren<unknown>,
  ContextProviderPropsWithoutChildren = Exclude<ContextProviderProps, 'children'>
>(
  hook: (props: HookProps) => HookResult,
  ContextProvider: React.ComponentType<ContextProviderProps>,
  providerProps: ContextProviderPropsWithoutChildren
) {
  const { result } = testingLibraryRenderHook(hook, {
    // @ts-ignore: provider props is complaining that it might not be instantiated with the proper children typing, I think
    wrapper: ({ children }) => React.createElement(ContextProvider, providerProps, children),
  });

  return result.current;
}

const makeElementNotFoundError = (text: string) => `Unable to find an element with the text: ${text}.`;

/**
 * A custom function to query by any data-* attribute on an HTML element.
 * This function should be considered a last resort, use only when it is not possible to
 * accessan element by any of the Testing Library built-in methods (such as elements
 * from Ant library that cannot be given test ids or other modifiers)
 *
 * See UserList.test.js for example usage
 *
 * @param container HTMLElement
 * @param attributeName name of data attribute to check. Can optionally omit the `data-` prefix, making `row` and `data-row` equivalent
 * @param attributeValue value of the data attribute to match
 * @returns HTMLElement or null
 */
const queryByDataAttribute = (container: HTMLElement, attributeName: string, attributeValue: string) => {
  const dataAttribute = attributeName.startsWith('data-') ? attributeName : `data-${attributeName}`;
  return queryByAttribute(dataAttribute, container, attributeValue);
};

// Query text with markup
type Query = (f: MatcherFunction) => HTMLElement;
const withMarkup = (query: Query) => (text: string): HTMLElement =>
  query((_content: string, node: Element | null) => {
    const hasText = (node: Element | null) => node?.textContent === text;
    const childrenDontHaveText = !!node && Array.from(node.children).every((child) => !hasText(child as HTMLElement));
    return hasText(node) && childrenDontHaveText;
  });

// re-export StoreHelper and react-testing-library stuff
export {
  createAppTestStore,
  fireEvent,
  makeElementNotFoundError,
  queryByDataAttribute,
  screen,
  userEvent,
  waitFor,
  within,
};

/**
 * Provides properly typed mocked ajax utils.
 * NOTE: Still requires `jest.mock('utils/AjaxUtil');` in the file where this is invoked
 */
export const mockedAjaxUtils = () => AjaxUtils as jest.Mocked<typeof AjaxUtils>;

export const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export const hideBenignTestWarnings = () => {
  const originalWarn = console.warn.bind(console.warn);
  beforeAll(() => {
    console.warn = (msg) => !msg.toString().includes('componentWillReceiveProps') && originalWarn(msg);
  });
  afterAll(() => {
    console.warn = originalWarn;
  });
};
