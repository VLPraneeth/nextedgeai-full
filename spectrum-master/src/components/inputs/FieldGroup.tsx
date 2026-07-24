import Icon, { IconProps } from 'antd/lib/icon';
import Tooltip from 'antd/lib/tooltip';
import cx from 'classnames';
import * as React from 'react';
import { useUID } from 'react-uid';

import { HStack } from 'components/layout';
import { Text } from 'components/typography';

import './FieldGroup.less';

type LabelProps = JSX.IntrinsicElements['label'];
type ParagraphProps = JSX.IntrinsicElements['p'];

type PickedLabelProps = Pick<LabelProps, 'htmlFor' | 'id'>;
type PickedInputProps = Pick<JSX.IntrinsicElements['input'], 'id' | 'name' | 'aria-describedby' | 'aria-labelledby'>;
type PickedHelpTextProps = Pick<ParagraphProps, 'id'>;

export type UseFieldGroupParams = {
  id?: string;
  name?: string;
};

export type UseFieldGroupResult = {
  labelProps: Required<PickedLabelProps>;
  inputProps: Required<PickedInputProps>;
  helpTextProps: Required<PickedHelpTextProps>;
};

export const useFieldGroup = ({ id, name }: UseFieldGroupParams): UseFieldGroupResult => {
  const uid = useUID();
  const fieldId = id || name || uid;
  const descriptionId = `${fieldId}-description`;
  const labelId = `${fieldId}-label`;

  return {
    labelProps: {
      htmlFor: fieldId,
      id: labelId,
    },
    inputProps: {
      id: fieldId,
      name: fieldId,
      'aria-describedby': descriptionId,
      'aria-labelledby': labelId,
    },
    helpTextProps: {
      id: descriptionId,
    },
  };
};

export type FieldLabelProps = LabelProps & { required?: boolean };

export const FieldLabel = ({ children, className, required, ...props }: FieldLabelProps) => {
  return (
    <label className={cx('synri-label', className)} {...props}>
      {children}
      {required && (
        <Text color="red-500" weight="semibold">
          *
        </Text>
      )}
    </label>
  );
};

export type FieldLabelTooltipProps = {
  children: string;
  icon?: IconProps['type'];
  iconTheme?: IconProps['theme'];
};

export const FieldLabelTooltip = ({
  iconTheme = 'filled',
  icon = 'question-circle',
  children,
}: FieldLabelTooltipProps) => {
  return (
    <Tooltip title={children}>
      <Icon className="synri-label-tooltip-icon" theme={iconTheme} type={icon} />
    </Tooltip>
  );
};

export type FieldHelpTextProps = ParagraphProps;

export const FieldHelpText = ({ children, className, ...props }: FieldHelpTextProps) => {
  return (
    <p className={cx('synri-help-text', className)} {...props}>
      {children}
    </p>
  );
};

export type FieldGroupProps = {
  children: React.ReactElement;
  className?: string;
  helpText?: React.ReactNode;
  id?: string;
  label?: string | React.ReactNode;
  labelContainerClassName?: string;
  labelSiblings?: React.ReactNode | React.ReactNodeArray;
  name?: string;
  required?: boolean;
  tooltip?: string;
  tooltipIcon?: FieldLabelTooltipProps['icon'];
};

const FieldGroup = ({
  className,
  id: providedId,
  name: providedName,
  helpText,
  label,
  labelContainerClassName,
  labelSiblings,
  required,
  tooltip,
  tooltipIcon,
  children,
}: FieldGroupProps) => {
  const { labelProps: fieldGroupLabelProps, inputProps, helpTextProps } = useFieldGroup({
    id: providedId,
    name: providedName,
  });

  const labelProps = { ...fieldGroupLabelProps, required };

  const labelNode =
    typeof label === 'string'
      ? React.createElement(FieldLabel, labelProps, label)
      : React.isValidElement(label)
      ? React.cloneElement(label, labelProps)
      : null;

  const helpTextNode =
    typeof helpText === 'string'
      ? React.createElement(FieldHelpText, helpTextProps, helpText)
      : React.isValidElement(helpText)
      ? React.cloneElement(helpText, helpTextProps)
      : null;

  const child = React.Children.only(children) as React.ReactElement;

  return (
    <div className={cx('synri-field-group', className)}>
      {(label || labelSiblings) && (
        <HStack className={labelContainerClassName} spacing="z">
          {label && labelNode}
          {tooltip && <FieldLabelTooltip icon={tooltipIcon}>{tooltip}</FieldLabelTooltip>}
          {labelSiblings}
        </HStack>
      )}
      {React.cloneElement(child, inputProps)}
      {helpTextNode}
    </div>
  );
};

export default FieldGroup;
