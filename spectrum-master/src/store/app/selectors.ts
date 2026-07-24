import { createSelector } from 'reselect';

import { RootState } from 'reducers/index';

export const selectErrorMessage = (state: RootState) => state.app.errorMessage;
export const selectErrorTitle = (state: RootState) => state.app.errorTitle;
export const selectKebabMenuNode = (state: RootState) => state.app.kebabMenuNode;

export const selectError = createSelector([selectErrorTitle, selectErrorMessage], (title, message) => ({
  title,
  message,
}));

const selectGlobalErrorMessage = (state: RootState) => state.app.globalErrorMessage;
const selectGlobalErrorStack = (state: RootState) => state.app.globalErrorStack;

export const selectGlobalError = createSelector(
  [selectGlobalErrorMessage, selectGlobalErrorStack],
  (message, stack) => ({ message, stack })
);

export const selectLastActions = (state: RootState) => {
  const lastActions = state.app.lastActions.slice();
  lastActions.reverse();

  return lastActions;
};
