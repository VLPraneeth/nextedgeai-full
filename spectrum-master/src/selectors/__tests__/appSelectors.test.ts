// @ts-nocheck
import { selectError } from '../appSelectors';

test('selectError returns the error data as object', () => {
  const title = 'TypeError';
  const message = 'n is undefined.';

  const state = {
    app: {
      errorTitle: title,
      errorMessage: message,
    },
  };

  expect(selectError(state)).toStrictEqual({
    title,
    message,
  });
});
