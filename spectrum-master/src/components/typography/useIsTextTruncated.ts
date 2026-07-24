import { useEffect, useState, Dispatch, SetStateAction } from 'react';

/**
 * useIsTextTruncated will tell you if a given text node has overflowed it's
 * container and is now truncated by the CSS rules
 *
 * @example
 * const App = () => {
 *   const [measuredElement, isTruncated] = useIsTextTruncated<HTMLDivElement>();
 *
 *   return (
 *     <div>
 *       <div className="truncate-text" ref={measuredElement}>
 *         Some long text string goes here that will overflow it's fictional container
 *       </div>
 *       {isTruncated && (<div>Text is truncated!</div>)}
 *     </div>
 *   )
 * }
 */

type UseIsTextTruncatedResult<T> = [Dispatch<SetStateAction<T | null | undefined>>, boolean, T | null | undefined];

function useIsTextTruncated<T extends HTMLElement = HTMLSpanElement>(): UseIsTextTruncatedResult<T> {
  const [ref, setRef] = useState<T | null>();
  const [isTruncated, setIsTruncated] = useState<boolean>(false);

  useEffect(() => {
    if (!ref) {
      return;
    }

    setIsTruncated(ref.offsetWidth < ref.scrollWidth);
  }, [ref]);

  return [setRef, isTruncated, ref];
}

export default useIsTextTruncated;
