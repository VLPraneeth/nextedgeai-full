import { createHistory, createMemorySource, LocationProvider, Router } from '@reach/router';
import { matches } from 'lodash';

import * as EntityPipelineActions from 'actions/entityPipelineActions';
import { ActionTypes as EpActionTypes } from 'actions/entityPipelineActions';
import * as FieldPipelineActions from 'actions/fieldPipelineActions';
import * as EntityThunks from 'store/entity/thunks';
import * as PipelineActionActions from 'store/pipeline-actions/actions';
import * as PipelineActions from 'store/pipeline/actions';
import * as UserThunks from 'store/user/thunks';
import { mockedAjaxUtils, renderWithRouter, screen, sleep } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import * as pipelineUtils from 'utils/PipelineUtil';

import PipelineEditor from '../../pipeline/PipelineEditor';
import { PipelineEditorProps } from '../../pipeline/PipelineEditor.types';
import { getMinimalFieldPipelineState } from '../FieldPipelineEditor.fixtures';

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();

const Home = () => <main />;

const approvedId = 'abcdef123456';
const draftId = 'def1234abc56';

const approvedFunctionsUrl = `/arcade/api/v1/functions/field/${approvedId}`;
const draftFunctionsUrl = `/arcade/api/v1/functions/field/${draftId}`;

const checkIfActionOccurred = (
  store: { getActions: () => Record<string, any>[] },
  checkAction: Record<string, any>
) => {
  const matchCheck = matches(checkAction);
  return store.getActions().some(matchCheck);
};

const mockedApi = jest.fn((url) => {
  switch (url) {
    case `/arcade/api/v1/schema`:
      return Promise.resolve({ data: {} });
    case approvedFunctionsUrl:
      return Promise.resolve({});
    case draftFunctionsUrl:
      return Promise.resolve({});
    case `/arcade/api/v1/pipeline/fieldPipeline/abcdefghijkl`:
      return Promise.resolve({
        data: {
          id: approvedId,
          draft: {
            id: draftId,
          },
        },
      });
    default:
      return Promise.resolve({});
  }
});

ajaxMock.get.mockImplementation(mockedApi);

