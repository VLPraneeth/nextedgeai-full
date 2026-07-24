import CodeMirror, { Editor, Hints, Position, ShowHintOptions } from 'codemirror';

import { DataSource, DataSourceFields, DatasetVariable } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

type Match = {
  type: string;
  pos: Position;
};

export const VARIABLE_PREFIX = '{{';
export const VARIABLE_SUFFIX = '}}';
export const CREATE_NEW_VARIABLE = 'CREATE_NEW_VARIABLE';
export const TABLE_HINTS_WITH_SCHEMA = ['FROM', 'JOIN'];
export const TABLE_HINTS_WITHOUT_SCHEMA = ['SELECT', 'ON', 'BY', 'WHERE'];
export const EDITOR_HEIGHT_BUFFER = 20;

export interface ExtendedHint extends CodeMirror.Hint {
  pick?: () => void;
}

export const highlightVariables = (editor: Editor) => {
  const regex = /\{\{|\}\}/g;
  const doc = editor.getDoc();

  doc.getAllMarks().forEach((mark) => {
    mark.clear();
  });

  let matches: Match[] = [];
  let match;
  while ((match = regex.exec(doc.getValue()))) {
    matches.push({
      type: match[0] === VARIABLE_PREFIX ? 'open' : 'close',
      pos: doc.posFromIndex(match.index),
    });
  }

  let openBrackets: Match[] = [];
  matches.forEach((match) => {
    if (match.type === 'open') {
      openBrackets.push(match);
    } else if (openBrackets.length > 0) {
      const openBracket = openBrackets.pop();
      const closeBracket = match;
      if (openBracket?.pos) {
        doc.markText(
          openBracket.pos,
          { line: openBracket.pos.line, ch: openBracket.pos.ch + 2 },
          { className: 'cm-variable-bracket' }
        );
        doc.markText(
          { line: openBracket.pos.line, ch: openBracket.pos.ch + 2 },
          { line: closeBracket.pos.line, ch: closeBracket.pos.ch },
          { className: 'cm-variable-name' }
        );
        doc.markText(
          closeBracket.pos,
          { line: closeBracket.pos.line, ch: closeBracket.pos.ch + 2 },
          { className: 'cm-variable-bracket' }
        );
      }
    } else {
      doc.markText(match.pos, { line: match.pos.line, ch: match.pos.ch + 2 }, { className: 'cm-unmatched-bracket' });
    }
  });

  openBrackets.forEach((openBracket) => {
    doc.markText(
      openBracket.pos,
      { line: openBracket.pos.line, ch: openBracket.pos.ch + 2 },
      { className: 'cm-unmatched-bracket' }
    );
  });
};

export const sqlHint = async (
  editor: Editor,
  options: ShowHintOptions,
  schema: string | undefined,
  variables: DatasetVariable[] | undefined,
  dataSources: DataSource[] | undefined,
  fetchDatasourceFields: (datasource: DataSource) => Promise<DataSourceFields[] | undefined>
): Promise<Hints> => {
  const cursor = editor.getCursor();
  const token = editor.getTokenAt(cursor);
  const line = editor.getLine(cursor.line);
  const start = token.start;
  const end = cursor.ch;
  const currentWord = token.string || '';
  const currentLineUpToCursor = line.substring(0, end);

  // Show variable suggestions
  if (variables?.length && currentLineUpToCursor.endsWith(VARIABLE_PREFIX)) {
    const variableHints: (string | CodeMirror.Hint)[] = (variables || []).map((variable) => ({
      text: variable.apiName || '',
      displayText: variable.apiName || '',
    })) || [
      {
        text: '',
        displayText: '',
      },
    ];
    variableHints.push({
      text: CREATE_NEW_VARIABLE,
      displayText: tn('AdvanceDataset.create_new_variable'),
      className: 'create-new-variable-option',
      render: (el) => {
        const button = document.createElement('button');
        button.className = 'create-new-variable-button';
        button.type = 'button';
        button.innerText = tn('AdvanceDataset.create_new_variable');
        el.appendChild(button);
      },
    } as ExtendedHint);

    return {
      list: variableHints,
      from: CodeMirror.Pos(cursor.line, start + 1),
      to: CodeMirror.Pos(cursor.line, end),
    };
  }

  // Show fields(columns) suggestions
  const currentWordWithDot = currentLineUpToCursor.split(' ').at(-1) || '';
  if (currentWordWithDot.includes('.')) {
    const [tableName, columnName] = currentWordWithDot.split('.');

    const dataSource = dataSources?.find((ds) => ds.apiName === tableName);
    if (dataSource) {
      const dataSourceFields = await fetchDatasourceFields(dataSource);

      const columns =
        dataSourceFields
          ?.filter((field) => field.apiName.toLowerCase().startsWith(columnName.trim().toLowerCase()))
          ?.map((field) => ({
            text: field.apiName,
            displayText: field.apiName,
          })) || [];

      return {
        list: columns,
        from: CodeMirror.Pos(editor.getCursor().line, !columnName.trim() ? start + 1 : start),
        to: CodeMirror.Pos(editor.getCursor().line, end),
      };
    }
  }

  // Show entities(tables) suggestions
  const words = line.substring(0, start).trim().split(/\s+/);
  const lastWord = words[words.length - 1].toUpperCase();

  const tableNames = dataSources?.map((ds) => ds.apiName) || [];

  let shouldShowTableHints = false;
  let shouldAddSchema = false;
  if ([...TABLE_HINTS_WITH_SCHEMA, ...TABLE_HINTS_WITHOUT_SCHEMA].includes(lastWord) || lastWord.trim().endsWith(',')) {
    shouldShowTableHints = true;
    if (TABLE_HINTS_WITH_SCHEMA.includes(lastWord)) {
      shouldAddSchema = true;
    }
  }
  let defaultHintsResult = (CodeMirror.hint as any).sql(editor, options);
  let defaultHints;

  if (defaultHintsResult instanceof Promise) {
    defaultHints = await defaultHintsResult;
  } else {
    defaultHints = defaultHintsResult;
  }

  const combinedHints = {
    list: [
      ...(shouldShowTableHints
        ? tableNames
            .filter((name) => name.toLowerCase().startsWith(currentWord.trim().toLowerCase()))
            .map((tableName) => ({
              text: schema && shouldAddSchema ? `${schema}.${tableName}` : tableName,
              displayText: tableName,
            }))
        : defaultHints?.list || []),
    ],
    from: CodeMirror.Pos(editor.getCursor().line, !currentWord.trim() ? start + 1 : start),
    to: CodeMirror.Pos(editor.getCursor().line, end),
  };

  return combinedHints;
};

