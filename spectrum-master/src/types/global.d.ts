declare type SyncariID = string;

declare module '*.svg' {
  import React = require('react');
  export const ReactComponent: React.FC<React.SVGProps<SVGSVGElement>>;
  const src: string;
  export default src;
}

interface Array<T> {
  // Support array concat union type
  concat<U>(...items: (U | ConcatArray<U>)[]): (T | U)[];
}

declare module '*.png' {
  const content: string;
  export default content;
}

declare module 'sg6-editor';