describe('Field pipeline editor', () => {
  afterEach(() => {
    mockedApi.mockClear();
  });

  test('should render and fetch the pipeline functions', () => {
    renderWithRouter(
      <PipelineEditor renderGraph={false} entityId="1234567890" fieldId={approvedId} graphVersion="APPROVED" />,
      getMinimalFieldPipelineState()
    );
    expect(mockedApi).toHaveBeenCalledWith(approvedFunctionsUrl);
  });

  test('when no graphVersion is provided the default displayedGraph should be set to NEW', () => {
    const { __reduxStore: store } = renderWithRouter(
      <PipelineEditor renderGraph={false} entityId="1234567890" fieldId="abcdefghijkl" />,
      getMinimalFieldPipelineState({
        fastMapper: {},
      })
    );

    // expect that we will have an action to close the modal
    expect(
      checkIfActionOccurred(store, {
        type: 'pipeline/setDisplayedGraph',
        payload: {
          displayedGraph: 'NEW',
        },
      })
    ).toBe(true);
  });

  test('fetches APPROVED field functions when in DRAFT mode but no draft exists', async () => {
    const navigateSpy = jest.spyOn(pipelineUtils, 'navigateToGraphVersion');
    const { rerenderWithRouter } = renderWithRouter(
      <PipelineEditor
        renderGraph={false}
        entityId="1234567890"
        fieldId="abcdefghijkl"
        graphVersion="DRAFT"
        pipelineFetching
      />,
      getMinimalFieldPipelineState({
        pipeline: { displayedGraph: 'DRAFT' },
      })
    );

    rerenderWithRouter(
      <PipelineEditor
        renderGraph={false}
        entityId="1234567890"
        fieldId="abcdefghijkl"
        graphVersion="DRAFT"
        pipelineFetching={false}
      />
    );

    expect(navigateSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        entityId: '1234567890',
        fieldId: 'abcdefghijkl',
        graphVersion: 'APPROVED',
        replace: true,
      })
    );
  });

  test('node config modal is closed when we navigate', async () => {
    const route = '/sync-studio/entity/5e0d22e77df51d38b296628e/field/1212512512123as/pipeline/DRAFT';
    const history = createHistory(createMemorySource(route));

    // show modal by default, then we'll see if it closes when the route changes
    const { __reduxStore: store } = renderWithRouter(
      <LocationProvider history={history}>
        <Router>
          <Home path="/" />
          <PipelineEditor
            key="field-pipeline-editor"
            path="/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion"
          />
        </Router>
      </LocationProvider>,
      getMinimalFieldPipelineState()
    );

    // navigate away from this page
    await history.navigate('/');

    // Allow an extra render to let the cleanup function get called
    await sleep(500);

    // expect that we will have an action to close the modal
    expect(
      checkIfActionOccurred(store, {
        type: EpActionTypes.SHOW_NODE_CONFIG,
        visible: false,
      })
    ).toBe(true);
  });

  test('should reinitialize on fieldId change', () => {
    const setDisplayedGraphSpy = jest.spyOn(PipelineActions, 'setDisplayedGraph');

    const getAttributeNodesSpy = jest.spyOn(FieldPipelineActions, 'getAttributeNodes');
    const clearFieldPipelineSpy = jest.spyOn(FieldPipelineActions, 'clearFieldPipeline');
    const clearAttributeNodesSpy = jest.spyOn(FieldPipelineActions, 'clearAttributeNodes');

    const getFieldPipelineActionsSpy = jest.spyOn(PipelineActionActions, 'getFieldPipelineActions');
    const getEntitiesSpy = jest.spyOn(EntityThunks, 'getEntities');
    const setPipelineIdSpy = jest.spyOn(PipelineActions, 'setPipelineId');
    const getUserPreferenceSpy = jest.spyOn(UserThunks, 'getUserPreference');
    const clearErrorSpy = jest.spyOn(FieldPipelineActions, 'clearError');
    const setPipelineContextSpy = jest.spyOn(EntityPipelineActions, 'setPipelineContext');
    const setSelectedGraphNodeSpy = jest.spyOn(EntityPipelineActions, 'setSelectedGraphNode');

    const { rerenderWithRouter } = renderWithRouter(
      <PipelineEditor
        renderGraph={false}
        fieldId="12345"
        {...({
          entityId: '1234567890',
          graphVersion: 'APPROVED',
        } as PipelineEditorProps)}
      />,
      getMinimalFieldPipelineState()
    );

    expect(setDisplayedGraphSpy).toHaveBeenCalledWith(AppConstants.GRAPH_STATUS.APPROVED);
    expect(getAttributeNodesSpy).toHaveBeenCalledWith('12345');
    expect(getFieldPipelineActionsSpy).toHaveBeenCalledWith('12345');
    expect(getEntitiesSpy).toHaveBeenCalledWith();
    expect(setPipelineContextSpy).toHaveBeenCalledWith(AppConstants.PIPELINE_CONTEXT.FIELD);
    expect(setPipelineIdSpy).toHaveBeenCalledWith('1234567890');
    expect(getUserPreferenceSpy).toHaveBeenCalledTimes(1);
    expect(clearFieldPipelineSpy).toHaveBeenCalled();
    expect(clearErrorSpy).toHaveBeenCalled();

    rerenderWithRouter(
      <PipelineEditor
        renderGraph={false}
        fieldId="543210"
        {...({
          entityId: '1234567890',
          graphVersion: 'DRAFT',
        } as PipelineEditorProps)}
      />
    );

    expect(setDisplayedGraphSpy).toHaveBeenCalledTimes(3);
    expect(clearErrorSpy).toHaveBeenCalledTimes(3);
    expect(clearFieldPipelineSpy).toHaveBeenCalledTimes(3);
    expect(clearAttributeNodesSpy).toHaveBeenCalled();
    expect(setSelectedGraphNodeSpy).toHaveBeenCalledWith();

    expect(getAttributeNodesSpy).toHaveBeenLastCalledWith('543210');
    expect(getFieldPipelineActionsSpy).toHaveBeenLastCalledWith('543210');
    expect(setPipelineContextSpy).toHaveBeenLastCalledWith(AppConstants.PIPELINE_CONTEXT.FIELD);
    expect(setPipelineIdSpy).toHaveBeenCalledWith('1234567890');
    expect(getUserPreferenceSpy).toHaveBeenCalledTimes(2);
  });

  test('should show loading message with nodes', () => {
    renderWithRouter(
      <PipelineEditor
        renderGraph={false}
        fieldId="12345"
        {...({
          entityId: '1234567890',
          graphVersion: 'APPROVED',
        } as PipelineEditorProps)}
      />,
      getMinimalFieldPipelineState()
    );
    expect(screen.getByTestId('field-pipeline-loading')).toBeInTheDocument();
  });
});
