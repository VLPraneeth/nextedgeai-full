import { mockedAjaxUtils } from 'tests/helpers';
import { createAppTestStore } from 'tests/helpers/StoreHelper';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/StringUtil';

import { ActionTypes } from '../tagActions';
import { addTag, removeTag, getTagsLike } from '../tagActions';

jest.mock('utils/AjaxUtil');

const AjaxUtils = mockedAjaxUtils();
const mockedPost = AjaxUtils.post;
const mockedGet = AjaxUtils.get;

describe('Tag Actions', () => {
  test('create tag success', () => {
    mockedPost.mockImplementation((url, _data) => {
      expect(url).toBe(DataUrlConstants.ADD_TAG);
      return Promise.resolve({ data: { test: 'test' } });
    });

    const expectedActions = [
      { type: ActionTypes.ADD_TAG_PENDING },
      {
        type: ActionTypes.ADD_TAG_FULFILLED,
        payload: { test: 'test' },
        params: [{ name: 'testingnow', taggedId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }],
      },
    ];

    const store = createAppTestStore();
    return store
      .dispatch(addTag({ newTag: 'testingnow', objectId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }))
      .then(() => {
        expect(store.getActions()).toEqual(expectedActions);
      });
  });

  test('create tag failed', () => {
    mockedPost.mockImplementation((url, _data) => {
      expect(url).toBe(DataUrlConstants.ADD_TAG);
      return Promise.reject({ error: 'error' });
    });

    const expectedActions = [
      { type: ActionTypes.ADD_TAG_PENDING },
      {
        type: ActionTypes.ADD_TAG_FAILED,
        error: { error: 'error' },
        params: [{ name: 'testingnow', taggedId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }],
      },
    ];

    const store = createAppTestStore();
    return store
      .dispatch(addTag({ newTag: 'testingnow', objectId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }))
      .then(() => {
        expect(store.getActions()).toEqual(expectedActions);
      });
  });

  test('remove tag success', () => {
    mockedPost.mockImplementation((url, _data) => {
      expect(url).toBe(DataUrlConstants.REMOVE_TAG);
      return Promise.resolve({ data: { test: 'test' } });
    });

    const expectedActions = [
      { type: ActionTypes.REMOVE_TAG_PENDING },
      {
        type: ActionTypes.REMOVE_TAG_FULFILLED,
        payload: { test: 'test' },
        params: [{ name: 'testingnow', taggedId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }],
      },
    ];

    const store = createAppTestStore();
    return store
      .dispatch(removeTag({ removedTag: 'testingnow', objectId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }))
      .then(() => {
        expect(store.getActions()).toEqual(expectedActions);
      });
  });

  test('remove tag failed', () => {
    mockedPost.mockImplementation((url, _data) => {
      expect(url).toBe(DataUrlConstants.REMOVE_TAG);
      return Promise.reject({ error: 'error' });
    });

    const expectedActions = [
      { type: ActionTypes.REMOVE_TAG_PENDING },
      {
        type: ActionTypes.REMOVE_TAG_FAILED,
        error: { error: 'error' },
        params: [{ name: 'testingnow', taggedId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }],
      },
    ];

    const store = createAppTestStore();
    return store
      .dispatch(removeTag({ removedTag: 'testingnow', objectId: '5f98703ad13cb43e9bfcf9ee', type: 'entity' }))
      .then(() => {
        expect(store.getActions()).toEqual(expectedActions);
      });
  });

  test('get tags like', () => {
    const uri = 'http://syncari.com';
    mockedGet.mockImplementation((url, _data) => {
      expect(url).toBe(replaceToken(DataUrlConstants.TAG, { partialName: btoa(uri) }));
      return Promise.resolve({ data: [] });
    });

    const expectedActions = [
      { type: ActionTypes.GET_TAGS_PENDING },
      {
        params: { partialName: btoa(uri) },
        payload: [],
        type: ActionTypes.GET_TAGS_FULFILLED,
      },
    ];

    const store = createAppTestStore();
    return store.dispatch(getTagsLike(uri)).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
