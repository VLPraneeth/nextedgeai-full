import { render } from 'tests/helpers';

import ProgressBar from '../ProgressBar';

describe('ProgressBar', () => {
  test.each([
    [-10, '0%'],
    [112, '100%'],
    [50, '50%'],
  ])('should limit the bounds to 0 and 100 using %s', (inputProgress, expectedWidth) => {
    const { getByTestId } = render(<ProgressBar progress={inputProgress} />);
    const element = getByTestId('progress-bar-progress');
    const style = window.getComputedStyle(element);

    expect(style.width).toBe(expectedWidth);
  });
});
