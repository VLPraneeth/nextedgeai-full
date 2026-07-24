import { useEffect } from 'react';

const visibilityChangeEvent = 'visibilitychange';
const blurEvent = 'blur';
const focusEvent = 'focus';

interface UseWindowFocusConfig {
  /** callback to fire when the window gains focus again */
  onFocus: (evt?: Event) => void;
  /** callback to fire when the window is backgrounded, suspended, etc */
  onBlur: (evt?: Event) => void;
}

/**
 * useWindowFocus gives you easy access to window visibility events.
 * Useful to fire events when the browser is foregrounded or backgrounded. eg,
 * refresh data when the browser gains focus, or pause data polling when the
 * window is backgrounded
 */
function useWindowFocus({ onFocus, onBlur }: UseWindowFocusConfig) {
  useEffect(() => {
    function handleVisibilityChange(evt?: Event) {
      const isDocVisible = isDocumentVisible();

      if (evt?.type === 'blur' || !isDocVisible) {
        // if the event is blur, or we know the document is not visible, fire onBlur
        typeof onBlur === 'function' && onBlur(evt);
      } else if (evt?.type === 'focus' || isDocVisible) {
        // if the event is focus, or we know the document is visible, fire onFocus
        typeof onFocus === 'function' && onFocus(evt);
      }
    }

    window.addEventListener(visibilityChangeEvent, handleVisibilityChange, false);
    window.addEventListener(focusEvent, handleVisibilityChange, false);
    window.addEventListener(blurEvent, handleVisibilityChange, false);

    return () => {
      window.removeEventListener(visibilityChangeEvent, handleVisibilityChange);
      window.removeEventListener(focusEvent, handleVisibilityChange);
      window.removeEventListener(blurEvent, handleVisibilityChange);
    };
  }, [onBlur, onFocus]);
}

function isDocumentVisible(): boolean {
  return (
    typeof document === 'undefined' ||
    document.visibilityState === undefined ||
    document.visibilityState === 'visible' ||
    document.visibilityState !== 'hidden'
  );
}

export default useWindowFocus;
