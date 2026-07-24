// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import ModalTable, { THead, TBody, TH, TR, TD } from 'components/ModalTable';

const fieldStyle = { width: 'calc(20%)' };
const oldValueHeaderStyle = { width: 'calc(30% - 0.5rem)' };
const oldValueStyle = { width: 'calc(30%)' };
const newValueStyle = { flexGrow: 1 };

function CreateUpdateTransactionChanges({ dataSource }) {
  return (
    <div className="transactions-changes-expanded-row">
      <ModalTable flex className="transactions-changes-table">
        <THead>
          <TR>
            <TH style={fieldStyle}>Field</TH>
            <TH style={oldValueHeaderStyle}>Old Value</TH>
            <TH style={newValueStyle}>New Value</TH>
          </TR>
        </THead>
        <TBody>
          {dataSource.map((ch) => (
            <TR key={`transaction-change-${ch.fieldId}`}>
              <TD style={fieldStyle} className="transaction-change-field-name">
                {ch.apiName}
              </TD>
              <TD style={oldValueStyle}>{ch.oldValue?.toString() || ''}</TD>
              <TD style={newValueStyle}>{ch.newValue?.toString() || ''}</TD>
            </TR>
          ))}
        </TBody>
      </ModalTable>
    </div>
  );
}

export default CreateUpdateTransactionChanges;
