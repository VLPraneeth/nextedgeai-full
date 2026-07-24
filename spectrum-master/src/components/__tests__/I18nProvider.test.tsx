import I18nProvider, { useI18nContext } from 'components/I18nProvider';
import { renderHookWithProvider } from 'tests/helpers';

test('Test I18nProvider context hook', async () => {
  const expectedNamespace = 'DataStudio';

  const { tc, tn, namespace } = renderHookWithProvider(useI18nContext, I18nProvider, { namespace: expectedNamespace });

  expect(tc('apply')).toBe('Apply');
  expect(tn('window_title')).toBe('Data Studio');
  expect(namespace).toBe(expectedNamespace);
});
