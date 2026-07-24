import { ReactNode } from 'react';
import { animated, useTransition } from 'react-spring';

export interface FadeInOutProps {
  visible?: boolean;
  children: ReactNode;
}

const FadeInOut = ({ visible, children }: FadeInOutProps) => {
  const transitions = useTransition(visible, {
    from: { opacity: 0 },
    enter: { opacity: 1 },
    leave: { opacity: 0 },
    config: { duration: 200 },
  });

  return transitions((styles, item) => item && <animated.div style={styles}>{children}</animated.div>);
};

export default FadeInOut;
