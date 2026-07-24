// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export function getTransactionRangeParams(params) {
  const { startDate, endDate } = params;
  return {
    startDate,
    endDate,
    pageNumber: 0,
  };
}
