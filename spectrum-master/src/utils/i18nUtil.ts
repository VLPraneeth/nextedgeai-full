//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import i18n from 'i18next';

import { GetDictValue, Leaves, ObjectPaths } from 'utils/TypeUtils';

import en_US from '../i18n/en-US.json';

// TODO: Add more locales and make "Region" support
export enum Locale {
  EN_US = 'en-US',
}

export enum LocaleStyle {
  DECIMAL = 'decimal',
  PERCENT = 'percent',
  CURRENCY = 'currency',
}

export interface InterpolationOptions {
  escapeValue: boolean;
}

export type I18nOptionValue = string | number | boolean | undefined | string[][] | InterpolationOptions;
export type I18nOptions = Record<string, I18nOptionValue>;

/**
 * these are the keys of all slices/objects for the i18n config
 * {
 *   "DataStudio": {
 *     "Modal": {
 *       "save": "Save Item"
 *     },
 *     "title": "Data Studio"
 *   }
 * }
 *
 * NamespaceKey = "DataStudio" | "DataStudio.Modal";
 */
export type NamespaceKey = ObjectPaths<typeof en_US>;

/**
 * this is all the possible leaf paths, in their entirety, for a translation item
 *
 * {
 *   "DataStudio": {
 *     "Modal": {
 *       "save": "Save Item"
 *     },
 *     "title": "Data Studio"
 *   }
 * }
 *
 * TranslationKeyPath = "DataStudio.Modal.save" | "DataStudio.title";
 */
export type TranslationKeyPath = Leaves<typeof en_US>;

/**
 * Wrapper function for the i18n module
 */
export const t = <T extends TranslationKeyPath>(key: T, options?: I18nOptions): GetDictValue<T, typeof en_US> => {
  // Throw an error during development and testing to avoid passing strings that
  // are not translated
  if (process.env.NODE_ENV !== 'production') {
    if (!i18n.exists(key, options)) {
      throw new Error(`Invalid i18n path for: ${key}`);
    }
  }

  return i18n.t(key, options);
};

/**
 * Create a t with a specific namespace
 * @param {String} namespace i18n namespace
 */
export function tNamespaced(namespace: NamespaceKey) {
  return function (key: string, options?: I18nOptions): string {
    const nKey = `${namespace}.${key}` as TranslationKeyPath;
    return t(nKey, options) || nKey;
  };
}

/**
 * Like tNamespaced but doesn't throw if value not found
 * @param {String} namespace i18n namespace
 */
export function tNamespacedOptional(namespace: NamespaceKey) {
  return function (key: string, options?: I18nOptions): string {
    const nKey = `${namespace}.${key}` as TranslationKeyPath;
    try {
      return t(nKey, options);
    } catch (error) {
      return nKey;
    }
  };
}

/**
 * Namespaced t common
 * @param {String} key i18n string key
 */
export const tCommon = tNamespaced('Common');

/**
 * Alias for the t common namespace
 */
export const tc = tCommon;

/**
 * Initialize the i18n resources
 * We will default it to en-US to start
 */
export function init() {
  i18n.init({
    lng: 'en',
    resources: {
      en: {
        translation: en_US,
      },
    },
  });
}

/**
 * Locale formatted number.
 *
 * @param num number to format
 * @param locale Locale to use
 * @param localeStyle Local number style
 * @returns formatted number or the same num value if its not a valid number.
 */
export const numberFormat = (
  num?: string | number,
  locale: Locale = Locale.EN_US,
  localeStyle: LocaleStyle = LocaleStyle.DECIMAL,
  maximumFractionDigits?: number
) =>
  String(
    Intl?.NumberFormat
      ? !isNaN(Number(num))
        ? new Intl.NumberFormat(locale, { style: localeStyle, maximumFractionDigits }).format(Number(num))
        : num || 0
      : num
  );
