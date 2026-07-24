import * as React from 'react';

export type LazyComponentImportFn = Parameters<typeof React.lazy>[0];

// More attempts in cases where it takes longer
// for the server to get rolled over
export const DEFAULT_RETRY_ATTEMPTS = 45;

export const DEFAULT_RETRY_DELAY_MS = 2000;

// Number of attempt to refetch the manifest file.
export const MAX_MANIFEST_RETRY_ATTEMPTS = 1;
const MANIFEST_RETRY_COUNT = 'MANIFEST_RETRY_COUNT';

const getManifestRetryCount = () => parseInt(String(sessionStorage.getItem(MANIFEST_RETRY_COUNT))) || 0;
const setManifestRetryCount = (count: number) => sessionStorage.setItem(MANIFEST_RETRY_COUNT, String(count));

// Initialize our manifest retry counter
// and skip reset on retry.
(() => {
  const manifestRetryCount = getManifestRetryCount();
  if (!manifestRetryCount || manifestRetryCount > MAX_MANIFEST_RETRY_ATTEMPTS) {
    setManifestRetryCount(0);
  }
})();

/**
 * lazyLoadComponentWithRetry
 *
 * given a async component loader fn, such as `import()`, this will
 * attempt to load, and retry if necessary
 */
const lazyLoadComponentWithRetry = <T extends unknown>(
  lazyComponentFactory: () => Promise<T>,
  retryAttempts = DEFAULT_RETRY_ATTEMPTS,
  retryIntervalMs = DEFAULT_RETRY_DELAY_MS
): Promise<T> => {
  // rename for semantics, this way the variable shows in autocompletion as 'retryAttempts'
  const remainingAttempts = retryAttempts;

  return new Promise((resolve, reject) => {
    lazyComponentFactory()
      .then(resolve)
      .catch((error) => {
        console.warn('Error loading Component, will retry…', { error, remainingAttempts });

        setTimeout(() => {
          if (remainingAttempts < 1) {
            const manifestRetryCount = getManifestRetryCount();
            if (manifestRetryCount < MAX_MANIFEST_RETRY_ATTEMPTS) {
              // We're going to perform a manifest fetch retry. This is for case the user got the manifest from
              // the old build and fetching its assets (no longer exists on the rolled over build).
              // All the front end servers should be up at this point.
              setManifestRetryCount(manifestRetryCount + 1);
              window.location.reload();
            } else {
              // we're out of attempts, go ahead and reject
              setManifestRetryCount(0);
              reject(error);
            }
            return;
          }

          // try to load the components again
          lazyLoadComponentWithRetry(lazyComponentFactory, remainingAttempts - 1).then(resolve, reject);
        }, retryIntervalMs);
      });
  });
};

/**
 * Wrapper around React.lazy that provides ability to load a component concurrently
 * as well as the ability to retry in case of load failure.
 *
 * Why would we want to load concurrently?
 * Loading concurrently is very useful to pre-fetch a component that is likely to be used
 * soon. It accomplishes this by firing the import fn, which starts the Promsise resolution.
 * Then, later when React.Suspense fires the lazyComponentFactory fn, the promise will (hopefully)
 * already be resolved and load instantly without Suspense fallback
 *
 * Why would we want to retry?
 * Maybe the browser hit a network error, maybe the server was performing a rolling update. Retrying
 * will give the app a chance to recover before failing.
 *
 * @example
 *
 * // load eagerly because it's probably our first route
 * const Home = EnhancedReactLazy(() => import("components/home"), { loadConcurrently: true });
 * // load ErrorPage eagerly just in case
 * const ErrorPage = EnhancedReactLazy(() => import("components/errorPage"), { loadConcurrently: true });
 * // retry loading this page slower for some reason
 * const Settings = EnhancedReactLazy(() => import("components/settings"), { retryIntervalMs: 5000 });
 * // regular lazy, with default retry
 * const OtherPage = EnhancedReactLazy(() => import("components/other"));
 *
 */

export type ReactWithLazyOptions = {
  loadConcurrently?: boolean;
  retryAttempts?: number;
  retryIntervalMs?: number;
};

export const EnhancedReactLazy = (
  lazyComponentFactory: LazyComponentImportFn,
  enhancedOptions?: ReactWithLazyOptions
) => {
  const { loadConcurrently, retryAttempts, retryIntervalMs } = { ...enhancedOptions };

  // if we're supposed to be loading concurrently, then fire the fn to start resolving the promise
  if (loadConcurrently) {
    lazyComponentFactory();
  }

  return React.lazy(() => lazyLoadComponentWithRetry(lazyComponentFactory, retryAttempts, retryIntervalMs));
};

/**
 * jumps through some hoops to give us the ability to lazily import named exports
 *
 * @example
 * const MyComponent = React.lazy(() => import('./MyComponent'));
 * const { MyNamedComponent, MyOtherNamedComponent } = lazily(() => import('./MyOtherComponents'));
 *
 */
export const lazily = <T extends Record<string, unknown>, U extends Extract<keyof T, string>>(
  loader: (x?: string) => Promise<T>,
  lazyOptions?: ReactWithLazyOptions
) =>
  new Proxy(({} as unknown) as T, {
    get: (_, componentName: U) => {
      if (typeof componentName === 'string') {
        return EnhancedReactLazy(
          () =>
            loader(componentName).then((x) => ({
              default: (x[componentName] as unknown) as React.ComponentType<any>,
            })),
          lazyOptions
        );
      }
    },
  });
