import { useAutoMapContext } from './AutoMap.context';

export const useAutoMap = () => {
  const { visible, setVisible } = useAutoMapContext();
  return {
    visible,
    setVisible,
  };
};
