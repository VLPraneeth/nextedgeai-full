import cx from 'classnames';
import * as React from 'react';

import { useI18nContext } from 'components/I18nProvider';
import { tNamespaced, I18nOptions, NamespaceKey } from 'utils/i18nUtil';

import type { ElementType, LineHeight, FontSize, FontWeight, TextColor } from './types';

import './Text.less';

type BaseTextProps = {
  as?: ElementType;
  className?: string;
  color?: TextColor;
  lineHeight?: LineHeight;
  size?: FontSize;
  weight?: FontWeight;
  style?: React.CSSProperties;
  noWrap?: boolean;
  breakAll?: boolean;
  breakWord?: boolean;
  htmlFor?: string;
};

type SafeText = BaseTextProps & {
  children: string | React.ReactNode | (React.ReactNode | string | false)[];
  beDangerous?: never | false;
};

type DangerousText = BaseTextProps & {
  children: string;
  beDangerous: true;
};

export type TextProps = SafeText | DangerousText;

type TooltipTriggerEvents = Pick<
  JSX.IntrinsicElements['span'],
  'onMouseLeave' | 'onMouseEnter' | 'onClick' | 'onFocus'
>;

const Text = ({
  as: Element = 'span',
  children,
  className,
  color,
  lineHeight = 'regular',
  size = 'md',
  style,
  weight = 'regular',
  beDangerous,
  noWrap = false,
  breakAll = false,
  breakWord = false,
  ...events
}: TextProps & TooltipTriggerEvents) => {
  const props = {
    className: cx(
      'syncari-text',
      lineHeight && `line-height-${lineHeight}`,
      size && `font-size-${size}`,
      weight && `font-weight-${weight}`,
      color && `color-${color}`,
      noWrap && `no-wrap`,
      breakAll && 'break-all',
      breakWord && 'break-word',
      className
    ),
    style,
    ...events,
  };

  if (beDangerous) {
    return React.createElement(Element, {
      ...props,
      dangerouslySetInnerHTML: {
        __html: children,
      },
    });
  }

  return React.createElement(Element, props, children);
};

export interface TranslatedTextProps extends Omit<TextProps, 'children'> {
  namespace?: NamespaceKey;
  args?: I18nOptions;
  // TODO: figure out typing so that text is validated for the namespace?
  text: string;
}

export const TranslatedText = ({ namespace, args, text, ...props }: TranslatedTextProps) => {
  const { tn } = useI18nContext();
  const translator = namespace ? tNamespaced(namespace) : tn;

  return <Text {...props}>{translator(text, args)}</Text>;
};

export default Text;
