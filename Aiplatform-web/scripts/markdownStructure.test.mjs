import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeCompactListLine, normalizeCompactTableLine } from '../src/utils/markdownStructure.ts'

test('a standalone GFM separator row is preserved and does not create an empty header', () => {
  const separator = '| --- | --- |'
  assert.equal(normalizeCompactTableLine(separator), separator)
})

test('a compact table row with its header and separator on one line is expanded', () => {
  assert.equal(
    normalizeCompactTableLine('| Status | Meaning | --- | --- | ok | Ready |'),
    '| Status | Meaning |\n| --- | --- |\n| ok | Ready |',
  )
})

test('a compact bullet list is split into separate items', () => {
  assert.equal(normalizeCompactListLine('- A- B- C'), '- A\n- B\n- C')
  assert.equal(normalizeCompactListLine('- A - B - C'), '- A\n- B\n- C')
})

test('hyphens inside a list item URL are preserved', () => {
  const line = '- 查看 [产品链接](https://lingdian-ai.top/welcome)'
  assert.equal(normalizeCompactListLine(line), line)
})
