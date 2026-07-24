//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tag as ATag, Input, Icon } from 'antd';
import cx from 'classnames';
import { TweenOneGroup } from 'rc-tween-one';
import { useState, memo, useEffect } from 'react';
import * as React from 'react';

import './Tag.less';
import Can, { PermissionErrorModes } from 'components/Can';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { AllPermissions } from 'utils/PermissionsConstants';

export type TagValueModel = string[];

interface TagProps {
  /**
   * Additional className that will be added to the container of the input tags
   */
  className?: string;
  /**
   * default values for the tags
   */
  defaultValue?: TagValueModel;

  value?: TagValueModel;
  /**
   * disable/enable the tags input
   */
  disabled: boolean;
  /**
   * Handler when a value have changed
   */
  onChange?: (values: TagValueModel) => void;
  /**
   * placeholder text when there are no tags
   */
  emptyPlaceholder?: string;
  /**
   * id for the input
   */
  id: string;

  placeholder?: string;
}

const Tag = memo(
  ({ className, disabled, onChange, value, defaultValue, emptyPlaceholder, id, placeholder }: TagProps) => {
    const { userHasPermission } = useUserHasPermission();

    const [newTagValue, setNewTagValue] = useState('');
    const [tags, setTags] = useState<string[]>(value ?? defaultValue ?? []);

    const deleteTag = (removedTag: string) => {
      const newTags = tags.filter((tag) => tag !== removedTag);
      setTags(newTags);
      onChange && onChange(newTags);
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      setNewTagValue(e.target.value);
    };

    const handleInputConfirm = () => {
      if (newTagValue && tags.indexOf(newTagValue) === -1) {
        const newTags = [...tags, newTagValue];
        setTags(newTags);
        onChange && onChange(newTags);
      }
      setNewTagValue('');
    };

    useEffect(() => {
      if (value) {
        setTags(value);
      }
    }, [value]);

    return (
      <Can permission={AllPermissions.READ_TAG} errorMode={PermissionErrorModes.ReplaceWithText}>
        <div className={cx('synri-input-tags', className)}>
          {!disabled && (
            <Can permission={AllPermissions.ASSIGN_TAG}>
              <Input
                id={id}
                type="text"
                className="synri-tag-input"
                value={newTagValue}
                prefix={<Icon type="tag" theme="filled" className="synri-tag-input-prefix" />}
                onChange={handleInputChange}
                onBlur={handleInputConfirm}
                onPressEnter={handleInputConfirm}
                placeholder={placeholder}
              />
            </Can>
          )}
          <div className={cx('synri-tags', { disabled })}>
            <TweenOneGroup
              enter={{
                scale: 0.8,
                opacity: 0,
                type: 'from',
                duration: 100,
              }}
              leave={{ opacity: 0, width: 0, scale: 0, duration: 200 }}
              appear={false}>
              {tags.length > 0
                ? tags.map((tag) => (
                    <span key={tag} className="synri-tag" data-testid="tag">
                      <ATag
                        closable={!disabled && userHasPermission(AllPermissions.REMOVE_TAG)}
                        onClose={(e: React.SyntheticEvent) => {
                          e.preventDefault();
                          deleteTag(tag);
                        }}>
                        {tag}
                      </ATag>
                    </span>
                  ))
                : emptyPlaceholder && <div className="synri-tag-input-empty-state">{emptyPlaceholder}</div>}
            </TweenOneGroup>
          </div>
        </div>
      </Can>
    );
  }
);

export default Tag;
