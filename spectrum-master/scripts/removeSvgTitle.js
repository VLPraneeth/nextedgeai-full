//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
const execSync = require('child_process').execSync;
const fs = require('fs');

const _ = require('lodash');

function removeTitle(path) {
  let svgs = fs.readdirSync(path);
  _.each(svgs, (fileName) => {
    if (!fileName.match(/.*svg$/)) {
      return;
    }
    const tempPath = `${path}/${fileName}-temp.svg`;
    const fullPath = `${path}/${fileName}`;
    fs.renameSync(fullPath, tempPath);
    const cmd = `sed -e 's/<title>.*<\\/title>//g' ${tempPath} > ${fullPath}`;
    execSync(cmd);
    fs.unlinkSync(tempPath);
  });
}

if (process.argv.length >= 3) {
  const path = process.argv[2];
  console.log(`Removing the title tag of svgs from directory: ${path}`);
  removeTitle(path);
} else {
  console.log(`Usage: node removeSvgTitle.js <directory>`);
}
