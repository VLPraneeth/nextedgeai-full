import { useEffect, useRef, useState } from 'react';

import { get } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';

type GoogleCredentialResponse = {
  credential?: string;
};

type GoogleIdentityWindow = Window & {
  google?: {
    accounts: {
      id: {
        initialize: (options: { client_id: string; callback: (response: GoogleCredentialResponse) => void }) => void;
        renderButton: (
          element: HTMLElement,
          options: { theme: string; size: string; shape: string; text: string; width: number }
        ) => void;
      };
    };
  };
};

type GoogleSignInProps = {
  disabled: boolean;
  onCredential: (credential: string) => void;
};

const SCRIPT_ID = 'nextedge-google-identity';
let scriptPromise: Promise<void> | null = null;

function loadGoogleIdentity() {
  const googleWindow = window as GoogleIdentityWindow;
  if (googleWindow.google?.accounts?.id) {
    return Promise.resolve();
  }
  if (scriptPromise) {
    return scriptPromise;
  }
  scriptPromise = new Promise((resolve, reject) => {
    const existingScript = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(), { once: true });
      existingScript.addEventListener('error', () => reject(new Error('Google Identity failed to load')), {
        once: true,
      });
      return;
    }
    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Google Identity failed to load'));
    document.head.appendChild(script);
  });
  return scriptPromise;
}

const GoogleSignIn = ({ disabled, onCredential }: GoogleSignInProps) => {
  const buttonRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  const [state, setState] = useState<'loading' | 'ready' | 'disabled' | 'error'>('loading');

  useEffect(() => {
    callbackRef.current = onCredential;
  }, [onCredential]);

  useEffect(() => {
    let active = true;
    get(DataUrlConstants.GOOGLE_AUTH_CONFIG)
      .then(async (response) => {
        if (!response.data?.enabled || !response.data?.clientId) {
          if (active) {
            setState('disabled');
          }
          return;
        }
        await loadGoogleIdentity();
        if (!active || !buttonRef.current) {
          return;
        }
        const googleWindow = window as GoogleIdentityWindow;
        googleWindow.google?.accounts.id.initialize({
          client_id: response.data.clientId,
          callback: ({ credential }) => {
            if (credential) {
              callbackRef.current(credential);
            }
          },
        });
        buttonRef.current.replaceChildren();
        googleWindow.google?.accounts.id.renderButton(buttonRef.current, {
          theme: 'outline',
          size: 'large',
          shape: 'rectangular',
          text: 'continue_with',
          width: 320,
        });
        if (active) {
          setState('ready');
        }
      })
      .catch(() => {
        if (active) {
          setState('error');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  if (state === 'disabled') {
    return null;
  }

  return (
    <div className={`google-sign-in google-sign-in--${state}`} aria-busy={state === 'loading'}>
      <div
        ref={buttonRef}
        className={disabled ? 'google-sign-in__button google-sign-in__button--disabled' : 'google-sign-in__button'}
        aria-label="Continue with Google"
      />
      {state === 'loading' && <span className="google-sign-in__loading">Loading Google sign-in…</span>}
      {state === 'error' && (
        <span className="google-sign-in__error" role="status">
          Google sign-in is unavailable. Use email and password.
        </span>
      )}
    </div>
  );
};

export default GoogleSignIn;
