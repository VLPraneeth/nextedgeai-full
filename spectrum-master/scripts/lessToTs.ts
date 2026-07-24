// eslint-disable-next-line import/order
import type * as Less from 'less';
const fs = require('fs');
const path = require('path');

const less = require('less');
const camelCase = require('lodash/camelCase');
const prettier = require('prettier');

// map of desired variable name to source file path
const INPUT_LESS_FILE_PATH_MAP = {
  colors: path.resolve('./src/styles/color.less'),
  variables: path.resolve('./src/styles/variables.less'),
};

// Where to output the generated file
const OUTPUT_FILE_PATH = path.resolve('./src/utils/LessConstants.ts');

/**
 * these types are completely made up by me while developing this. They are very incomplete and possibly incorrect given
 * specific circumstances and LESS variables not tested or encountered so far.
 */
type Rule = {
  name: string;
  value: Rule | string;

  toCSS?: (opts: Less.Options) => string;
};

type RuleSet = {
  selectors: null | string[] | string;
  rules: Rule[];
};

type LessNode = {
  name: string;
  allowRoot: boolean;
  inline: boolean;
  variable: boolean;

  toCSS: (opts: Less.Options) => string;

  value:
    | Rule
    | {
        ruleset?: RuleSet;
        toCSS?: (opts: Less.Options) => string;
      };
};

const lessToJson = (str = '', nameProjectionFunc?: (name: string) => string, options?: Less.Options) => {
  return new Promise<object>((resolve, reject) => {
    less.parse(str, options, (err: Error | null, root: any, _imports: any, lessOpts: Less.Options) => {
      if (err) {
        reject(err);
      }

      let evalEnv = new less.contexts.Eval(lessOpts);
      let evaldRoot = root.eval(evalEnv);
      let ruleset = evaldRoot.rules;

      // this handles extracting our CSS value from LESS maps
      const getValue = (value: Rule): string | number | unknown => {
        if (value.toCSS) {
          return value.toCSS(lessOpts);
        }

        if (Array.isArray(value.value)) {
          return getValue(value.value[0]);
        }

        if (typeof value.value === 'object') {
          return getValue(value.value);
        }

        return value.value || value.name || value;
      };

      resolve(
        ruleset
          .filter((node: LessNode) => node.variable === true)
          .reduce((prev: Record<string, unknown>, curr: LessNode) => {
            if (!curr?.value) {
              return prev;
            }

            const key = typeof nameProjectionFunc === 'function' ? nameProjectionFunc(curr.name) : curr.name;
            const css = curr.value.toCSS?.(lessOpts) || '';

            // if this was a simple LESS variable, we'll have our CSS here, add it to the map.
            if (css) {
              return {
                ...prev,
                [key]: css,
              };
            }
            // this is to satisfy TS given my made-up LESS types
            if (!('ruleset' in curr.value) || !curr.value.ruleset) {
              return prev;
            }

            // run over the rules and extract the name and value from the LESS map
            // for nesting in our varaibles output
            const values = curr.value.ruleset.rules
              .filter((rule: any) => rule.name)
              .reduce((acc: Record<string, string | number>, rule: any) => {
                const name = rule.name?.[0]?.value;

                return {
                  ...acc,
                  [name]: getValue(rule.value),
                };
              }, {} as Record<string, string | number>);

            return {
              ...prev,
              [key]: values,
            };
          }, {})
      );
    });
  });
};

const convertLessToJSON = async (filePath: string) => {
  const lessData = fs.readFileSync(filePath, 'utf-8');

  // converts the less data string from the file into camelCased json
  // we first slice off the first char from the var name ("@") so that we get clean keys
  // eg, "@syncari-blue" becomes "syncariBlue"
  return lessToJson(lessData, (name: string) => camelCase(name.slice(1)));
};

const main = async (outputPath: string, fileMap: Record<string, string> = INPUT_LESS_FILE_PATH_MAP) => {
  console.log('Starting conversion…');

  try {
    // creates a prettier instance to format our constants file
    const formatter = async (data: string) => {
      const filePath = await prettier.resolveConfigFile();

      if (!filePath) {
        throw new Error('Unable to find prettier config');
      }

      const config = await prettier.resolveConfig(filePath);
      return await prettier.format(data, { parser: 'babel-ts', ...config });
    };

    const lessFileKeys = Object.keys(fileMap);
    const lessData = await Promise.all(Object.values(fileMap).map(convertLessToJSON));

    // for each key/value in our LESS_FILE_PATHS map, we want to extract the variables from path
    // and store the result onto the named variable
    const variables = lessData.reduce((acc: string, data, idx) => {
      const key = lessFileKeys[idx];
      const json = JSON.stringify(data);
      console.log(`Converted ${key}`);

      // outputs a new variable with our json data, with `as const` appended for proper ts annotation as read-only
      return `${acc} export const ${key} = ${json} as const;\n\n`;
    }, '');

    const formatted = await formatter(variables);
    fs.writeFileSync(outputPath, formatted);

    console.log('Done.');
  } catch (err) {
    console.error(err);
  }
};

main(OUTPUT_FILE_PATH);
