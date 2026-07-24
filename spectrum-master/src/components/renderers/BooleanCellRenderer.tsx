import { ReactComponent as FalseIcon } from 'assets/icons/table-boolean-false.svg';
import { ReactComponent as TrueIcon } from 'assets/icons/table-boolean-true.svg';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import './BooleanCellRenderer.less';

const BooleanCellRenderer = (flag: boolean | null | undefined) => {
  if (flag === null || flag === undefined) {
    return <TranslatedText color="gray-1000" key="label" namespace="TableCellRenderers.BooleanCell" text="null" />;
  }
  if (typeof flag !== 'boolean') {
    return String(flag);
  }
  return (
    <HStack spacing="xxs">
      {flag
        ? [
            <TrueIcon className="syncari-boolean-cell" key="icon" />,
            <TranslatedText color="gray-1000" key="label" namespace="TableCellRenderers.BooleanCell" text="true" />,
          ]
        : [
            <FalseIcon className="syncari-boolean-cell" key="icon" />,
            <TranslatedText color="gray-1000" key="label" namespace="TableCellRenderers.BooleanCell" text="false" />,
          ]}
    </HStack>
  );
};

export default BooleanCellRenderer;
