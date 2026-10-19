import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { compile } from 'json-schema-to-typescript';

const schemaPath = new URL('../../schemas/software-city-v1.schema.json', import.meta.url);
const destination = new URL('../src/generated/city-document.ts', import.meta.url);
const text = await readFile(schemaPath, 'utf8');
const schema = JSON.parse(text);
schema.title = 'CityDocument';
// TypeScript declarations describe structure; AJV enforces the original conditional rules.
delete schema.allOf;
const hash = createHash('sha256').update(text).digest('hex');
const generated = await compile(schema, 'CityDocument', {
  bannerComment: '/* Generated from software-city-v1.schema.json. Do not edit.\n * Schema SHA-256: ' + hash + '\n */',
  unreachableDefinitions: true,
  style: { singleQuote: true, tabWidth: 2 },
});
if (process.argv.includes('--check')) {
  if (await readFile(destination, 'utf8').catch(() => '') !== generated) {
    console.error('Generated JSON contract is stale. Run npm run generate:types.');
    process.exitCode = 1;
  }
} else {
  await mkdir(new URL('../src/generated/', import.meta.url), { recursive: true });
  await writeFile(destination, generated, 'utf8');
}
