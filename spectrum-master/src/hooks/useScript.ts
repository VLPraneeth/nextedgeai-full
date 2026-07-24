import { useState, ScriptHTMLAttributes } from 'react';

import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

import useMountUnmountEffect from './useMountUnmountEffect';

// track loaded scripts here so we can dedupe
const scriptCache = new Map();

const { FETCH_STATUS } = AppConstants;

const addScript = (src: string, attrs: ScriptAttributes) => {
  return new Promise<void>((resolve, reject) => {
    const script: HTMLScriptElement = document.createElement('script');
    script.src = src;
    script.async = true;

    // given attrs, add them to the script element
    Object.entries(attrs).forEach((attribute) => {
      const attrName = attribute[0] as keyof ScriptHTMLAttributes<HTMLScriptElement>;
      const attrValue: string = attribute[1];

      script.setAttribute(attrName, attrValue);
    });

    const onLoad = () => {
      script.removeEventListener('load', onLoad);
      script.removeEventListener('error', onError);
      return resolve();
    };

    const onError = () => {
      script.removeEventListener('load', onLoad);
      script.removeEventListener('error', onError);

      // remove from the DOM
      script.remove();
      return reject();
    };

    script.addEventListener('load', onLoad);
    script.addEventListener('error', onError);

    // Add script to document body
    document.body.appendChild(script);
  });
};

type ScriptAttributes = ScriptHTMLAttributes<HTMLScriptElement>;

/**
 * useScript lets you add external scripts to your project
 *
 * This hook will not load the same script multiple times.
 */
function useScript(src: string, attrs: ScriptAttributes) {
  const [status, setStatus] = useState<ValuesOf<typeof FETCH_STATUS>>(FETCH_STATUS.IDLE);

  useMountUnmountEffect(() => {
    setStatus(FETCH_STATUS.LOADING);

    let loadScriptPromise;

    if (scriptCache.has(src)) {
      loadScriptPromise = scriptCache.get(src);
    } else {
      // add the script and it's promise to the cache
      loadScriptPromise = addScript(src, attrs);
      scriptCache.set(src, loadScriptPromise);
    }

    loadScriptPromise
      .then(() => {
        setStatus(FETCH_STATUS.SUCCESS);
      })
      .catch(() => {
        setStatus(FETCH_STATUS.ERROR);
      });
  });

  return status;
}

export default useScript;
