const identifierPattern = /^[A-Za-z][A-Za-z0-9_$#]*$/;

export function buildViewSql(form, schema, kind) {
  const name = String(form.name || "").trim();
  const query = String(form.query || "").trim().replace(/;\s*$/, "");
  if (!identifierPattern.test(name)) throw new Error("视图名称只能使用字母、数字、下划线、$ 或 #，且须以字母开头");
  if (!/^(SELECT|WITH)\b/i.test(query)) throw new Error("查询定义必须以 SELECT 或 WITH 开始");
  const qualifiedName = schema ? `${schema}.${name}` : name;
  if (kind === "materialized") {
    return `CREATE MATERIALIZED VIEW ${qualifiedName}\nBUILD ${form.buildMode}\nREFRESH ${form.refreshMode} ON ${form.refreshOn}\nAS\n${query}`;
  }
  const replace = form.orReplace ? " OR REPLACE" : "";
  const force = form.force ? " FORCE" : "";
  const columns = String(form.columns || "").trim();
  const columnNames = columns ? columns.split(",").map(item => item.trim()) : [];
  if (columnNames.some(item => !identifierPattern.test(item))) throw new Error("列名请用逗号分隔，并使用合法标识符");
  const columnSql = columnNames.length ? ` (${columnNames.join(", ")})` : "";
  const restriction = form.restriction === "readOnly" ? "\nWITH READ ONLY"
    : form.restriction === "check" ? "\nWITH CHECK OPTION" : "";
  return `CREATE${replace}${force} VIEW ${qualifiedName}${columnSql}\nAS\n${query}${restriction}`;
}
