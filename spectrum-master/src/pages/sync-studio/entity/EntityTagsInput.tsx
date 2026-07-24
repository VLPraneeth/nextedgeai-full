// @ts-nocheck
import { Icon } from 'antd';
import { debounce, difference, first } from 'lodash';
import { useState } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import {
  getTagsLike as getTagsLikeAction,
  addTag as addTagAction,
  removeTag as removeTagAction,
} from 'actions/tagActions';
import AutoSuggest from 'components/AutoSuggest';
import { getDervEntitiesWithFieldDraftSummary } from 'selectors/entitySelectors';
import AppConstants from 'utils/AppConstants';
import { getEntityTags } from 'utils/EntityUtil';
import { tNamespaced } from 'utils/i18nUtil';

import './EntityTagsInput.less';

const tn = tNamespaced('EntityEditorEntityPanel');

function EntityTagsInput({
  entityId,

  entities,
  tagsFetching,
  tagsSuggest,

  getTagsLike,
  addTag,
  removeTag,
}) {
  const [currentTags, setCurrentTags] = useState(getEntityTags(entities, entityId));
  const _onSearchTag = debounce((value) => {
    if (value) {
      getTagsLike(value);
    }
  }, 200);

  const onTagsChange = (newTags) => {
    // Adding a tag
    if (currentTags.length < newTags.length) {
      const diffTag = difference(newTags, currentTags);
      addTag({
        type: AppConstants.TAG_TYPES.ENTITY,
        objectId: entityId,
        newTag: first(diffTag),
      });
    }
    // Removing a tag
    else if (currentTags.length > newTags.length) {
      const diffTag = difference(currentTags, newTags);
      removeTag({
        type: AppConstants.TAG_TYPES.ENTITY,
        objectId: entityId,
        removedTag: first(diffTag),
      });
    }

    setCurrentTags(newTags);
  };

  return (
    <div className="synri-entity-tags">
      <AutoSuggest
        prefix={<Icon type="tag" style={{ color: '#AAB6BE' }} />}
        placeholder={tn('add_tag')}
        onSearch={_onSearchTag}
        className="add-tag-input"
        data={tagsSuggest}
        value={currentTags}
        fetching={tagsFetching}
        onChange={onTagsChange}
      />
    </div>
  );
}

if (process.env.NODE_ENV !== 'production') {
  const { string } = require('prop-types');

  EntityTagsInput.propTypes = {
    entityId: string.isRequired,
  };
}

export default connect(
  (state, props) => ({
    entities: getDervEntitiesWithFieldDraftSummary(state, props),
    tagsFetching: state.tag.tagsFetching,
    tagsSuggest: state.tag.tags,
  }),
  (dispatch) =>
    bindActionCreators(
      {
        getTagsLike: getTagsLikeAction,
        addTag: addTagAction,
        removeTag: removeTagAction,
      },
      dispatch
    )
)(EntityTagsInput);
