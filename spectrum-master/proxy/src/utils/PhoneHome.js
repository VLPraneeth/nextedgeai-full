import fs from 'fs';

import { SourceMapConsumer } from 'source-map';

import { getConfigVariables } from 'utils/ConfigUtil';
import {
  append,
  compose,
  filter,
  getMatchResult,
  join,
  map,
  match,
  prepend,
  prop,
  split,
  trim,
  unique,
} from 'utils/Fp';
import { base64Decode } from 'utils/StringUtil';

const { artifactsRoot } = getConfigVariables();

const chunkRegex = /.*\(?https?:\/\/.*(\/static\/js\/.*\.chunk\.js:\d+:\d+)\)?/;

/** given a base64 stack trace, this will extract
 * { file, line, column } for each frame in the stack
 */
const getFramesFlow = compose(
  map(([file, line, column]) => ({
    file: prepend(artifactsRoot)(file),
    line: parseInt(line, 10),
    column: parseInt(column, 10),
  })),
  map(split(':')),
  map(getMatchResult),
  filter(Boolean),
  map(match(chunkRegex)),
  map(trim),
  split('\\n'),
  base64Decode
);

// extracts original position for a frame
const getOriginal = ({ file, line, column }) =>
  new Promise((resolve, reject) => {
    try {
      const fileData = JSON.parse(Buffer.from(fs.readFileSync(append('.map')(file), 'utf-8')));
      SourceMapConsumer.with(fileData, null, (consumer) => {
        resolve(consumer.originalPositionFor({ line, column }));
      });
    } catch (err) {
      reject(err);
    }
  });

/** get original frames from minified frames
 */
const getOriginalFrames = (frames) => Promise.all(map(getOriginal)(frames));

/*
 * feed this the b64 stack trace and it will return a unique
 * list of source maps that are needed.
 *
 */
const getFilesListFlow = compose(unique, map(prop('file')), getFramesFlow);

// return a new string comprised of the original frame data
const getOriginalStackString = compose(
  join('\n'),
  map((frame) => `${frame.name} at ${frame.source}:${frame.line}:${frame.column}`)
);

module.exports = {
  getFilesListFlow,
  getFramesFlow,
  getOriginalFrames,
  getOriginalStackString,
};
