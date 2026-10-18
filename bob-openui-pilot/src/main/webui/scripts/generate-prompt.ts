import { readFileSync, writeFileSync } from 'node:fs';
import { library, promptOptions } from '../src/library.tsx';

const path = new URL('../../resources/openui-system-prompt.txt', import.meta.url);
const prompt = library.prompt(promptOptions) + '\n';
if (process.argv.includes('--check')) {
  if (readFileSync(path, 'utf8') !== prompt) throw new Error('The component library changed. Run npm run generate and include the updated prompt.');
} else {
  writeFileSync(path, prompt);
}
