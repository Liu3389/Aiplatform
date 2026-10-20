import assert from 'node:assert/strict'
import test from 'node:test'
import { runOnceWhilePending } from '../src/utils/runOnceWhilePending.ts'
import { oauthCallbackErrorMessage } from '../src/utils/oauthCallbackError.ts'

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

test('expired state tells the user to restart authorization instead of reusing the callback', () => {
  const message = oauthCallbackErrorMessage({ code: 400, message: 'OAuth state 无效或已过期' })
  assert.match(message, /重新点击 Google 登录/)
  assert.match(message, /不要刷新/)
})

test('provider network failure remains actionable and visible', () => {
  const message = oauthCallbackErrorMessage({ code: 503, message: 'Google 登录服务暂时不可用，请稍后重试' })
  assert.match(message, /Google 登录服务暂时不可用/)
})
