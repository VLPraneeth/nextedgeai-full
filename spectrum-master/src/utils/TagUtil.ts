// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { map } from 'lodash';

export function getResponseTagsLike(data) {
  if (data?.length) {
    return map(data, (action) => {
      return {
        title: action,
        text: action,
        key: action,
        value: action,
      };
    });
  }
  return [];
}