export const handleVariableEdit = (editor: Editor, handleSetVariable: (variableName: string) => void) => {
  const cursor = editor.getCursor();
  const token = editor.getTokenAt(cursor);
  const start = token.start;
  const end = cursor.ch;
  const variablePattern = getVariablePattern(editor);

  // Open edit variable modal
  if (variablePattern.startsWith(VARIABLE_PREFIX) && variablePattern.endsWith(VARIABLE_SUFFIX)) {
    const variableName = variablePattern.slice(2, -2).trim();
    handleSetVariable(variableName);
    return {
      list: [],
      from: CodeMirror.Pos(cursor.line, start + 1),
      to: CodeMirror.Pos(cursor.line, end),
    };
  }
};

export const handleVariableCreate = (editor: Editor) => {
  const cursor = editor.getCursor();
  const token = editor.getTokenAt(cursor);
  const line = editor.getLine(cursor.line);
  const start = token.start;
  const end = cursor.ch;
  const currentLineUpToCursor = line.substring(0, end);

  if (currentLineUpToCursor.endsWith(VARIABLE_PREFIX)) {
    return {
      list: [
        {
          text: CREATE_NEW_VARIABLE,
        },
      ],
      from: CodeMirror.Pos(cursor.line, start + 1),
      to: CodeMirror.Pos(cursor.line, end),
    };
  }
};

export const getVariablePattern = (editor: CodeMirror.Editor | null) => {
  if (!editor) {
    return '';
  }
  const cursor = editor.getCursor();
  const line = editor.getLine(cursor.line);

  let start = cursor.ch;
  let end = cursor.ch;

  while (start > 0 && !line.substring(start - 2, start).startsWith(VARIABLE_PREFIX)) {
    start--;
  }
  while (end < line.length && !line.substring(end, end + 2).endsWith(VARIABLE_SUFFIX)) {
    end++;
  }

  const pattern = line.substring(start - 2, end + 2);
  return pattern;
};

export const isVariableContext = (editor: CodeMirror.Editor) => {
  const pattern = getVariablePattern(editor);
  return pattern.startsWith(VARIABLE_PREFIX) && pattern.endsWith(VARIABLE_SUFFIX);
};

export const onEndCompletion = (
  editor: CodeMirror.Editor,
  handleNewVariableOpen: (cursor: CodeMirror.Position, start: number) => void
) => {
  const cursor = editor.getCursor();
  const line = editor.getLine(cursor.line);
  const tokenAtCursor = editor.getTokenAt(cursor);
  let start = cursor.ch - tokenAtCursor.string.length;
  let end = cursor.ch;

  if (tokenAtCursor.string.startsWith(CREATE_NEW_VARIABLE)) {
    const pattern = `${VARIABLE_PREFIX}${CREATE_NEW_VARIABLE}`;
    const indexOfPattern = line.indexOf(pattern);
    start = indexOfPattern;
    end = start + pattern.length;
    editor.replaceRange('', { line: cursor.line, ch: start }, { line: cursor.line, ch: end });
    editor.setCursor({ line: cursor.line, ch: start });

    handleNewVariableOpen(cursor, start);
  }
};
