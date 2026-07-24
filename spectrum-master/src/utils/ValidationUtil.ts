//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

// import * as yAst from '@overgear/yup-ast';
import { transformAll } from '@demvsystems/yup-ast';
import produce from 'immer';

import { tNamespaced } from 'utils/i18nUtil';
import { replaceToken } from 'utils/StringUtil';

const tn = tNamespaced('ValidationUtil');

// const { transformAll } = yAst;

/**
 * Different validation status that can be set in ant input
 */
export const VALIDATION_STATUS = {
  ERROR: 'error',
  SUCCESS: '',
};

export interface ValidationMetadata {
  /**
   * yup ast multi dementional array of string. It can be more complex but only supporting multi dimentional
   * array of strings for now.
   * See: https://github.com/WASD-Team/yup-ast
   */
  yup?: string[][];

  /**
   * Check required value
   */
  required?: boolean;

  /**
   * Custom message to throw if the validation failed.
   */
  message?: string | undefined;

  /**
   * Label of the input that we are trying to validate.
   */
  label?: string;

  /**
   * keys that will be replaced to the yup ast validation template
   */
  [key: string]: boolean | string | number | undefined | string[][];
}

export const VALIDATION_KEYS = {
  REQUIRED: 'REQUIRED',
};

function getValidationTemplate(key: string, params: ValidationMetadata): string | undefined {
  const VALIDATION_TEMPLATE: { [key: string]: string } = {
    // This is pretty limited compare to what yup can do. We'll expose the full capability of yup overtime.
    REQUIRED: `[["yup.mixed"], ["yup.required", "${params?.message}"]]`,
  };

  if (VALIDATION_TEMPLATE[key]) {
    return replaceToken(VALIDATION_TEMPLATE[key], params);
  }
}

/**
 * Validate the value with the given validation metadata.
 *
 * @param vMeta ValidationMetadata metadata on validating the value.
 * @param value value that we will be validating.
 */
export function validate(vMeta: ValidationMetadata, value?: any): boolean {
  let yupAst: string | undefined;
  if (vMeta.required) {
    if (!vMeta.message && vMeta.label) {
      vMeta = produce(vMeta, (draftVMeta) => {
        draftVMeta.message = tn('required_message', vMeta);
      });
    }
    yupAst = getValidationTemplate(VALIDATION_KEYS.REQUIRED, vMeta);
  }
  if (yupAst) {
    const result = transformAll(JSON.parse(yupAst));
    result.validateSync(value);
  } else if (vMeta.yup) {
    const result = transformAll(vMeta.yup);
    result.validateSync(value);
  }
  return true;
}
