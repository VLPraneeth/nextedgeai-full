import sortKeys from 'sort-keys';

const fs = require('fs');
const path = require('path');

// Cypress JSON files
const CYPRESS_JSON_FILES = ['fixtures/example.json', 'fixtures/profile.json', 'fixtures/users.json'].map((p) =>
  path.resolve(`./cypress/${p}`)
);

// i18n JSON files
const I18N_JSON_FILES = ['en-US.json'].map((p) => path.resolve(`./src/i18n/${p}`));

function formatFile(filePath: string) {
  try {
    console.log(`Formatting ${filePath}…`);
    const fileData = JSON.parse(fs.readFileSync(filePath, { encoding: 'utf-8', flag: 'r' }));
    fs.writeFileSync(filePath, JSON.stringify(sortKeys(fileData, { deep: true }), null, 2));
  } catch (err) {
    console.log(`Error formatting ${filePath}`, err);
  }
}

function main() {
  const jsonFiles = [...CYPRESS_JSON_FILES, ...I18N_JSON_FILES];
  console.log(`Formatting ${jsonFiles.length} JSON files…`);

  jsonFiles.forEach(formatFile);
  console.log('Done.');
}

main();
