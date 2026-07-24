export const alphaNumericRegEx = /[^0-9a-zA-Z\s]+/gi;

export const integerRegEx = /^\d*$/;

export const apiNameRegEx = /[^a-zA-Z0-9\d_-]/;

// Legacy employee-only behavior is disabled in the white-label build.
export const syncariEmailRegEx = /$a/;

// Alpha numberic plus dashes, underscores, and periods
export const validFileNameRegEx = /[^0-9a-zA-Z-_\.\s]+/gi;

// Matchches any opening or closing HTML tag
// NOTE: Don't use this for user input sanitation! It's not bulletproof!
export const HTML_TAGS_REGEX = /<\/?[^>]+(>|$)/g;

// Regex for control and zero width no-break space (BOM) characters
// eslint-disable-next-line no-control-regex
export const NON_PRINTABLE_REGEX = /[\u0000-\u001F\u007F-\u009F\uFEFF]/g;

export const NO_WHITESPACE_REGEX = /^\S*$/;

export const HTTP_URL = /^https?:\/\/(?:www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&\/=]*)$/;

// This is used for the ShowWhiteSpaceChars component to show extra white space
// in the middle of a string. This looks for spaces before and after commas and
// any duplicate white spaces.
export const SHOW_WHITESPACE_REGEX = /(\s,)|(,\s)|(\s{2,})/;

export const DOMAIN_REGEX = /^([a-zA-Z0-9_\-\.]+)\.([a-zA-Z]{2,})$/;
// Simply verifies that the string starts with a forward slash
export const RELATIVE_URL = /^\//;

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// The regex below describes a string of the form:
// {string w/o whitespace}@{string w/o whitespace}.{string w/o whitespace}
export const LOOSE_EMAIL_REGEX = /^\S+@\S+\.\S+$/;

export const ALPHA_NUMERIC_REGEX = /^\w+$/;
