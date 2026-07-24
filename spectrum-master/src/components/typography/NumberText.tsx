//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { numberFormat } from 'utils/i18nUtil';

import Text, { TextProps } from './Text';

export interface NumberTextProps extends Omit<TextProps, 'children'> {
  /**
   * Children of the NumberText that will be number locale formatted.
   */
  children?: string | number;
}

export const NumberText = ({ children, ...props }: NumberTextProps) => <Text {...props}>{numberFormat(children)}</Text>;

export default NumberText;
