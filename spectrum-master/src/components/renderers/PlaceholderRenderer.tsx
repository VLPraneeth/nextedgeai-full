import DateCellRenderer from 'components/renderers/DateCellRenderer';
import { colors } from 'utils/LessConstants';

export const PlaceholderRenderer = (value: string | undefined, placeholder: string = '-', isDate: boolean = false) => {
  if (value && isDate) {
    return DateCellRenderer(value as any);
  }

  return <span style={!value ? { color: colors.gray600 } : undefined}>{value ?? placeholder}</span>;
};
