import { cp, mkdir, readFile, realpath, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(import.meta.dirname, '..');
export const specialists = ['inbox-triage', 'meeting-prep', 'promise-keeper', 'checker'];

export function outputPattern(workspace) {
    const escaped = workspace.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return '^' + escaped + '/(?:memory|briefs|drafts)/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.md$';
}

export async function prepare(destination, { team = false } = {}) {
    const requested = path.resolve(destination);
    await mkdir(path.dirname(requested), { recursive: true });
    await mkdir(requested); // Refuse to overwrite an existing workspace.
    const workspace = await realpath(requested);
    await cp(path.join(root, 'fixture'), workspace, { recursive: true });
    const config = path.join(workspace, '.bob/custom_modes.yaml');
    const template = await readFile(config, 'utf8');
    if (!template.includes("fileRegex: '(?!)'")) throw new Error('Missing fail-closed output restriction');
    let configured = template.replace("fileRegex: '(?!)'", 'fileRegex: ' + JSON.stringify(outputPattern(workspace)));
    if (team) {
        await cp(path.join(root, 'team/.bob/agents'), path.join(workspace, '.bob/agents'), { recursive: true });
        configured = configured.replace('    groups:\n', '    allowedSubagents: ' + JSON.stringify(specialists) + '\n    groups:\n')
            .replace('      - skill\n', '      - skill\n      - subagent\n')
            + '      When the user requests specialist delegation, use only the four named\n'
            + '      read-only specialists. Give them the scenario time and source paths.\n'
            + '      They return cited findings; you remain the sole writer. Verify their\n'
            + '      findings against the shared evidence before changing memory or reporting.\n';
    }
    await writeFile(config, configured);
    for (const directory of ['briefs', 'drafts']) await mkdir(path.join(workspace, directory));
    return workspace;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    if (process.argv.length < 3 || process.argv.slice(3).some(arg => arg !== '--team')) {
        throw new Error('Usage: node scripts/prepare.mjs /path/to/new-workspace [--team]');
    }
    console.log(await prepare(process.argv[2], { team: process.argv.includes('--team') }));
}
