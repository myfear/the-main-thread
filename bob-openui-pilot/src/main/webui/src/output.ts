import { createParser } from '@openuidev/lang-core';
import { library } from './library';

export function inspectOutput(source: string) {
  const result = createParser(library.toJSONSchema(), 'Report').parse(source);
  return result;
}
