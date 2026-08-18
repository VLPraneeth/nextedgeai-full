import type { SVGProps } from 'react';

import { ReactComponent as Lockup } from 'assets/brand/nextedge-lockup.svg';
import { ReactComponent as LockupReverse } from 'assets/brand/nextedge-lockup-reverse.svg';
import { ReactComponent as Mark } from 'assets/brand/nextedge-mark.svg';
import { ReactComponent as MarkReverse } from 'assets/brand/nextedge-mark-reverse.svg';

type BrandLogoProps = SVGProps<SVGSVGElement> & {
  variant?: 'lockup' | 'mark';
  reverse?: boolean;
};

const BrandLogo = ({ variant = 'lockup', reverse = false, ...props }: BrandLogoProps) => {
  const Logo = variant === 'mark' ? (reverse ? MarkReverse : Mark) : reverse ? LockupReverse : Lockup;

  return <Logo role="img" aria-label="NextEdge AI" focusable="false" {...props} />;
};

export default BrandLogo;
