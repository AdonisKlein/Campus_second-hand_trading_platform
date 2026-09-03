import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

export const EXPECTED = Object.freeze({ schemaVersion: 1, seed: 20260902, users: 2500, items: 20000, messages: 50000 });
export const BENCHMARK_PASSWORD_HASH = '$benchmark$not-a-real-password-hash$20260902';

const HEADERS = Object.freeze({
  users: ['id', 'username', 'nickname', 'email', 'campus_region', 'credit_score', 'last_active_at'],
  items: ['id', 'title', 'category', 'price', 'description', 'seller_id', 'status', 'moderation_status', 'campus_region', 'created_at', 'tags'],
  messages: ['id', 'item_id', 'sender_id', 'receiver_id', 'content', 'created_at'],
});

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let index = text.charCodeAt(0) === 0xfeff ? 1 : 0; index < text.length; index += 1) {
    const character = text[index];
    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else if (character === '"') {
        quoted = false;
      } else {
        field += character;
      }
    } else if (character === '"' && field.length === 0) {
      quoted = true;
    } else if (character === ',') {
      row.push(field);
      field = '';
    } else if (character === '\n' || character === '\r') {
      if (character === '\r' && text[index + 1] === '\n') index += 1;
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else {
      field += character;
    }
  }
  if (quoted) throw new Error('CSV has an unterminated quoted field.');
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

export function parseTable(text, expectedHeaders, name) {
  const rows = parseCsv(text);
  if (rows.length === 0) throw new Error(`${name} is empty.`);
  if (rows[0].join('\u0000') !== expectedHeaders.join('\u0000')) {
    throw new Error(`${name} headers do not match the canonical schema.`);
  }
  return rows.slice(1).map((values, rowIndex) => {
    if (values.length !== expectedHeaders.length) throw new Error(`${name} row ${rowIndex + 2} has ${values.length} fields; expected ${expectedHeaders.length}.`);
    return Object.fromEntries(expectedHeaders.map((header, index) => [header, values[index]]));
  });
}

export function sqlText(value, { nullable = false } = {}) {
  if (value === null || value === undefined || (nullable && value === '')) return 'NULL';
  const hex = Buffer.from(String(value), 'utf8').toString('hex');
  return `CONVERT(0x${hex} USING utf8mb4)`;
}

function integer(value, field) {
  if (!/^[1-9]\d*$/.test(value)) throw new Error(`${field} must be a positive integer.`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) throw new Error(`${field} exceeds the safe integer range.`);
  return parsed;
}

function decimal(value, field) {
  if (!/^\d{1,8}\.\d{2}$/.test(value)) throw new Error(`${field} must be a DECIMAL(10,2) value.`);
  return value;
}

function required(row, fields, table, rowNumber) {
  for (const field of fields) if (row[field] === '') throw new Error(`${table} row ${rowNumber}: ${field} is required.`);
}

export function validateRows(tables, expected = EXPECTED) {
  for (const table of ['users', 'items', 'messages']) {
    if (tables[table].length !== expected[table]) throw new Error(`${table} row count is ${tables[table].length}; expected ${expected[table]}.`);
  }
  const userIds = new Set();
  tables.users.forEach((row, index) => {
    required(row, ['id','username','email','credit_score','last_active_at'], 'users', index + 2);
    const id = integer(row.id, `users[${index}].id`);
    if (id !== index + 1 || userIds.has(id)) throw new Error('users IDs must be unique canonical IDs 1..N.');
    userIds.add(id);
    integer(row.credit_score, `users[${index}].credit_score`);
  });
  const itemIds = new Set();
  tables.items.forEach((row, index) => {
    required(row, ['id','title','category','price','seller_id','status','moderation_status','campus_region','created_at'], 'items', index + 2);
    const id = integer(row.id, `items[${index}].id`);
    if (id !== index + 1 || itemIds.has(id)) throw new Error('items IDs must be unique canonical IDs 1..N.');
    itemIds.add(id);
    if (!userIds.has(integer(row.seller_id, `items[${index}].seller_id`))) throw new Error(`items row ${index + 2} references an unknown seller.`);
    decimal(row.price, `items[${index}].price`);
  });
  tables.messages.forEach((row, index) => {
    required(row, HEADERS.messages, 'messages', index + 2);
    const id = integer(row.id, `messages[${index}].id`);
    if (id !== index + 1) throw new Error('messages IDs must be unique canonical IDs 1..N.');
    if (!itemIds.has(integer(row.item_id, `messages[${index}].item_id`))) throw new Error(`messages row ${index + 2} references an unknown item.`);
    for (const field of ['sender_id', 'receiver_id']) if (!userIds.has(integer(row[field], `messages[${index}].${field}`))) throw new Error(`messages row ${index + 2} references an unknown user.`);
  });
}

