import { CSSProperties, useState, useMemo } from 'react';
import { Spin } from 'antd';
import './IFrameEmbed.scss';

export interface IFrameEmbedProps {
  src: string;
  title?: string;
  width?: string | number;
  height?: string | number;
  style?: CSSProperties;
  className?: string;
  showSpinner?: boolean;
  spinnerSize?: 'small' | 'default' | 'large';
  onLoad?: () => void;
  onError?: () => void;
  sandbox?: string;
  allow?: string;
  loading?: 'lazy' | 'eager';
  replaceVariables?: Record<string, string>;
}

/**
 * Validates if the URL is a relative path (not an absolute URL)
 */
const isRelativePath = (url: string): boolean => {
  try {
    // Check if it starts with protocol (http://, https://, //, etc.)
    if (/^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(url)) {
      return false;
    }
    // Check if it starts with // (protocol-relative URL)
    if (url.startsWith('//')) {
      return false;
    }
    // Must start with / or be a relative path
    return url.startsWith('/') || !url.includes('://');
  } catch (error) {
    console.error('Invalid URL provided to IFrameEmbed:', error);
    return false;
  }
};

const IFrameEmbed = ({
  src,
  title = 'Embedded Content',
  width = '100%',
  height = '100%',
  style = {},
  className = '',
  showSpinner = true,
  spinnerSize = 'large',
  onLoad,
  onError,
  sandbox,
  allow,
  loading = 'lazy',
  replaceVariables = {},
}: IFrameEmbedProps) => {
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  // Replace variables in URL (e.g., {entityId}, {userId}, etc.)
  const resolvedSrc = useMemo(() => {
    const urlWithReplacements = Object.entries(replaceVariables).reduce(
      (url, [key, value]) => url.replace(new RegExp(`\\{${key}\\}`, 'g'), value),
      src
    );
    return urlWithReplacements;
  }, [src, replaceVariables]);

  // Validate that the URL is a relative path (or localhost in development)
  const isValidPath = useMemo(() => {
    // In development, allow localhost URLs for iframe content
    if (process.env.NODE_ENV === 'development' && resolvedSrc.startsWith('http://localhost:')) {
      return true;
    }
    return isRelativePath(resolvedSrc);
  }, [resolvedSrc]);

  // Debug logging
  console.log('[IFrameEmbed] src:', src, 'resolvedSrc:', resolvedSrc, 'isValidPath:', isValidPath);

  const handleLoad = () => {
    console.log('[IFrameEmbed] iframe loaded:', resolvedSrc);
    setIsLoading(false);
    onLoad?.();
  };

  const handleError = () => {
    setIsLoading(false);
    setHasError(true);
    onError?.();
  };

  // Show error if path validation fails
  if (!isValidPath) {
    return (
      <div className={`iframe-embed ${className}`} style={{ width, height, position: 'relative' }}>
        <div className="iframe-embed__error">
          <p>Invalid URL: Only relative paths are allowed</p>
          <p className="iframe-embed__error-url">{resolvedSrc}</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`iframe-embed ${className}`} style={{ width, height, position: 'relative' }}>
      {showSpinner && isLoading && (
        <div className="iframe-embed__spinner">
          <Spin size={spinnerSize} />
        </div>
      )}

      {hasError ? (
        <div className="iframe-embed__error">
          <p>Failed to load content</p>
        </div>
      ) : (
        <iframe
          src={resolvedSrc}
          title={title}
          style={{
            width: '100%',
            height: '100%',
            border: 'none',
            visibility: isLoading ? 'hidden' : 'visible',
            ...style,
          }}
          onLoad={handleLoad}
          onError={handleError}
          sandbox={sandbox}
          allow={allow}
          loading={loading}
        />
      )}
    </div>
  );
};

export default IFrameEmbed;
