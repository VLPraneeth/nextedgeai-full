/**
 * Parse JSON with error handling
 * @param inputString
 * @returns
 */
export function parseJSON(inputString: string) {
  let parsedJSON: string | JSON;

  try {
    parsedJSON = JSON.parse(inputString);
  } catch (error) {
    parsedJSON = inputString;
  }

  return parsedJSON;
}
