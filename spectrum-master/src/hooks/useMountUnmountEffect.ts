import { useEffect } from 'react';

// This hook can be used to replace the `componentDidMount/Unmount` life cycle
// properties whem moving from class components to functional components.
const useMountUnmountEffect = (handler: () => any) => {
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(handler, []);
};

export default useMountUnmountEffect;
