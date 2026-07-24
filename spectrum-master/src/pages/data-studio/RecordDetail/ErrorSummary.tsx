import Icon from 'antd/lib/icon';
import Modal from 'antd/lib/modal';

import { ReactComponent as ChevronRight } from 'assets/icons/chevron-right.svg';
import I18nProvider, { useI18nContext } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import ModalTable, { THead, TBody, TH, TD, TR } from 'components/ModalTable';
import { Text } from 'components/typography';
import { TranslatedText } from 'components/typography';

export interface ErrorSummaryProps {
  errors: Record<string, string>;
}

const ErrorSummary = ({ errors }: ErrorSummaryProps) => {
  const { tn } = useI18nContext();
  const errorsCount = Object.keys(errors).length;

  const onRequestShowModal = () => {
    Modal.error({
      className: 'error-summary-modal',
      title: <Text>{tn('update_validation_errors', { count: errorsCount })}</Text>,
      onCancel: () => Promise.resolve(),
      onOk: () => Promise.resolve(),
      content: (
        <I18nProvider namespace="DataStudio">
          <Stack>
            <TranslatedText as="div" text="errors_summary_modal_content" args={{ count: errorsCount }} />

            <ModalTable>
              <THead>
                <TR>
                  <TH>
                    <TranslatedText text="field" />
                  </TH>
                  <TH>
                    <TranslatedText text="error" />
                  </TH>
                </TR>
              </THead>
              <TBody>
                {Object.entries(errors).map(([field, error]) => (
                  <TR key={field}>
                    <TD className="error-summary-field-name">{field}</TD>
                    <TD>{error}</TD>
                  </TR>
                ))}
              </TBody>
            </ModalTable>
          </Stack>
        </I18nProvider>
      ),
    });
  };

  return (
    <button type="button" className="errors-summary-link" onClick={onRequestShowModal}>
      <HStack className="errors-summary" spacing="xs">
        <Icon type="exclamation-circle" />
        <TranslatedText text="update_validation_errors" args={{ count: errorsCount }} />
        <span className="errors-summary-expansion-chevron">
          <ChevronRight />
        </span>
      </HStack>
    </button>
  );
};

export default ErrorSummary;
