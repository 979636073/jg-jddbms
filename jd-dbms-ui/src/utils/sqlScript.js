// Keep literal/comment semicolons and Oracle/DM procedural blocks in one statement.
export function splitSqlStatements(sql) {
  const statements = [];
  let start = 0, quote = '', qEnd = '', lineComment = false, blockComment = false;
  let words = [], depth = 0, afterEnd = false, procedural = false, ended = false;
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i], next = sql[i + 1];
    if (lineComment) { if (ch === '\n') lineComment = false; continue; }
    if (blockComment) { if (ch === '*' && next === '/') { blockComment = false; i++; } continue; }
    if (qEnd) { if (ch === qEnd && next === "'") { qEnd = ''; i++; } continue; }
    if (quote) {
      if (ch === quote) { if (next === quote) i++; else quote = ''; }
      continue;
    }
    if (ch === '-' && next === '-') { lineComment = true; i++; continue; }
    if (ch === '/' && next === '*') { blockComment = true; i++; continue; }
    if (/[qQ]/.test(ch) && next === "'" && sql[i + 2]) {
      const delimiter = sql[i + 2];
      qEnd = ({ '[': ']', '(': ')', '{': '}', '<': '>' })[delimiter] || delimiter;
      i += 2; continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') { quote = ch; continue; }
    if (/[a-zA-Z_]/.test(ch)) {
      const token = sql.slice(i).match(/^[a-zA-Z_][\w$#]*/)[0].toUpperCase();
      i += token.length - 1;
      if (words.length < 8) words.push(token);
      if (words[0] === 'BEGIN' || words[0] === 'DECLARE' ||
        (words[0] === 'CREATE' && words.slice(1, 6).some(word => ['PROCEDURE', 'FUNCTION', 'TRIGGER', 'PACKAGE', 'BODY'].includes(word)))) {
        if (!procedural && ['PACKAGE', 'BODY'].includes(token)) depth++;
        procedural = true;
      }
      if (procedural) {
        if (token === 'END') { depth = Math.max(0, depth - 1); afterEnd = true; ended = depth === 0; }
        else if (afterEnd && ['IF', 'LOOP', 'CASE'].includes(token)) afterEnd = false;
        else {
          afterEnd = false;
          if (['BEGIN', 'IF', 'LOOP', 'CASE'].includes(token)) { depth++; ended = false; }
        }
      }
      continue;
    }
    if (ch === '/' && /^\s*$/.test(sql.slice(sql.lastIndexOf('\n', i - 1) + 1, i)) &&
      /^\s*(?:\r?\n|$)/.test(sql.slice(i + 1))) {
      if (words.length) statements.push({ start, end: i, sql: sql.slice(start, i).trim() });
      start = i + 1; words = []; depth = 0; afterEnd = false; procedural = false; ended = false;
      continue;
    }
    if (ch === ';' && (!procedural || (depth === 0 && ended))) {
      if (words.length) statements.push({ start, end: i + 1, sql: sql.slice(start, procedural ? i + 1 : i).trim() });
      start = i + 1; words = []; depth = 0; afterEnd = false; procedural = false; ended = false;
    }
  }
  if (words.length && sql.slice(start).trim()) statements.push({ start, end: sql.length, sql: sql.slice(start).trim() });
  return statements;
}

export function sqlAtCursor(sql, offset) {
  const statements = splitSqlStatements(sql);
  const current = statements.find(item => offset >= item.start && offset < item.end) ||
    (offset === sql.length ? statements[statements.length - 1] : null);
  return current ? current.sql : '';
}
