import { useCallback } from 'react';

import { setWindowTitle } from 'utils/AppUtil';

import { useBreadcrumbContext } from './BreadcrumbContext';

export const useBreadcrumb = () => {
  const { urlNameMap, setUrlNameMap } = useBreadcrumbContext();

  const setUrlName = useCallback(
    (url: string, name: string) => {
      url = url.toLowerCase();
      if (urlNameMap[url] && urlNameMap[url] === name) {
        return;
      }
      setUrlNameMap((current) => ({
        ...current,
        [url]: name,
      }));
    },
    [setUrlNameMap, urlNameMap]
  );

  const refreshTitle = useCallback(() => {
    const title: string[] = [];
    let partialPath = '';
    const sections = window.location.pathname?.toLowerCase()?.split('/');
    sections.forEach((section) => {
      if (!section) {
        return;
      }
      partialPath += `/${section}`;

      if (urlNameMap[partialPath]) {
        title.push(urlNameMap[partialPath]);
      }
    });
    setWindowTitle(title.join('・'));
  }, [urlNameMap]);

  return { setUrlName, urlNameMap, refreshTitle };
};
