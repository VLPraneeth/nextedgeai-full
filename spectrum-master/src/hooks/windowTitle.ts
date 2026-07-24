import { useEffect } from 'react';

import { useI18nContext } from 'components/I18nProvider';
import { setWindowTitle } from 'utils/AppUtil';

/**
 * Sets the window title and sets the title to the previous string when unmounted
 */
export const useWindowTitle = (title: string) => {
  useEffect(() => {
    setWindowTitle(title);
  }, [title]);
};

/**
 * uses the current i18n context to set the window title
 */
export const useTranslatedWindowTitle = (textKey = 'window_title') => {
  const { tn } = useI18nContext();
  return useWindowTitle(tn(textKey));
};
