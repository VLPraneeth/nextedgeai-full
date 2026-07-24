//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useEffect } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { connectMessageStream } from 'store/app/actions';

const useConnectMessageStream = () => {
  const dispatch = useEnhancedDispatch();

  const currentInstanceNextEdgeId = useEnhancedSelector((state) => state.user.currentInstanceNextEdgeId);
  const previousCurrentInstanceNextEdgeId = usePreviousValue(currentInstanceNextEdgeId);

  const userEmail = useEnhancedSelector((state) => state.user.email);

  useEffect(() => {
    if (currentInstanceNextEdgeId && currentInstanceNextEdgeId !== previousCurrentInstanceNextEdgeId) {
      dispatch(connectMessageStream(currentInstanceNextEdgeId, userEmail));
    }
  }, [currentInstanceNextEdgeId, dispatch, previousCurrentInstanceNextEdgeId, userEmail]);
};

export default useConnectMessageStream;
