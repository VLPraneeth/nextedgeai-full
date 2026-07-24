import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { CSSProperties } from 'react';

import './Markdown.scss';

export const Markdown = ({ children, style }: { children?: string; style?: CSSProperties | undefined }) => {
  return (
    <div
      className="markdown-view"
      style={style}
      dangerouslySetInnerHTML={{
        __html: DOMPurify.sanitize(marked.parse(children || '', { async: false }) as string),
      }}
    />
  );
};
