//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { TEST_STATUS, doesTestStatusHaveResult } from '../Test.util';

describe('Test.util', () => {
  // prettier-ignore
  test.each`
    status                    | expectedStatus
    ${TEST_STATUS.COMPLETED}  | ${true}
    ${TEST_STATUS.SUCCESS}    | ${true}
    ${TEST_STATUS.FAILED}     | ${true}
    ${TEST_STATUS.ERROR}      | ${true}
    ${TEST_STATUS.QUEUED}     | ${false}
    ${TEST_STATUS.PENDING}    | ${false}
    ${TEST_STATUS.RUNNING}    | ${false}
    ${TEST_STATUS.ABORTED}    | ${false}
    ${undefined}              | ${false}
    ${null}                   | ${false}
    `('doesTestStatusHaveResult returns $expectedStatus when status is $status', ({status, expectedStatus}) => {
      expect(doesTestStatusHaveResult(status)).toBe(expectedStatus);
    })
});
