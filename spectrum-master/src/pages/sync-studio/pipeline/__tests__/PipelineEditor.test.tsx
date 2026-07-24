//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createHistory, createMemorySource, LocationProvider, Router } from '@reach/router';

import * as EntityPipelineActions from 'actions/entityPipelineActions';
import * as PipelineActions from 'store/pipeline/actions';
import * as UserThunks from 'store/user/thunks';
import {
  hideBenignTestWarnings,
  mockedAjaxUtils,
  render,
  renderWithRouter,
  screen,
  sleep,
  userEvent,
} from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import { t, tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import PipelineEditor from '../PipelineEditor';
import { getMinimalEntityPipelineState } from '../PipelineEditor.fixtures';
import PipelineEditorMoreActions from '../PipelineEditorMoreActions';

const EpActionTypes = EntityPipelineActions.ActionTypes;

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();

hideBenignTestWarnings();

const Home = () => <main />;

describe('Entity Pipeline editor', () => {
  test('node config modal is closed when we navigate', async (done) => {
    ajaxMock.get.mockImplementation((url) => {
      return Promise.resolve({ data: {} });
    });
    const route = '/sync-studio/entity/5e0d22e77df51d38b296628e/pipeline/DRAFT';
    const history = createHistory(createMemorySource(route));
    const testState = getMinimalEntityPipelineState();
    // show modal by default, then we'll see if it closes when the route changes
    const { __reduxStore: store } = render(
      <LocationProvider history={history}>
        <Router>
          <Home path="/" />
          <PipelineEditor
            key="entity-pipeline-editor"
            path="/sync-studio/entity/:entityId/pipeline/:graphVersion"
            renderGraph={false}
          />
        </Router>
      </LocationProvider>,
      {
        testState,
      }
    );

    // navigate away from this page
    await history.navigate('/');

    // Allow an extra render to let the cleanup function get called
    await sleep(500);

    // expect that we will have an action to close the modal
    const actions = store.getActions();
    expect(actions.some((action) => action.type === EpActionTypes.SHOW_NODE_CONFIG && action.visible === false)).toBe(
      true
    );

    done();
  });

  test('Pipeline test be enabled under normal pipeline state', async () => {
    ajaxMock.get.mockImplementation((url) => {
      return Promise.resolve({ data: {} });
    });
    const testState = getMinimalEntityPipelineState();

    renderWithRouter(
      <PipelineEditor
        key="entity-pipeline-editor"
        rightGroup={
          <PipelineEditorMoreActions
            isEntityPipeline
            graphIsReadOnly
            currentInstanceState={{}}
            isApproveWithDraftGraph={false}
            isDraftOnlyGraph={false}
            navigateToEntityPipeline={() => {}}
          />
        }
      />,
      {
        testState,
      }
    );

    await userEvent.click(await screen.findByText('More Actions'));
    expect(await (await screen.findByRole('menuitem', { name: 'Run Live Test' })).parentElement).not.toHaveAttribute(
      'aria-disabled',
      'true'
    );
  });

  test('Pipeline test should be disabled when a Pipeline test is in progress', async () => {
    ajaxMock.get.mockImplementation((url) => {
      return Promise.resolve({ data: {} });
    });
    const testState = getMinimalEntityPipelineState({
      entityPipeline: {
        entityPipeline: {
          readOnly: true,
          readOnlyReason: 'Pipeline test is in progress',
        },
      },
    });

    const { getByTextWithMarkup } = renderWithRouter(
      <PipelineEditor
        rightGroup={
          <PipelineEditorMoreActions
            isEntityPipeline
            graphIsReadOnly
            currentInstanceState={{}}
            isApproveWithDraftGraph={false}
            isDraftOnlyGraph={false}
            navigateToEntityPipeline={() => {}}
          />
        }
      />,
      {
        testState: {
          ...testState,
          user: {
            privileges: [AllPermissions.READ_STUDIO, AllPermissions.WRITE_STUDIO, AllPermissions.READ_CONNECTOR],
          },
        },
      }
    );

    // Run Live Test
    expect(await screen.findByText('Pipeline test is in progress')).toBeInTheDocument();
    await userEvent.click(await screen.findByText('More Actions'));

    const runLiveTestMenuItem = await screen.findByRole('menuitem', { name: 'Run Live Test' });
    expect(runLiveTestMenuItem).toHaveAttribute('aria-disabled', 'true');

    await userEvent.hover(runLiveTestMenuItem.lastChild);
    // Wait for the tooltip to be rendered
    await sleep(500);
    expect(getByTextWithMarkup(t('PipelineEditor.test_in_progress'))).toBeInTheDocument();
  });

  test('should reinitialize on entityId change', async () => {
    ajaxMock.get.mockImplementation((url) => {
      return Promise.resolve({ data: {} });
    });
    const getUserPreferenceSpy = jest.spyOn(UserThunks, 'getUserPreference');
    const clearErrorSpy = jest.spyOn(EntityPipelineActions, 'clearError');
    const clearEntityPipelineSpy = jest.spyOn(EntityPipelineActions, 'clearEntityPipeline');
    const getEntityPipelineSpy = jest.spyOn(EntityPipelineActions, 'getEntityPipeline');
    const getConnectorEntitiesSpy = jest.spyOn(EntityPipelineActions, 'getConnectorEntities');
    const setPipelineContextSpy = jest.spyOn(EntityPipelineActions, 'setPipelineContext');
    const setSelectedGraphNodeSpy = jest.spyOn(EntityPipelineActions, 'setSelectedGraphNode');
    const setDisplayedGraphSpy = jest.spyOn(PipelineActions, 'setDisplayedGraph');
    const setPipelineIdSpy = jest.spyOn(PipelineActions, 'setPipelineId');

    const testState = getMinimalEntityPipelineState();

    const { rerenderWithRouter } = renderWithRouter(<PipelineEditor entityId="1234" renderGraph={false} />, {
      testState,
    });

    expect(clearErrorSpy).toHaveBeenCalled();
    expect(clearEntityPipelineSpy).toHaveBeenCalled();
    expect(getEntityPipelineSpy).toHaveBeenCalledWith('1234', 'APPROVED');
    expect(setDisplayedGraphSpy).toHaveBeenCalled();
    expect(getConnectorEntitiesSpy).toHaveBeenCalledWith('1234');
    expect(setPipelineContextSpy).toHaveBeenCalledWith(AppConstants.PIPELINE_CONTEXT.ENTITY);
    expect(setPipelineIdSpy).toHaveBeenCalledWith('1234');

    rerenderWithRouter(<PipelineEditor entityId="54321" renderGraph={false} />);

    expect(clearErrorSpy).toHaveBeenCalledTimes(3);
    expect(clearEntityPipelineSpy).toHaveBeenCalledTimes(3);
    expect(getEntityPipelineSpy).toHaveBeenLastCalledWith('54321', 'APPROVED');
    expect(setDisplayedGraphSpy).toHaveBeenCalledTimes(3);
    expect(getConnectorEntitiesSpy).toHaveBeenLastCalledWith('54321');
    expect(setPipelineIdSpy).toHaveBeenLastCalledWith('54321');
    expect(getUserPreferenceSpy).toHaveBeenCalled();
    expect(setSelectedGraphNodeSpy).toHaveBeenLastCalledWith();
  });

  test('should show loading message', async () => {
    mockedAjaxUtils().get.mockResolvedValue({ data: {} });
    renderWithRouter(<PipelineEditor entityId="1234" renderGraph={false} />, {
      testState: getMinimalEntityPipelineState({
        entityPipeline: {
          entityPipelineFetching: true,
        },
      }),
    });
    expect(await screen.findByText(tc('loading'))).toBeInTheDocument();
  });
});
