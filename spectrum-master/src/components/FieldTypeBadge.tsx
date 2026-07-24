import Tooltip, { TooltipProps } from 'antd/lib/tooltip';
import cx from 'classnames';
import * as React from 'react';

import { ReactComponent as BooleanIcon } from 'assets/icons/field-datatype-boolean.svg';
import { ReactComponent as DateIcon } from 'assets/icons/field-datatype-date.svg';
import { ReactComponent as DatetimeIcon } from 'assets/icons/field-datatype-datetime.svg';
import { ReactComponent as DoubleIcon } from 'assets/icons/field-datatype-double.svg';
import { ReactComponent as EmailListIcon } from 'assets/icons/field-datatype-email-list.svg';
import { ReactComponent as FileLinkIcon } from 'assets/icons/field-datatype-filelink.svg';
import { ReactComponent as IdIcon } from 'assets/icons/field-datatype-id.svg';
import { ReactComponent as IntegerIcon } from 'assets/icons/field-datatype-integer.svg';
import { ReactComponent as ObjectIcon } from 'assets/icons/field-datatype-object.svg';
import { ReactComponent as PicklistIcon } from 'assets/icons/field-datatype-pick-list.svg';
import { ReactComponent as ReferenceIcon } from 'assets/icons/field-datatype-reference.svg';
import { ReactComponent as StringIcon } from 'assets/icons/field-datatype-string.svg';
import { ReactComponent as UrlIcon } from 'assets/icons/field-datatype-url.svg';
import { FieldDataType } from 'components/types';

import './FieldTypeBadge.less';

export const DataTypeIcons: Record<FieldDataType, React.FC<React.SVGProps<SVGSVGElement>>> = {
  string: StringIcon,
  boolean: BooleanIcon,
  complex: ObjectIcon,
  date: DateIcon,
  datetime: DatetimeIcon,
  double: DoubleIcon,
  filelink: FileLinkIcon,
  id: IdIcon,
  integer: IntegerIcon,
  list: EmailListIcon,
  object: ObjectIcon,
  password: StringIcon,
  picklist: PicklistIcon,
  polymorphicreference: ReferenceIcon,
  reference: ReferenceIcon,
  textarea: StringIcon,
  timestamp: DatetimeIcon,
  url: UrlIcon,
};

type FieldTypeBadgeSize = 'small' | 'medium';

export interface FieldTypeBadgeProps {
  className?: string;
  dataType: FieldDataType;
  description?: string;
  disableTooltip?: true;
  tooltipProps?: Partial<TooltipProps>;
  size?: FieldTypeBadgeSize;
  children?: React.ReactNode;
}

const EmptyTooltipProps: Partial<TooltipProps> = {};

const FieldTypeBadge = ({
  children,
  className,
  dataType,
  description: providedDescription,
  disableTooltip,
  tooltipProps: givenTooltipProps = EmptyTooltipProps,
  size = 'medium',
}: FieldTypeBadgeProps) => {
  const Icon = DataTypeIcons?.[dataType] || DataTypeIcons.string;
  const description = providedDescription || dataType;

  // Only set to bool if we want to disable (we don't allow explicit display at the moment).
  // Note: Setting undefined to visible doesn't seem to work. Need to not pass visible as props at all...
  const { className: tooltipClassName, ...tooltipProps }: Partial<TooltipProps> =
    disableTooltip === true ? { visible: false } : givenTooltipProps;

  return (
    <div className={cx('field-data-type', size, className)}>
      <Tooltip
        className="field-data-type-wrapper"
        title={description}
        overlayClassName={cx('field-data-type-description', tooltipClassName)}
        {...tooltipProps}>
        <span className="field-data-type-icon-wrapper">
          <Icon className="field-data-type-icon" role="img" aria-label={description} />
        </span>
        {children}
      </Tooltip>
    </div>
  );
};

export default FieldTypeBadge;
