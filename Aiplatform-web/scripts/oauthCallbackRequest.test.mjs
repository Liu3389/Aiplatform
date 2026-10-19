import assert from 'node:assert/strict'
import test from 'node:test'
import { runOnceWhilePending } from '../src/utils/runOnceWhilePending.ts'

test('concurrent callback renders share one state-consuming request', async () => {
  let calls = 0
  let resolveOperation
  const operation = () => {
    calls += 1
    return new Promise(resolve => { resolveOperation = resolve })
  }

  const first = runOnceWhilePending('oauth:google:state-1', operation)
  const second = runOnceWhilePending('oauth:google:state-1', operation)
  assert.equal(first, second)

  await Promise.resolve()
  assert.equal(calls, 1)
  resolveOperation({ token: 'test-token' })
  assert.deepEqual(await Promise.all([first, second]), [
    { token: 'test-token' },
    { token: 'test-token' },
  ])
})

test('a failed callback request clears its key so the user can retry', async () => {
  let calls = 0
  await assert.rejects(runOnceWhilePending('oauth:google:state-2', async () => {
    calls += 1
    throw new Error('temporary network error')
  }), /temporary network error/)

  const retry = await runOnceWhilePending('oauth:google:state-2', async () => {
    calls += 1
    return 'retried'
  })
  assert.equal(retry, 'retried')
  assert.equal(calls, 2)
})
