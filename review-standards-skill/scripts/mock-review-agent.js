#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const [, , prompt = '', optionsJson = '{}', contextJson = '{}'] = process.argv;

function parseJson(value, fallback) {
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

const options = parseJson(optionsJson, {});
const promptfooData = parseJson(contextJson, {});
const sampleDir = options.config?.sampleDir || options.sampleDir || 'samples/v1';
const skillPath = path.join(sampleDir, '.agents', 'skills', 'review-standards', 'SKILL.md');
const sourcePath = path.join(sampleDir, 'src', 'auth.ts');

const skill = fs.existsSync(skillPath) ? fs.readFileSync(skillPath, 'utf8') : '';
const source = fs.readFileSync(sourcePath, 'utf8');
const request = promptfooData.vars?.request || prompt;

const hasTimingRule = /constant-time|timing-safe|timing attack/i.test(skill);
const hasRequestLimitRule = /asked for|only/i.test(skill);
const passwordOnly = /only for password/i.test(request);

const issues = [];
const evidence = [];

if (/sha1/i.test(source)) {
  if (skill) {
    issues.push({
      id: 'weak-password-hash',
      severity: 'high',
      file: 'src/auth.ts',
    });
    evidence.push('src/auth.ts hashes passwords with SHA-1');
  }
}

if ((!passwordOnly || !hasRequestLimitRule) && hasTimingRule && /===/.test(source)) {
  issues.push({
    id: 'timing-unsafe-compare',
    severity: 'medium',
    file: 'src/auth.ts',
  });
  evidence.push('src/auth.ts compares reset tokens with ===');
}

process.stdout.write(JSON.stringify({
  summary: `${path.basename(sampleDir)} found ${issues.length} issue(s)`,
  skillUsed: skill.includes('review-standards'),
  issues,
  evidence,
  approvalPoints: [],
}));
