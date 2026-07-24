//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { useContext } from 'react';

import { ListCtx } from './ActionHeader';

export const useListContext = () => {
  const { onDeleteItem } = useContext(ListCtx);
  return {
    onDeleteItem,
  };
};
