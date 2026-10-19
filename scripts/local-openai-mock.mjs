import http from 'node:http'

let requests = 0
let streamingRequests = 0
let lastTokenLimitKey = null
let lastTokenLimit = null
let lastEndpoint = null
let lastAuthorizationPresent = false

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ status: 'ok', localOnly: true }))
    return
  }

  if (req.method === 'GET' && req.url === '/stats') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ requests, streamingRequests, lastTokenLimitKey, lastTokenLimit, lastEndpoint, lastAuthorizationPresent }))
    return
  }

  if (req.method !== 'POST' || !['/v1/chat/completions', '/responses'].includes(req.url)) {
    res.writeHead(404, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ error: { message: 'not found' } }))
    return
  }

  let raw = ''
  for await (const chunk of req) raw += chunk
  let payload
  try {
    payload = JSON.parse(raw)
  } catch {
    res.writeHead(400, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ error: { message: 'invalid JSON' } }))
    return
  }

  requests += 1
  lastEndpoint = req.url
  lastAuthorizationPresent = Boolean(req.headers.authorization)
  const tokenLimitKey = ['max_tokens', 'max_completion_tokens', 'max_output_tokens']
    .find((key) => Object.hasOwn(payload, key))
  lastTokenLimitKey = tokenLimitKey || null
  lastTokenLimit = tokenLimitKey ? payload[tokenLimitKey] : null
  if (payload.stream) streamingRequests += 1

  if (req.url === '/responses') {
    const text = payload.stream ? 'LOCAL_DEEPSEEK_STREAM_OK' : 'LOCAL_DEEPSEEK_OK'
    const output = [{
      id: `msg-local-${requests}`,
      type: 'message',
      status: 'completed',
      role: 'assistant',
      content: [{ type: 'output_text', text, annotations: [] }],
    }]
    const usage = { input_tokens: 4, output_tokens: 2, total_tokens: 6 }
    res.writeHead(200, {
      'content-type': payload.stream ? 'text/event-stream; charset=utf-8' : 'application/json',
      'cache-control': 'no-cache',
      connection: 'keep-alive',
    })
    if (payload.stream) {
      const send = (type, data) => res.write(`event: ${type}\ndata: ${JSON.stringify({ type, ...data })}\n\n`)
      send('response.output_text.delta', { delta: text })
      send('response.output_text.done', { text })
      send('response.completed', { response: { status: 'completed', output, usage } })
      res.end()
    } else {
      res.end(JSON.stringify({ id: `resp-local-${requests}`, object: 'response', status: 'completed', output, usage }))
    }
    return
  }

  if (payload.stream) {
    res.writeHead(200, {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-cache',
      connection: 'keep-alive',
    })
    const id = `chatcmpl-local-${requests}`
    const send = (data) => res.write(`data: ${JSON.stringify(data)}\n\n`)
    send({ id, object: 'chat.completion.chunk', choices: [{ index: 0, delta: { role: 'assistant' }, finish_reason: null }] })
    send({ id, object: 'chat.completion.chunk', choices: [{ index: 0, delta: { content: 'LOCAL_STREAM_OK' }, finish_reason: null }] })
    send({ id, object: 'chat.completion.chunk', choices: [{ index: 0, delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 4, completion_tokens: 2, total_tokens: 6 } })
    res.write('data: [DONE]\n\n')
    res.end()
    return
  }

  res.writeHead(200, { 'content-type': 'application/json' })
  res.end(JSON.stringify({
    id: `chatcmpl-local-${requests}`,
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model: payload.model || 'local-chat-smoke',
    choices: [{ index: 0, message: { role: 'assistant', content: 'LOCAL_CHAT_OK' }, finish_reason: 'stop' }],
    usage: { prompt_tokens: 4, completion_tokens: 2, total_tokens: 6 },
  }))
})

server.listen(19090, '127.0.0.1', () => {
  console.log('local OpenAI-compatible mock listening on 127.0.0.1:19090')
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
