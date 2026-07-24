import { variables } from 'utils/LessConstants';
import { KeysOf } from 'utils/TypeUtils';

export type ElementType = 'span' | 'div' | 'p' | 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'label';
export type LineHeight = KeysOf<typeof variables.lineHeights>;
export type FontSize = KeysOf<typeof variables.fontSizes>;
export type FontWeight = KeysOf<typeof variables.fontWeights>;
export type TextColor =
  | 'black'
  | 'white'
  | 'light-gray'
  | 'gray-100'
  | 'gray-200'
  | 'gray-300'
  | 'gray-400'
  | 'gray-500'
  | 'gray-600'
  | 'gray-700'
  | 'gray-750'
  | 'gray-800'
  | 'gray-850'
  | 'gray-900'
  | 'gray-1000'
  | 'red-100'
  | 'red-200'
  | 'red-300'
  | 'red-500'
  | 'orange-700'
  | 'green-300'
  | 'blue-600'
  | 'syncari-blue';
