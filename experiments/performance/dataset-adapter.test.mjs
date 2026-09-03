import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { loadCanonicalDataset, parseCsv, parseTable, renderMidtermSql, renderMicroservicesSql, sqlText, validateRows } from './dataset-adapter.mjs';

const tables = {
  users: [{ id:'1', username:'u1', nickname:"陈'\n宇", email:'u1@benchmark.example', campus_region:'学院路\\北', credit_score:'100', last_active_at:'2026-08-01T00:01:00.000Z' }, { id:'2', username:'u2', nickname:'', email:'u2@benchmark.example', campus_region:'沙河', credit_score:'80', last_active_at:'2026-08-01T00:02:00.000Z' }],
  items: [{ id:'1', title:'书', category:'教材', price:'12.30', description:'a', seller_id:'1', status:'ON_SALE', moderation_status:'VISIBLE', campus_region:'沙河', created_at:'2026-08-01T00:03:00.000Z', tags:'可小刀|支持验货' }],
  messages: [{ id:'1', item_id:'1', sender_id:'2', receiver_id:'1', content:"问'价\\\n第二行", created_at:'2026-08-01T00:04:00.000Z' }],
};

test('CSV parser handles UTF-8, commas, quotes, CRLF and embedded newlines', () => {
  assert.deepEqual(parseCsv('id,text\r\n1,"中文,""引号""\n下一行"\r\n'), [['id','text'], ['1','中文,"引号"\n下一行']]);
  assert.equal(parseTable('id,text\n1,x\n', ['id','text'], 'x.csv')[0].text, 'x');
});

test('SQL text encoding is deterministic and immune to SQL string escaping modes', () => {
  const encoded = sqlText("中文'\\\n");
  assert.equal(encoded, 'CONVERT(0xe4b8ade69687275c0a USING utf8mb4)');
  assert.equal(sqlText('', { nullable: true }), 'NULL');
  assert.equal(encoded, sqlText("中文'\\\n"));
});

test('validation checks counts, references, required fields and canonical IDs', () => {
  assert.doesNotThrow(() => validateRows(tables, { users:2, items:1, messages:1 }));
  assert.throws(() => validateRows({ ...tables, messages: [] }, { users:2, items:1, messages:1 }), /row count/);
  assert.throws(() => validateRows({ ...tables, items: [{ ...tables.items[0], id:'2' }] }, { users:2, items:1, messages:1 }), /canonical IDs/);
});

test('schema renderers preserve IDs and map regions, tags and service-specific columns', async () => {
  const first = renderMidtermSql(tables, 'abc');
  const second = renderMidtermSql(tables, 'abc');
  assert.equal(first, second);
  assert.match(first, /`status_reason`/);
  assert.match(first, /`region`/);
  assert.match(first, /INSERT INTO `item_tags`/);
  assert.match(first, /\(1, CONVERT\(0xe58fafe5b08fe58880/);
  const micro = renderMicroservicesSql(tables, 'abc');
  assert.match(micro.account, /`public_profile_version`/);
  assert.match(micro.marketplace, /INSERT INTO `searchable_user_projection`/);
  assert.match(micro.marketplace, /`reserved_order_id`/);
  assert.doesNotMatch(micro.marketplace, /chat_messages/);
  assert.ok(micro.marketplace.indexOf('INSERT INTO `searchable_user_projection`') < micro.marketplace.indexOf('INSERT INTO `items`'));
  assert.match(micro.marketplace, /`id`, `username`, `nickname`, `campus_region`, `credit_score`, `last_active_at`, `status`, `role`, `created_at`, `source_version`, `row_version`, `updated_at`/);
  assert.ok(micro.marketplace.includes(`(1, ${sqlText('u1')}, ${sqlText("陈'\n宇")}, ${sqlText('学院路\\北')}, 100,`));
  assert.ok(micro.marketplace.includes(`${sqlText('ACTIVE')}, ${sqlText('STUDENT')}`));
  const projectedTime = `CAST(${sqlText('2026-08-01 00:01:00.000000')} AS DATETIME(6))`;
  assert.ok(micro.marketplace.includes(`${projectedTime}, 0, 0, ${projectedTime}`));
  assert.deepEqual(renderMicroservicesSql(tables, 'abc'), micro);
  const directory = await mkdtemp(join(tmpdir(), 'dataset-adapter-test-'));
  const path = join(directory, 'output.sql');
  await writeFile(path, micro.marketplace, 'utf8');
  assert.equal(await readFile(path, 'utf8'), micro.marketplace);
});

test('dataset loader enforces manifest version, counts, bytes and SHA-256', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'canonical-loader-test-'));
  const contents = {
    'users.csv': 'id,username,nickname,email,campus_region,credit_score,last_active_at\n1,u1,n1,u1@benchmark.example,学院路,100,2026-08-01T00:01:00.000Z\n2,u2,n2,u2@benchmark.example,沙河,80,2026-08-01T00:02:00.000Z\n',
    'items.csv': 'id,title,category,price,description,seller_id,status,moderation_status,campus_region,created_at,tags\n1,书,教材,12.30,说明,1,ON_SALE,VISIBLE,学院路,2026-08-01T00:03:00.000Z,可小刀\n',
    'messages.csv': 'id,item_id,sender_id,receiver_id,content,created_at\n1,1,2,1,询价,2026-08-01T00:04:00.000Z\n',
  };
  const files = {};
  for (const [name, content] of Object.entries(contents)) {
    await writeFile(join(directory, name), content, 'utf8');
    files[name] = { sha256: createHash('sha256').update(content).digest('hex'), bytes: Buffer.byteLength(content) };
  }
  const manifest = { schemaVersion:7, seed:42, counts:{ users:2, items:1, messages:1 }, files };
  await writeFile(join(directory, 'manifest.json'), `${JSON.stringify(manifest)}\n`, 'utf8');
  const loaded = await loadCanonicalDataset(directory, { schemaVersion:7, seed:42, users:2, items:1, messages:1 });
  assert.deepEqual(loaded.fileSha256, Object.fromEntries(Object.entries(files).map(([name, entry]) => [name, entry.sha256])));
  await writeFile(join(directory, 'items.csv'), `${contents['items.csv']}corruption`, 'utf8');
  await assert.rejects(loadCanonicalDataset(directory, { schemaVersion:7, seed:42, users:2, items:1, messages:1 }), /manifest SHA-256\/bytes/);
});
