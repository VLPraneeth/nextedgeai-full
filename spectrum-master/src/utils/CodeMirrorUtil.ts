export const getCodeMirrorOptions = (readOnly = false) => ({
  matchBrackets: true,
  lineWrapping: true,
  autoCloseBrackets: true,
  mode: 'javascript',
  readOnly,
  lineNumbers: true,
});
