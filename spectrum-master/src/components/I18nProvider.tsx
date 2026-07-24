import { useContext, useMemo } from 'react';
import * as React from 'react';

import { NamespaceKey, tCommon, tNamespaced, t } from 'utils/i18nUtil';

export interface I18nContextShape {
  namespace: NamespaceKey;
  t: typeof t;
  tc: typeof tCommon;
  tn: ReturnType<typeof tNamespaced>;
}

const I18nContext = React.createContext<I18nContextShape>({
  namespace: 'Common',
  t,
  tc: tCommon,
  tn: tCommon,
});

/**
 * helper hook for accessing I18n context
 */
export const useI18nContext = () => useContext(I18nContext);

/**
 * helper hook for accessing I18n namespace
 */
export const useI18nNamespace = (namespace: NamespaceKey = 'Common') =>
  useMemo(() => tNamespaced(namespace), [namespace]);

export interface I18nProviderProps {
  namespace: NamespaceKey;
  children: React.ReactNode;
}

/**
 * I18N provider
 * Wrap any tree in the app with a specific i18n namespace.
 *
 * Context contains memoized fns for access Common translations and
 * namespaced translations as well as the namespace key
 *
 */
const I18nProvider = ({ namespace, children }: I18nProviderProps) => {
  const value = useMemo(
    () => ({
      namespace,
      t,
      tc: tCommon,
      tn: tNamespaced(namespace),
    }),
    [namespace]
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
};

/**
 * HOC for wrapping existing nodes or small trees without
 * requiring writing a new wrapper class just for this
 */

export const withI18n = <Props extends object>(Component: React.ComponentType<Props>, namespace: NamespaceKey) => (
  props: Props
) => {
  return (
    <I18nProvider namespace={namespace}>
      <Component {...props} />
    </I18nProvider>
  );
};

export default I18nProvider;
