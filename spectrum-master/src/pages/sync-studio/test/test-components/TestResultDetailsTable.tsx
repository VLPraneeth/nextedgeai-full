import { Icon, Tooltip } from 'antd';
import Text from 'antd/lib/typography/Text';
import cx from 'classnames';
import { differenceWith, intersectionWith, isEmpty, keyBy, map } from 'lodash';

import FieldTypeBadge from 'components/FieldTypeBadge';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import CenterLayout from 'components/layout/CenterLayout';
import ModalTable, { TBody, TD, TH, THead, TR } from 'components/ModalTable';
import { FieldDataType } from 'components/types';
import { IndividualNodeResult, TestDataModel } from 'store/test/types';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('TestResultDetails');

interface TestResultTableRow {
  apiName: string;
  dataType: FieldDataType;
  hasInputField: boolean;
  inputDisplayName?: string;
  inputValue?: string;
  hasResultField: boolean;
  showError?: boolean;
  resultDisplayName?: string;
  expectedValue?: string;
  resultValue?: string;
}

const getInputOutputCombinedRows = ({ input, expectedResult, actualResult }: TestDataModel) => {
  const inputApiNames = map(input, 'apiName');
  const resultApiNames = map(actualResult, 'apiName');

  const inputObject = keyBy(input, 'apiName');
  const actualResultObject = keyBy(actualResult, 'apiName');

  const commonApiNames = intersectionWith(inputApiNames, resultApiNames);
  const inputOnlyApiNames = differenceWith(inputApiNames, commonApiNames);
  const resultOnlyApiNames = differenceWith(resultApiNames, commonApiNames);

  const rows: TestResultTableRow[] = [];

  commonApiNames.forEach((apiName) => {
    const inputRow = inputObject[apiName];
    const resultRow = actualResultObject[apiName];

    const row: TestResultTableRow = {
      apiName,
      dataType: inputRow.dataType,
      hasInputField: true,
      inputDisplayName: inputRow.displayName,
      inputValue: inputRow.value?.toString(),
      showError: !!(resultRow.failed && resultRow.expectedValue),
      hasResultField: true,
      resultDisplayName: resultRow.displayName,
      expectedValue: resultRow.expectedValue,
      resultValue: resultRow.value?.toString(),
    };

    rows.push(row);
  });

  inputOnlyApiNames.forEach((apiName) => {
    const inputRow = inputObject[apiName];

    const row: TestResultTableRow = {
      apiName,
      dataType: inputRow.dataType,
      hasInputField: true,
      inputDisplayName: inputRow.displayName,
      inputValue: inputRow.value?.toString(),
      hasResultField: false,
    };

    rows.push(row);
  });

  resultOnlyApiNames.forEach((apiName) => {
    const resultRow = actualResultObject[apiName];

    const row: TestResultTableRow = {
      apiName,
      dataType: resultRow.dataType,
      hasInputField: false,
      resultDisplayName: resultRow.displayName,
      resultValue: resultRow.value?.toString(),
      hasResultField: true,
    };

    rows.push(row);
  });

  return rows;
};

interface Props {
  selectedTestNodeResult: IndividualNodeResult;
}

const TestResultDetailsTable = ({ selectedTestNodeResult }: Props) => {
  const inputOutputRows = getInputOutputCombinedRows(selectedTestNodeResult.testData);

  if (isEmpty(inputOutputRows)) {
    return (
      <CenterLayout>
        <Text>{tn('no_changes_found')}</Text>
      </CenterLayout>
    );
  }

  const inputDisplayNameField = (row: TestResultTableRow) => {
    if (!row.hasInputField) {
      return <TD className="synri-test-field-empty" />;
    }

    return (
      <TD className="synri-test-field-displayname">
        <div className="synri-field-name">
          <FieldTypeBadge className="synri-connector-field-badge" dataType={row.dataType} description={row.dataType} />
          <Tooltip title={row.apiName}>
            <div className="synri-field-display-name">{row.inputDisplayName}</div>
          </Tooltip>
        </div>
      </TD>
    );
  };

  const inputValueField = (row: TestResultTableRow) => {
    if (!row.hasInputField) {
      return <TD className="synri-test-field-empty" />;
    }

    return <TD>{row.inputValue}</TD>;
  };

  const resultDisplayNameField = (row: TestResultTableRow) => {
    if (!row.hasResultField) {
      return <TD className="synri-test-field-empty" />;
    }

    return (
      <TD className="synri-test-field-displayname">
        <div className="synri-field-name">
          <FieldTypeBadge className="synri-connector-field-badge" dataType={row.dataType} description={row.dataType} />
          <Tooltip title={row.apiName}>
            <div className="synri-field-display-name">{row.resultDisplayName}</div>
          </Tooltip>
          {row.showError && <Icon type="exclamation-circle" theme="filled" />}
        </div>
      </TD>
    );
  };

  const resultValueField = (row: TestResultTableRow) => {
    if (!row.hasResultField) {
      return <TD className="synri-test-field-empty" />;
    }

    if (row.showError) {
      return (
        <TD>
          <InlineMessage
            type={InlineMessageTypes.ERROR}
            title={tn('error_unexpected_result', {
              expectedValue: row.expectedValue,
              value: row.resultValue,
            })}>
            {tn('error_unexpected_result', {
              expectedValue: row.expectedValue,
              value: row.resultValue,
            })}
          </InlineMessage>
        </TD>
      );
    }

    return <TD>{row.resultValue}</TD>;
  };

  return (
    <div className="synri-test-result-table-wrapper">
      <ModalTable flex={false}>
        <THead>
          <TR>
            <TH className="synri-test-field-header-displayname">{tn('input')}</TH>
            <TH className="synri-test-field-header-displayname" />
            <TH className="synri-test-field-header-displayname">{tn('output')}</TH>
            <TH className="synri-test-field-header-displayname" />
          </TR>
        </THead>
        <TBody>
          {inputOutputRows.map((row) => {
            return (
              <TR className={cx(row.showError && 'synri-field-value-error')} key={row.apiName}>
                {inputDisplayNameField(row)}
                {inputValueField(row)}
                {resultDisplayNameField(row)}
                {resultValueField(row)}
              </TR>
            );
          })}
        </TBody>
      </ModalTable>
    </div>
  );
};

export default TestResultDetailsTable;
