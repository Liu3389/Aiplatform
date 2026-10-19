import assert from 'node:assert/strict'
import test from 'node:test'
import {
  AUTH_SESSION_EXPIRED_NOTICE,
  AUTH_SESSION_NOTICE_KEY,
  consumeAuthSessionNotice,
  recordAuthSessionNotice,
  recoverUnauthorized,
} from '../src/services/authResponseRecovery.ts'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  }
}

test('refreshed token retries the original request exactly once without expiring the session', async () => {
  let attempts = 0
  let expired = 0
  const result = await recoverUnauthorized(
    { code: 401, message: 'token expired' },
    async () => { attempts += 1; return { code: 200, data: 'ok' } },
    async () => true,
    () => { expired += 1 },
  )
  assert.deepEqual(result, { code: 200, data: 'ok' })
  assert.equal(attempts, 1)
  assert.equal(expired, 0)
})

test('still-invalid refreshed token expires the session once', async () => {
  let expired = 0
  const result = await recoverUnauthorized(
    { code: 401, message: 'old session' },
    async () => ({ code: 401, message: 'revoked' }),
    async () => true,
    () => { expired += 1 },
  )
  assert.equal(result.message, 'revoked')
  assert.equal(expired, 1)
})

test('HTTP 401 thrown by the retried request expires the session', async () => {
  let expired = 0
  const unauthorized = Object.assign(new Error('HTTP 401'), { code: 401 })
  const result = await recoverUnauthorized(
    { code: 401, message: 'old session' },
    async () => { throw unauthorized },
    async () => true,
    () => { expired += 1 },
  )
  assert.equal(result.message, 'old session')
  assert.equal(expired, 1)
})

test('non-auth retry errors do not expire the session', async () => {
  let expired = 0
  const serverError = Object.assign(new Error('HTTP 503'), { code: 503 })
  await assert.rejects(
    recoverUnauthorized(
      { code: 401, message: 'old session' },
      async () => { throw serverError },
      async () => true,
      () => { expired += 1 },
    ),
    serverError,
  )
  assert.equal(expired, 0)
})

test('failed token refresh expires the session and returns the original unauthorized response', async () => {
  let retried = false
  let expired = 0
  const result = await recoverUnauthorized(
    { code: 401, message: 'legacy token has no device binding' },
    async () => { retried = true; return { code: 200 } },
    async () => false,
    () => { expired += 1 },
  )
  assert.equal(result.message, 'legacy token has no device binding')
  assert.equal(retried, false)
  assert.equal(expired, 1)
})

test('locked-account response preserves the session and skips refresh', async () => {
  let refreshed = false
  let expired = false
  const result = await recoverUnauthorized(
    { code: 401, message: '账户已锁定' },
    async () => ({ code: 200 }),
    async () => { refreshed = true; return true },
    () => { expired = true },
  )
  assert.equal(result.message, '账户已锁定')
  assert.equal(refreshed, false)
  assert.equal(expired, false)
})

test('expiration notice is shown once and then consumed', () => {
  const storage = memoryStorage()
  recordAuthSessionNotice(storage)
  assert.equal(storage.getItem(AUTH_SESSION_NOTICE_KEY), AUTH_SESSION_EXPIRED_NOTICE)
  assert.equal(consumeAuthSessionNotice(storage), AUTH_SESSION_EXPIRED_NOTICE)
  assert.equal(consumeAuthSessionNotice(storage), '')
})
