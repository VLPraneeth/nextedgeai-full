import { useEffect, useRef } from 'react';

const useEventListener = (
  eventName: keyof HTMLElementEventMap,
  handlerStable: EventListener,
  element: HTMLElement | Window = window
) => {
  // Use a ref for the handler to prevent accidentatlly passing a new handler
  // on each renter a re-triggering the addEventHandler
  const storedHandler = useRef<EventListener>();

  useEffect(() => {
    storedHandler.current = handlerStable;
  }, [handlerStable]);

  useEffect(() => {
    const eventListener: EventListener = (event) => storedHandler.current?.(event);

    if (!element || !element.addEventListener) {
      console.log('Cannot add handler to element:', element);
      return;
    }

    element.addEventListener(eventName, eventListener);

    return () => {
      element.removeEventListener(eventName, eventListener);
    };
  }, [eventName, element]);
};

export default useEventListener;
