import assert from 'node:assert/strict'
import test from 'node:test'
import { readHttpApiError } from '../src/services/httpApiError.ts'

test('preserves API error message and data for HTTP failures', async () => {
  const response = new Response(JSON.stringify({
    code: 429,
    message: '验证码请求过于频繁，请稍后再试',
    data: { retryAfterSeconds: 60 },
  }), {
    status: 429,
    statusText: 'Too Many Requests',
    headers: { 'Content-Type': 'application/json' },
  })

  assert.deepEqual(await readHttpApiError(response), {
    message: '验证码请求过于频繁，请稍后再试',
    data: { retryAfterSeconds: 60 },
  })
})

test('uses HTTP status text when an error response is not JSON', async () => {
  const response = new Response('upstream unavailable', {
    status: 503,
    statusText: 'Service Unavailable',
  })

  assert.deepEqual(await readHttpApiError(response), {
    message: 'HTTP 503: Service Unavailable',
  })
})
