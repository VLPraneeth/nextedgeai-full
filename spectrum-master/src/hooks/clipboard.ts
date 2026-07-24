import { copyStringToClipboard } from 'utils/StringUtil';

const useClipboard = () => {
  return {
    addToClipboard: copyStringToClipboard,
    readFromClipboard: () => {
      throw new Error('Not implemented yet');
    },
  };
};

export default useClipboard;
