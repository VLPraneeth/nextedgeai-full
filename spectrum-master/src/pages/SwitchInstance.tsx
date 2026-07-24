import { useEffect } from 'react';

import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import { switchInstance } from 'store/instances/slice';

export interface SwitchInstanceProps {
  instanceId: string;
}

const SwitchInstance = ({ instanceId }: SwitchInstanceProps) => {
  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(switchInstance(instanceId));
  }, [dispatch, instanceId]);

  return null;
};

export default SwitchInstance;
