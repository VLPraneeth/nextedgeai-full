// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export function listToMapById(list) {
  return list.reduce(
    (acc, item) => ({
      ...acc,
      [item.id]: item,
    }),
    {}
  );
}
