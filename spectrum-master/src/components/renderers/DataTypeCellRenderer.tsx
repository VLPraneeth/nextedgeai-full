import FieldTypeBadge from 'components/FieldTypeBadge';
import { FieldDataType } from 'components/types';
import { tNamespaced } from 'utils/i18nUtil';

import './DataTypeCell.less';

const tn = tNamespaced('DataTypes');

interface DateTypeCellProps {
  datatype: FieldDataType;
}

const DataTypeCell = ({ datatype }: DateTypeCellProps) => {
  const datatypeString = tn(datatype) as string;

  return (
    <div className="schema-studio-data-type-cell">
      <FieldTypeBadge dataType={datatype} description={datatypeString} disableTooltip />
      <span>{datatypeString}</span>
    </div>
  );
};

const DataTypeCellRenderer = (datatype: FieldDataType) => <DataTypeCell datatype={datatype} />;

export default DataTypeCellRenderer;