function insert(table, columns, values, batchSize = 500) {
  const statements = [];
  for (let start = 0; start < values.length; start += batchSize) {
    statements.push(`INSERT INTO \`${table}\` (${columns.map((column) => `\`${column}\``).join(', ')}) VALUES\n${values.slice(start, start + batchSize).map((row) => `  (${row.join(', ')})`).join(',\n')};`);
  }
  return statements.join('\n\n');
}

const text = (value, nullable = false) => sqlText(value, { nullable });
const time = (value) => {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2}\.\d{3})Z$/.exec(value);
  if (!match) throw new Error(`Invalid canonical UTC timestamp: ${value}`);
  return `CAST(${text(`${match[1]} ${match[2]}000`)} AS DATETIME(6))`;
};

function userValues(rows, microservices) {
  return rows.map((row) => [row.id, text(row.username), text(BENCHMARK_PASSWORD_HASH), text(row.nickname, true), 'NULL', text(row.email), text('STUDENT'), text('ACTIVE'), '0', 'NULL', '0', text(row.campus_region, true), row.credit_score, time(row.last_active_at), time(row.last_active_at), ...(microservices ? ['0'] : ['NULL'])]);
}

function projectionValues(rows) {
  return rows.map((row) => [row.id, text(row.username), text(row.nickname, true),
    text(row.campus_region, true), row.credit_score, time(row.last_active_at),
    text('ACTIVE'), text('STUDENT'), time(row.last_active_at), '0', '0', time(row.last_active_at)]);
}

function itemValues(rows) {
  return rows.map((row) => [row.id, text(row.title), text(row.category), row.price, text(row.description, true), 'NULL', row.seller_id, text(row.status), text(row.moderation_status), time(row.created_at), '0', text(row.campus_region), 'NULL']);
}

function messageValues(rows) {
  return rows.map((row) => [row.id, row.item_id, row.sender_id, row.receiver_id, text(row.content), time(row.created_at)]);
}

function tagValues(rows) {
  return rows.flatMap((row) => (row.tags === '' ? [] : row.tags.split('|')).map((tag) => {
    if (tag === '') throw new Error(`items ID ${row.id} contains an empty tag between separators.`);
    return [row.id, text(tag)];
  }));
}

function sqlHeader(label, manifestSha) {
  return `-- Deterministic benchmark import for ${label}\n-- canonical manifest sha256: ${manifestSha}\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 1;`;
}

export function renderMidtermSql(tables, manifestSha) {
  const users = insert('users', ['id','username','password_hash','nickname','phone','email','role','status','login_failed_count','locked_until','auth_version','campus_region','credit_score','last_active_at','created_at','status_reason'], userValues(tables.users, false));
  const items = insert('items', ['id','title','category','price','description','image_url','seller_id','status','moderation_status','created_at','version','region'], itemValues(tables.items).map((row) => row.slice(0, -1)));
  const tags = insert('item_tags', ['item_id','tag'], tagValues(tables.items));
  const messages = insert('messages', ['id','item_id','sender_id','receiver_id','content','created_at'], messageValues(tables.messages));
  return `${sqlHeader('midterm-check', manifestSha)}\n\n${users}\n\n${items}\n\n${tags}\n\n${messages}\n`;
}

export function renderMicroservicesSql(tables, manifestSha) {
  const accountUsers = insert('users', ['id','username','password_hash','nickname','phone','email','role','status','login_failed_count','locked_until','auth_version','campus_region','credit_score','last_active_at','created_at','public_profile_version'], userValues(tables.users, true));
  const projections = insert('searchable_user_projection', ['id','username','nickname','campus_region','credit_score','last_active_at','status','role','created_at','source_version','row_version','updated_at'], projectionValues(tables.users));
  const marketplaceItems = insert('items', ['id','title','category','price','description','image_url','seller_id','status','moderation_status','created_at','version','region','reserved_order_id'], itemValues(tables.items));
  const tags = insert('item_tags', ['item_id','tag'], tagValues(tables.items));
  const messages = insert('messages', ['id','item_id','sender_id','receiver_id','content','created_at'], messageValues(tables.messages));
  return {
    account: `${sqlHeader('microservices-end account-service', manifestSha)}\n\n${accountUsers}\n`,
    marketplace: `${sqlHeader('microservices-end marketplace-service', manifestSha)}\n\n${projections}\n\n${marketplaceItems}\n\n${tags}\n\n${messages}\n`,
  };
}

export async function loadCanonicalDataset(inputDirectory, expected = EXPECTED) {
  const input = resolve(inputDirectory);
  const manifestBuffer = await readFile(resolve(input, 'manifest.json'));
  let manifest;
  try { manifest = JSON.parse(manifestBuffer.toString('utf8')); } catch (error) { throw new Error(`manifest.json is invalid JSON: ${error.message}`); }
  if (manifest.schemaVersion !== expected.schemaVersion) throw new Error(`manifest schemaVersion is ${manifest.schemaVersion}; expected ${expected.schemaVersion}.`);
  if (manifest.seed !== expected.seed) throw new Error(`manifest seed is ${manifest.seed}; expected ${expected.seed}.`);
  for (const table of ['users', 'items', 'messages']) if (manifest.counts?.[table] !== expected[table]) throw new Error(`manifest count for ${table} is invalid.`);
  const buffers = {};
  const hashes = {};
  for (const table of ['users', 'items', 'messages']) {
    const name = `${table}.csv`;
    buffers[table] = await readFile(resolve(input, name));
    hashes[name] = sha256(buffers[table]);
    const entry = manifest.files?.[name];
    if (!entry || entry.sha256 !== hashes[name] || entry.bytes !== buffers[table].byteLength) throw new Error(`${name} does not match canonical manifest SHA-256/bytes.`);
  }
  const tables = Object.fromEntries(Object.entries(buffers).map(([table, buffer]) => [table, parseTable(buffer.toString('utf8'), HEADERS[table], `${table}.csv`)]));
  validateRows(tables, expected);
  return { tables, manifest, manifestSha256: sha256(manifestBuffer), fileSha256: hashes, input };
}

export async function generateAdapters({ inputDirectory, outputDirectory, target = 'all' }) {
  if (!['all', 'midterm', 'microservices'].includes(target)) throw new Error(`Unknown target: ${target}`);
  const dataset = await loadCanonicalDataset(inputDirectory);
  const output = resolve(outputDirectory);
  if (output === dataset.input) throw new Error('Output directory must differ from the canonical dataset directory.');
  await mkdir(output, { recursive: true });
  const outputs = {};
  if (target === 'all' || target === 'midterm') {
    outputs.midterm = 'midterm-check.sql';
    await writeFile(resolve(output, outputs.midterm), renderMidtermSql(dataset.tables, dataset.manifestSha256), 'utf8');
  }
  if (target === 'all' || target === 'microservices') {
    const rendered = renderMicroservicesSql(dataset.tables, dataset.manifestSha256);
    outputs.account = 'microservices-end-account.sql';
    outputs.marketplace = 'microservices-end-marketplace.sql';
    await writeFile(resolve(output, outputs.account), rendered.account, 'utf8');
    await writeFile(resolve(output, outputs.marketplace), rendered.marketplace, 'utf8');
  }
  const report = {
    schemaVersion: 1,
    target,
    canonical: { manifestSha256: dataset.manifestSha256, schemaVersion: dataset.manifest.schemaVersion, seed: dataset.manifest.seed, counts: dataset.manifest.counts, files: Object.fromEntries(Object.entries(dataset.fileSha256).map(([name, hash]) => [name, { sha256: hash }])) },
    validation: { rowCountsMatch: true, manifestHashesMatch: true, idsPreserved: true, marketplaceProjectionGenerated: target !== 'midterm', userIdRange: [1, dataset.tables.users.length], itemIdRange: [1, dataset.tables.items.length], messageIdRange: [1, dataset.tables.messages.length] },
    outputs,
  };
  await writeFile(resolve(output, 'adapter-manifest.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  return report;
}

export function displayOutput(report, outputDirectory) {
  return `Generated ${Object.keys(report.outputs).length} SQL file(s) in ${resolve(outputDirectory)} from manifest ${report.canonical.manifestSha256}.`;
}
