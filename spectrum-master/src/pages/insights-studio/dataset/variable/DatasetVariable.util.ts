/**
 * Check if the given value is variable
 * @param value to check if its values
 * @returns boolean true if a variable otherwise false
 */
export const isValueVariable = (value: string) => /^\{\{.+}}$/.test(value);
