import test from 'node:test'
import assert from 'node:assert/strict'
import { canUseChatModel, selectChatModel } from '../src/utils/modelAvailability.ts'

const model = (displayName, { accessible = true, chatEnabled = true } = {}) => ({
  modelId: displayName,
  displayName,
  modelCode: displayName.toLowerCase(),
  provider: 'deepseek',
  fixedPoints: 1,
  supportsVision: 0,
  sortOrder: 0,
  chatEnabled,
  accessible,
  requiredLevel: 0,
  requiredLevelName: null,
  upgradeHint: null,
})

test('a model requires both access permission and configured upstream to be selectable', () => {
  assert.equal(canUseChatModel(model('ready')), true)
  assert.equal(canUseChatModel(model('missing-key', { chatEnabled: false })), false)
  assert.equal(canUseChatModel(model('locked', { accessible: false })), false)
  assert.equal(canUseChatModel(undefined), false)
})

test('saved selection is kept only when that model is usable', () => {
  const models = [
    model('missing-key', { chatEnabled: false }),
    model('member-only', { accessible: false }),
    model('local-mock'),
  ]

  assert.equal(selectChatModel(models, 'missing-key')?.displayName, 'local-mock')
  assert.equal(selectChatModel(models, 'LOCAL-MOCK')?.displayName, 'local-mock')
})

test('no configured and accessible model produces no selection', () => {
  assert.equal(selectChatModel([
    model('missing-key', { chatEnabled: false }),
    model('locked', { accessible: false }),
  ], 'missing-key'), null)
})
