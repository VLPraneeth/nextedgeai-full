//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
const execSync = require('child_process').execSync;
const fs = require('fs');

const _ = require('lodash');

const functionPath = 'public/assets/icons/functions';
const newFunctionFill = '#B9E1E0';

const actionPath = 'public/assets/icons/actions';
const newActionFill = '#A9A1DA';

// replace the fill of all the svgs and save it to the same file
function replaceFill(path, fillColor) {
  const svgs = fs.readdirSync(path);
  _.each(svgs, (fileName) => {
    if (!fileName.match(/.*svg$/)) {
      return;
    }
    const fileBn = baseName(fileName);
    const fileDir = path;
    const fullFilePath = `${fileDir}/${fileName}`;
    // replace the fills of the file
    const cmd = `sed 's/fill="#[^"]*"/fill="${fillColor}"/g' ${fullFilePath} > ${fileDir}/${fileBn}-temp.svg`;
    execSync(cmd);
    fs.renameSync(`${fileDir}/${fileBn}-temp.svg`, `${fileDir}/${fileBn}.svg`);
  });
}

function baseName(path) {
  let base = path.substring(path.lastIndexOf('/') + 1);
  if (base.lastIndexOf('.') !== -1) {
    base = base.substring(0, base.lastIndexOf('.'));
  }
  return base;
}

replaceFill(functionPath, newFunctionFill);
replaceFill(actionPath, newActionFill);
