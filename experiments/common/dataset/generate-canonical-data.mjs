import { createHash } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import process from 'node:process';

function parseArguments(argv) {
  const values = { seed: 20260902, users: 2500, items: 20000, messages: 50000, output: null };
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]?.replace(/^--/, '');
    const value = argv[index + 1];
    if (!key || value === undefined || !(key in values)) throw new Error(`Unknown or incomplete argument: ${argv[index]}`);
    values[key] = key === 'output' ? value : Number(value);
  }
  if (!values.output) throw new Error('--output is required.');
  for (const key of ['seed', 'users', 'items', 'messages']) {
    if (!Number.isSafeInteger(values[key]) || values[key] <= 0) throw new Error(`--${key} must be a positive integer.`);
  }
  if (values.users < 2) throw new Error('--users must be at least 2 so a buyer can differ from a seller.');
  return values;
}

function mulberry32(seed) {
  let state = seed >>> 0;
  return () => {
    state += 0x6D2B79F5;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function csv(value) {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toCsv(headers, rows) {
  return `${headers.join(',')}\n${rows.map((row) => headers.map((header) => csv(row[header])).join(',')).join('\n')}\n`;
}

function deterministicTime(anchor, offsetMinutes) {
  return new Date(anchor.getTime() + offsetMinutes * 60_000).toISOString();
}

function sha256(content) {
  return createHash('sha256').update(content, 'utf8').digest('hex');
}

const options = parseArguments(process.argv.slice(2));
const random = mulberry32(options.seed);
const anchor = new Date('2026-08-01T00:00:00.000Z');
const regions = ['学院路校区', '沙河校区', '大运村'];
const categories = ['教材', '数码', '生活', '运动', '其他'];
const tagSets = [['可小刀'], ['仅自提'], ['支持验货'], ['可小刀', '支持验货'], ['免费赠送']];

const users = [];
for (let id = 1; id <= options.users; id += 1) {
  users.push({
    id,
    username: `student${String(id).padStart(5, '0')}`,
    nickname: `学生${String(id).padStart(5, '0')}`,
    email: `student${String(id).padStart(5, '0')}@benchmark.example`,
    campus_region: regions[id % regions.length],
    credit_score: 60 + (id % 41),
    last_active_at: deterministicTime(anchor, id % 10080),
  });
}

const items = [];
for (let id = 1; id <= options.items; id += 1) {
  const sellerId = 1 + Math.floor(random() * options.users);
  const category = categories[id % categories.length];
  items.push({
    id,
    title: `${category}二手物品 ${String(id).padStart(6, '0')}`,
    category,
    price: (5 + Math.floor(random() * 199500) / 100).toFixed(2),
    description: `固定种子 ${options.seed} 生成的校园二手商品 ${id}`,
    seller_id: sellerId,
    status: 'ON_SALE',
    moderation_status: 'VISIBLE',
    campus_region: regions[id % regions.length],
    created_at: deterministicTime(anchor, id),
    tags: tagSets[id % tagSets.length].join('|'),
  });
}

const messages = [];
for (let id = 1; id <= options.messages; id += 1) {
  const itemId = 1 + Math.floor(random() * options.items);
  const sellerId = items[itemId - 1].seller_id;
  let senderId = 1 + Math.floor(random() * options.users);
  if (senderId === sellerId) senderId = (senderId % options.users) + 1;
  messages.push({
    id,
    item_id: itemId,
    sender_id: senderId,
    receiver_id: sellerId,
    content: `商品 ${itemId} 的公开留言 ${String(id).padStart(7, '0')}`,
    created_at: deterministicTime(anchor, options.items + id),
  });
}

const files = {
  'users.csv': toCsv(['id', 'username', 'nickname', 'email', 'campus_region', 'credit_score', 'last_active_at'], users),
  'items.csv': toCsv(['id', 'title', 'category', 'price', 'description', 'seller_id', 'status', 'moderation_status', 'campus_region', 'created_at', 'tags'], items),
  'messages.csv': toCsv(['id', 'item_id', 'sender_id', 'receiver_id', 'content', 'created_at'], messages),
};

const output = resolve(options.output);
await mkdir(output, { recursive: true });
for (const [name, content] of Object.entries(files)) await writeFile(resolve(output, name), content, 'utf8');

const manifest = {
  schemaVersion: 1,
  seed: options.seed,
  anchorTime: anchor.toISOString(),
  counts: { users: options.users, items: options.items, messages: options.messages },
  files: Object.fromEntries(Object.entries(files).map(([name, content]) => [name, { sha256: sha256(content), bytes: Buffer.byteLength(content, 'utf8') }])),
};
await writeFile(resolve(output, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
process.stdout.write(`${JSON.stringify(manifest)}\n`);
