import Can, { PermissionErrorModes } from 'components/Can';
import { RoleName } from 'store/user/types';
import { render, screen } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

const testState = {
  testState: {
    user: {
      currentInstanceNextEdgeId: 'asdf',
      userRoles: { asdf: ['allowed' as RoleName, 'not-allowed' as RoleName] },
      privileges: [AllPermissions.ACTION_WRITE],
    },
  },
};

beforeAll(() => {
  localStorage.setItem('ACCESS_CONTROL', 'true');
});

afterAll(() => {
  localStorage.removeItem('ACCESS_CONTROL');
});

describe('<Can />', () => {
  it("displays children when user's capabilities include any of the provided capabilities", () => {
    render(
      <Can capability={['allowed']}>
        <div>Child Component</div>
      </Can>,
      testState
    );

    expect(screen.queryByText('Child Component')).toBeVisible();
  });

  it("does not display children when user's capabilities do not include any of the provided capabilities", () => {
    render(
      <Can capability={['something else']}>
        <div>Child Component</div>
      </Can>,
      testState
    );

    expect(screen.queryByText('Child Component')).not.toBeInTheDocument();
  });

  it("does not display children when user's capabilities include any of the provided restrictions", () => {
    render(
      <Can capability={['allowed']} restrict={['not-allowed']}>
        <div>Child Component</div>
      </Can>,
      testState
    );

    expect(screen.queryByText('Child Component')).not.toBeInTheDocument();
  });

  it('handles multiple and nested children', () => {
    render(
      <Can capability={['allowed']}>
        <div>Child 1</div>
        <div>Child 2</div>
        <div>
          <span>subchild</span>
        </div>
      </Can>,
      testState
    );

    expect(screen.queryByText('Child 1')).toBeVisible();
    expect(screen.queryByText('Child 2')).toBeVisible();
    expect(screen.queryByText('subchild')).toBeVisible();
  });

  it('passes other props to direct children', () => {
    render(
      <Can capability={['allowed']} aria-label="test prop">
        <div>Child 1</div>
        <div>Child 2</div>
        <div>
          <span>subchild</span>
        </div>
      </Can>,
      testState
    );

    expect(screen.queryByText('Child 1')).toHaveAttribute('aria-label');
    expect(screen.queryByText('Child 2')).toHaveAttribute('aria-label');
    expect(screen.queryByText('subchild')).not.toHaveAttribute('aria-label');
  });

  // permissions

  it('renders the component with disabled prop if mode = DisableChildComponent is passed', () => {
    render(
      <Can permission={[AllPermissions.LIST_USER]} errorMode={PermissionErrorModes.DisableChildComponent}>
        <div>Child 1</div>
        <div>
          <span>subchild</span>
        </div>
      </Can>,
      testState
    );

    expect(screen.queryByText('Child 1')).toHaveAttribute('disabled');
    expect(screen.queryByText('subchild')).not.toHaveAttribute('disabled');
  });

  it('returns text if customReplacedText prop and mode is set to ReplaceWithText', () => {
    render(
      <Can
        permission={[AllPermissions.ADD_INSTANCE]}
        errorMode={PermissionErrorModes.ReplaceWithText}
        customReplacedText="test">
        <div>Child 1</div>
        <div>
          <span>subchild</span>
        </div>
      </Can>,
      testState
    );

    expect(screen.getByText('test')).toBeInTheDocument();
  });
});
