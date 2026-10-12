export const RESOURCE_MUTATION_EVENT = 'workspace-resource-mutation';

// Only endpoints which actually execute DDL belong here. SQL-preview endpoints are excluded.
const RESOURCE_ENDPOINTS = {
  '/api/rdb/table/createTable': ['tables'],
  '/api/rdb/table/delete': ['tables'],
  '/api/rdb/table/dropTable': ['tables'],
  '/api/rdb/table/dropTableTask': ['tables'],
  '/api/rdb/table/copyTable': ['tables'],
  '/api/rdb/table/createToTable': ['tables'],
  '/api/rdb/table/createTable_constraint': ['tables'],
  '/api/rdb/table/drop_table_constraint': ['tables'],
  '/api/rdb/view/execute': ['views'],
  '/api/rdb/view/allExecute': ['views'],
  '/api/rdb/view/delete': ['views'],
  '/api/rdb/view/updateViewTableName': ['views'],
  '/api/rdb/view/updateViewColumnName': ['views'],
  '/api/rdb/user/createUser': ['users', 'schemas'],
  '/api/rdb/user/dropUser': ['users', 'schemas'],
  '/api/rdb/user/modify': ['users'],
  '/api/rdb/user/addOrDelUserRole': ['users'],
  '/api/rdb/user/addObjectRole': ['users'],
  '/api/rdb/user/managePassWord': ['users'],
  '/api/rdb/user/lockUser': ['users'],
  '/api/rdb/tableSpace/createTablespaceSql': ['tableSpace'],
  '/api/rdb/tableSpace/updateTablespaceSql': ['tableSpace'],
  '/api/rdb/tableSpace/dropTablespace': ['tableSpace'],
  '/api/rdb/schema/delete_schema': ['schemas'],
  '/api/rdb/schema/modify_schema': ['schemas'],
  '/api/rdb/database/delete_database': ['databases'],
  '/api/rdb/database/modify_database': ['databases'],
  '/api/rdb/function/update': ['functions'],
  '/api/rdb/function/delete_function': ['functions'],
  '/api/rdb/procedure/update': ['procedures'],
  '/api/rdb/procedure/delete_procedure': ['procedures'],
  '/api/rdb/trigger/createTriggersWH': ['triggers'],
  '/api/rdb/trigger/deleteTriggers': ['triggers'],
  '/api/pin/table/add': ['tables'],
  '/api/pin/table/delete': ['tables'],
};

const SQL_ENDPOINTS = new Set([
  '/api/rdb/dml/execute', '/api/rdb/dml/execute_ddl', '/api/rdb/dml/JDBCExecute',
]);
const pendingTaskChanges = new Map();
const notifiedTransferTasks = new Set();

const SQL_OBJECT_GROUPS = {
  TABLE: 'tables', VIEW: 'views', USER: 'users', SCHEMA: 'schemas',
  TABLESPACE: 'tableSpace', FUNCTION: 'functions', PROCEDURE: 'procedures',
  TRIGGER: 'triggers', DATABASE: 'databases', INDEX: 'tables',
};

export function groupsChangedBySql(sql) {
  const text = String(sql || '').replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\r\n]*/g, ' ').replace(/'(?:''|[^'])*'/g, ' ');
  const groups = new Set();
  const ddl = /\b(?:CREATE(?:\s+OR\s+REPLACE)?|ALTER|DROP|TRUNCATE)\s+(?:(?:UNIQUE|BITMAP|MATERIALIZED)\s+)?(TABLESPACE|TABLE|VIEW|USER|SCHEMA|FUNCTION|PROCEDURE|TRIGGER|DATABASE|INDEX)\b/gi;
  let match;
  while ((match = ddl.exec(text))) groups.add(SQL_OBJECT_GROUPS[match[1].toUpperCase()]);
  if (/\bRENAME\s+\S+\s+TO\s+\S+/i.test(text)) groups.add('tables');
  if (/\bCOMMENT\s+ON\s+TABLE\b/i.test(text)) groups.add('tables');
  if (/\bCOMMENT\s+ON\s+COLUMN\b/i.test(text)) groups.add('tables');
  if (/\bCOMMENT\s+ON\s+VIEW\b/i.test(text)) groups.add('views');
  if (/\b(?:GRANT|REVOKE)\b/i.test(text)) groups.add('users');
  if (groups.has('users')) groups.add('schemas');
  return [...groups];
}

function parseRequestData(config) {
  let body = {};
  if (config?.data && typeof config.data === 'object') body = config.data;
  else {
    try { body = JSON.parse(config?.data || '{}'); } catch (error) { body = {}; }
  }
  const query = {};
  const queryString = String(config?.url || '').split('?')[1] || '';
  queryString.split('&').filter(Boolean).forEach(part => {
    const separator = part.indexOf('=');
    const key = decodeURIComponent(separator < 0 ? part : part.slice(0, separator));
    const value = decodeURIComponent((separator < 0 ? '' : part.slice(separator + 1)).replace(/\+/g, ' '));
    query[key] = value;
  });
  return { ...query, ...(config?.params || {}), ...body };
}

function succeeded(response) {
  if (!response) return false;
  if (response.success === false) {
    if (Array.isArray(response.data)) return response.data.some(item => item.success === true);
    return Array.isArray(response.data?.items)
      && response.data.items.some(item => item.status === 'SUCCESS');
  }
  if (Array.isArray(response.data)) return response.data.some(item => item.success === true);
  if (typeof response.data?.success === 'boolean') return response.data.success;
  return response.success === true || response.code === 200;
}

export function mutationFromResponse(config, response) {
  if (!succeeded(response)) return null;
  const path = String(config?.url || '').split('?')[0].replace(/^https?:\/\/[^/]+/, '');
  const data = parseRequestData(config);
  if (!data.dataSourceId) return null;
  let groups = RESOURCE_ENDPOINTS[path];
  let schemaName = data.copySchemaName || data.schemaName || '';
  if (SQL_ENDPOINTS.has(path)) {
    const successfulSql = Array.isArray(response.data)
      ? response.data.filter(item => item.success === true).map(item => item.originalSql || data.sql)
      : [data.sql];
    groups = [...new Set(successfulSql.flatMap(groupsChangedBySql))];
    // A SQL script may explicitly target another schema than the current console schema.
    schemaName = '';
  }
  if (!groups?.length) return null;
  const change = {
    dataSourceId: data.dataSourceId,
    databaseName: data.databaseName || '',
    schemaName,
    groups: [...groups],
  };
  if (path === '/api/rdb/table/dropTableTask' && response.data != null) {
    pendingTaskChanges.set(String(response.data), change);
    return null;
  }
  return change;
}

export function publishResourceMutation(change) {
  if (!change || typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent(RESOURCE_MUTATION_EVENT, { detail: change }));
}

export function notifyResourceTaskStatus(task) {
  if (!task || !['FINISH', 'ERROR'].includes(task.taskStatus)) return;
  const key = String(task.id);
  if (task.taskStatus === 'FINISH' && ['TABLE_IMPORT', 'SCHEMA_SQL_IMPORT'].includes(task.taskType) && !notifiedTransferTasks.has(key)) {
    notifiedTransferTasks.add(key);
    publishResourceMutation({ dataSourceId: task.dataSourceId, schemaName: task.schemaName || '', databaseName: task.databaseName || '',
      groups: task.taskType === 'SCHEMA_SQL_IMPORT' ? ['tables', 'views', 'procedures', 'functions', 'triggers'] : ['tables'] });
  }
  const change = pendingTaskChanges.get(key);
  if (!change) return;
  pendingTaskChanges.delete(key);
  publishResourceMutation(change);
}
