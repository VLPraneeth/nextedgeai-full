import cx from 'classnames';
import * as React from 'react';
import { useEffect, useRef, useState } from 'react';

import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';

import './Image.less';

const DefaultFallbackWhen = [AppConstants.FETCH_STATUS.LOADING, AppConstants.FETCH_STATUS.ERROR];

interface ImageProps {
  /** this is the react component/tree to render if FETCH_STATUS is in `fallbackWhen` */
  fallback?: React.ReactElement | null;
  /** FETCH_STATUS states that will trigger fallback */
  fallbackWhen?: FetchStatus[];
  /** image alt attr */
  alt: string;
  /** image src attr */
  src: string;
  /** optional className for the image element */
  className?: string;
}

const Image = ({ fallback = null, fallbackWhen = DefaultFallbackWhen, className, alt, src, ...props }: ImageProps) => {
  const [status, setStatus] = useState<FetchStatus>(AppConstants.FETCH_STATUS.IDLE);
  const imageRef = useRef<HTMLImageElement | null>(null);

  useEffect(() => {
    if (imageRef.current) {
      const imgNode = imageRef.current;
      setStatus(AppConstants.FETCH_STATUS.LOADING);

      const imageLoaded = () => setStatus(AppConstants.FETCH_STATUS.SUCCESS);
      const imageFailed = () => setStatus(AppConstants.FETCH_STATUS.ERROR);
      imgNode.addEventListener('load', imageLoaded);
      imgNode.addEventListener('error', imageFailed);

      return () => {
        imgNode.removeEventListener('load', imageLoaded);
        imgNode.removeEventListener('error', imageFailed);
      };
    }
  }, [src, setStatus]);

  if (fallbackWhen.includes(status) && fallback) {
    return fallback;
  }

  return (
    <img
      ref={imageRef}
      className={cx('synri-image', `synri-image-${status}`, className)}
      alt={alt}
      src={src}
      {...props}
    />
  );
};

export default Image;
