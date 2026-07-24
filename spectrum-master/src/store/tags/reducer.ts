import produce, { Draft } from 'immer';

import AppConstants from 'utils/AppConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';
import { safeDecodeBase64 } from 'utils/StringUtil';

import { TagState, TagAction, GET_TAGS_PENDING, GET_TAGS_FULFILLED, GET_TAGS_FAILED } from './types';

export function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.TAG),
    tags: [],
    tagsFetching: false,
  };
}

// TODO: better typings - this is from the ajax response
interface TagResult {
  title: string;
  text: string;
  key: string;
  value: string;
}

function appendTypedResult(params: { partialName: string }, result: TagResult[] = []): TagResult[] {
  const partialName = safeDecodeBase64(params.partialName);

  if (result.find((res) => res.title === partialName)) {
    return result;
  }

  return result.concat([
    {
      title: partialName,
      text: partialName,
      key: partialName,
      value: partialName,
    },
  ]);
}

const reducer = produce((draft: Draft<TagState>, action: TagAction) => {
  switch (action.type) {
    case GET_TAGS_PENDING:
      draft.tagsError = undefined;
      draft.tagsFetching = true;
      break;
    case GET_TAGS_FULFILLED:
      draft.tags = appendTypedResult(action.params, action.payload);
      draft.tagsFetching = false;
      break;
    case GET_TAGS_FAILED:
      draft.tags = appendTypedResult(action.params);
      draft.tagsError = action.error;
      draft.tagsFetching = false;
      break;
  }
}, _getDefaultState());

export default reducer;
