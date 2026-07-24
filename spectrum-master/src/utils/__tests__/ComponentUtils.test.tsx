import cx from 'classnames';

import { render, screen } from 'tests/helpers';

import { validateMutuallyExclusiveProps } from '../ComponentUtils';

type TestButtonProps = {
  children: string;
  small?: boolean;
  medium?: boolean;
  large?: boolean;
  primary?: boolean;
  destructive?: boolean;
};

const OurTestButton = ({ children, small, medium, large, primary, destructive }: TestButtonProps) => {
  validateMutuallyExclusiveProps<TestButtonProps>({ small, medium, large }, { primary, destructive });

  return (
    <button
      type="button"
      className={cx('test-button', {
        small,
        medium,
        large,
        primary,
        destructive,
      })}>
      {children}
    </button>
  );
};

describe('Test validateMutuallyExclusiveProps', () => {
  let consoleSpy: ReturnType<typeof jest.spyOn>;

  beforeEach(() => {
    consoleSpy = jest.spyOn(global.console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    consoleSpy.mockRestore();
  });

  test('no conflicting props', async () => {
    render(<OurTestButton>Save</OurTestButton>);

    expect(await screen.findByText('Save')).toBeInTheDocument();
    expect(consoleSpy).not.toHaveBeenCalled();
  });

  test('conflicting props from 1 propset', async () => {
    render(
      <OurTestButton small medium>
        Save
      </OurTestButton>
    );

    expect(await screen.findByText('Save')).toBeInTheDocument();
    expect(consoleSpy).toHaveBeenCalledWith('May not provide more than 1 prop included in: {small, medium, large}.');
  });

  test('conflicting props from multiple propsets', async () => {
    render(
      <OurTestButton small medium primary destructive>
        Save
      </OurTestButton>
    );

    expect(await screen.findByText('Save')).toBeInTheDocument();
    expect(consoleSpy).toHaveBeenCalledWith('May not provide more than 1 prop included in: {small, medium, large}.');
    expect(consoleSpy).toHaveBeenCalledWith('May not provide more than 1 prop included in: {primary, destructive}.');
  });
});
