import { useState, useEffect } from 'react'
import { userApiKeyApi, type UserApiKeyItem } from '@/services/api'
import { motion, AnimatePresence } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'

const PROVIDERS = [
  { id: 'openai', name: 'OpenAI', placeholder: 'sk-proj-...', baseUrl: 'https://api.openai.com', tip: '以 sk-proj- 或 sk- 开头' },
  { id: 'deepseek', name: 'DeepSeek', placeholder: 'sk-...', baseUrl: 'https://api.deepseek.com', tip: '以 sk- 开头' },
  { id: 'anthropic', name: 'Anthropic', placeholder: 'sk-ant-...', baseUrl: 'https://api.anthropic.com', tip: '以 sk-ant- 开头' },
  { id: 'moonshot', name: 'Moonshot', placeholder: 'sk-...', baseUrl: 'https://api.moonshot.cn', tip: '以 sk- 开头' },
  { id: 'zhipu', name: '智谱 GLM', placeholder: '...', baseUrl: 'https://open.bigmodel.cn', tip: '以 . 结尾的 API Key' },
  { id: 'qwen', name: '通义千问', placeholder: 'sk-...', baseUrl: 'https://dashscope.aliyuncs.com', tip: '以 sk- 开头' },
  { id: 'siliconflow', name: '硅基流动', placeholder: 'sk-...', baseUrl: 'https://api.siliconflow.cn', tip: '以 sk- 开头' },
]

export default function ApiKeysPage() {
  const { lang } = useI18nStore()
  const [keys, setKeys] = useState<UserApiKeyItem[]>([])
  const [loading, setLoading] = useState(true)
  const [showAdd, setShowAdd] = useState(false)
  const [form, setForm] = useState({ provider: 'openai', displayName: '', apiKey: '', baseUrl: '' })
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const loadKeys = async () => {
    try {
      const list = await userApiKeyApi.list()
      setKeys(list || [])
    } catch {}
    setLoading(false)
  }

  useEffect(() => { loadKeys() }, [])

  const showMsg = (type: 'success' | 'error', text: string) => {
    setMessage({ type, text })
    setTimeout(() => setMessage(null), 3000)
  }

  const handleTest = async () => {
    if (!form.apiKey.trim()) { showMsg('error', lang === 'zh' ? '请先输入 API Key' : 'Please enter an API Key'); return }
    setTesting(true)
    setTestResult(null)
    try {
      await userApiKeyApi.test({ provider: form.provider, apiKey: form.apiKey, baseUrl: form.baseUrl || undefined })
      setTestResult({ ok: true, msg: lang === 'zh' ? '连接测试通过！' : 'Connection test passed!' })
    } catch (err: any) {
      setTestResult({ ok: false, msg: (err.message || String(err)).replace('ApiError: ', '') })
    }
    setTesting(false)
  }

  const handleAdd = async () => {
    if (!form.apiKey.trim()) return showMsg('error', lang === 'zh' ? '请输入 API Key' : 'Please enter API Key')
    setLoading(true)
    try {
      const result = await userApiKeyApi.add({
        provider: form.provider,
        displayName: form.displayName || undefined,
        apiKey: form.apiKey,
        baseUrl: form.baseUrl || undefined,
      })
      showMsg('success', lang === 'zh' ? '添加成功' : 'Added successfully')
      setShowAdd(false)
      setForm({ provider: 'openai', displayName: '', apiKey: '', baseUrl: '' })
      setTestResult(null)
      loadKeys()
    } catch (err: any) {
      showMsg('error', (err.message || String(err)).replace('ApiError: ', ''))
    }
    setLoading(false)
  }

  const handleDelete = async (id: number) => {
    try {
      await userApiKeyApi.delete(id)
      showMsg('success', lang === 'zh' ? '已删除' : 'Deleted')
      loadKeys()
    } catch (err: any) {
      showMsg('error', (err.message || String(err)).replace('ApiError: ', ''))
    }
  }

  const providerColors: Record<string, string> = {
    openai: '#10A37F', deepseek: '#4D6BFE', anthropic: '#D97706', moonshot: '#6366F1',
    zhipu: '#0066FF', qwen: '#6166DC', siliconflow: '#7C3AED',
  }

  return (
    <div className="p-4 lg:p-6 max-w-4xl mx-auto overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
              {lang === 'zh' ? '我的 API Keys' : 'My API Keys'}
            </h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              {lang === 'zh' ? '存储你的第三方 API Key，在自定义模型对话中使用' : 'Store your 3rd-party API keys for custom model chat'}
            </p>
          </div>
          <button
            onClick={() => setShowAdd(true)}
            className="px-4 py-2 rounded-xl text-sm font-medium text-white transition-all"
            style={{ background: 'var(--accent-purple)' }}
          >
            + {lang === 'zh' ? '添加 Key' : 'Add Key'}
          </button>
        </div>
      </motion.div>

      <AnimatePresence>
        {message && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="mb-4 px-4 py-3 rounded-xl text-sm font-medium"
            style={{
              background: message.type === 'success' ? 'rgba(16,185,129,0.12)' : 'rgba(244,63,94,0.12)',
              color: message.type === 'success' ? '#10B981' : '#F43F5E',
              border: '1px solid ' + (message.type === 'success' ? 'rgba(16,185,129,0.25)' : 'rgba(244,63,94,0.25)')
            }}
          >
            {message.text}
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {showAdd && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden mb-6"
          >
            <div className="rounded-2xl p-6 border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
              <h3 className="text-base font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
                {lang === 'zh' ? '添加 API Key' : 'Add API Key'}
              </h3>

              <div className="space-y-4">
                <div>
                  <label className="block text-xs mb-1.5" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '服务商' : 'Provider'}
                  </label>
                  <div className="grid grid-cols-4 gap-2">
                    {PROVIDERS.map(p => (
                      <button
                        key={p.id}
                        onClick={() => setForm({ ...form, provider: p.id, baseUrl: '' })}
                        className="py-2 px-2 rounded-lg text-xs font-medium transition-all border"
                        style={{
                          background: form.provider === p.id ? (providerColors[p.id] || 'var(--accent-purple)') + '20' : 'var(--bg-surface)',
                          borderColor: form.provider === p.id ? providerColors[p.id] || 'var(--accent-purple)' : 'var(--border-color)',
                          color: form.provider === p.id ? providerColors[p.id] || 'var(--accent-purple)' : 'var(--text-dim)',
                        }}
                      >
                        {p.name}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-xs mb-1.5" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '显示名称' : 'Display Name'} ({lang === 'zh' ? '可选' : 'optional'})
                  </label>
                  <input
                    value={form.displayName}
                    onChange={e => setForm({ ...form, displayName: e.target.value })}
                    placeholder={form.provider.charAt(0).toUpperCase() + form.provider.slice(1)}
                    className="w-full px-4 py-2.5 rounded-xl text-sm outline-none"
                    style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
                  />
                </div>

                <div>
                  <label className="block text-xs mb-1.5" style={{ color: 'var(--text-dim)' }}>
                    API Key <span style={{ color: '#F43F5E' }}>*</span>
                  </label>
                  <input
                    value={form.apiKey}
                    onChange={e => setForm({ ...form, apiKey: e.target.value })}
                    placeholder={PROVIDERS.find(p => p.id === form.provider)?.placeholder || 'sk-...'}
                    type="password"
                    className="w-full px-4 py-2.5 rounded-xl text-sm outline-none font-mono"
                    style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
                  />
                </div>

                <div>
                  <label className="block text-xs mb-1.5" style={{ color: 'var(--text-dim)' }}>
                    Base URL ({lang === 'zh' ? '可选，默认自动识别' : 'optional, auto-detected'})
                  </label>
                  <input
                    value={form.baseUrl}
                    onChange={e => setForm({ ...form, baseUrl: e.target.value })}
                    placeholder={PROVIDERS.find(p => p.id === form.provider)?.baseUrl || 'https://api.openai.com'}
                    className="w-full px-4 py-2.5 rounded-xl text-sm outline-none font-mono"
                    style={{ background: 'var(--bg-input)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
                  />
                </div>

                {testResult && (
                  <div
                    className="px-4 py-3 rounded-xl text-sm"
                    style={{
                      background: testResult.ok ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)',
                      color: testResult.ok ? '#10B981' : '#F43F5E',
                      border: '1px solid ' + (testResult.ok ? 'rgba(16,185,129,0.2)' : 'rgba(244,63,94,0.2)'),
                    }}
                  >
                    {testResult.msg}
                  </div>
                )}

                <div className="flex gap-3">
                  <button
                    onClick={handleTest}
                    disabled={testing || !form.apiKey.trim()}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium transition-all disabled:opacity-40"
                    style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)', border: '1px solid var(--border-color)' }}
                  >
                    {testing ? (lang === 'zh' ? '测试中...' : 'Testing...') : (lang === 'zh' ? '测试连接' : 'Test Connection')}
                  </button>
                  <button
                    onClick={handleAdd}
                    disabled={!form.apiKey.trim() || loading}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-40"
                    style={{ background: 'var(--accent-purple)' }}
                  >
                    {loading ? (lang === 'zh' ? '添加中...' : 'Adding...') : (lang === 'zh' ? '添加' : 'Add')}
                  </button>
                </div>
                <button
                  onClick={() => { setShowAdd(false); setTestResult(null); setForm({ provider: 'openai', displayName: '', apiKey: '', baseUrl: '' }) }}
                  className="w-full py-2 rounded-xl text-xs transition-colors"
                  style={{ color: 'var(--text-dim)' }}
                >
                  {lang === 'zh' ? '取消' : 'Cancel'}
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {keys.length === 0 && !loading ? (
        <div className="text-center py-16 px-4 rounded-2xl border" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" strokeWidth="1.5" className="mx-auto mb-4 opacity-30">
            <path d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
          </svg>
          <p className="text-sm" style={{ color: 'var(--text-dim)' }}>
            {lang === 'zh' ? '还没有存储的 API Key' : 'No API keys stored yet'}
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--text-dim)', opacity: 0.6 }}>
            {lang === 'zh' ? '点击上方按钮添加你的 OpenAI/DeepSeek/Anthropic 等 API Key' : 'Click the button above to add your API keys'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {keys.map(key => (
            <motion.div
              key={key.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex items-center justify-between p-4 rounded-xl border"
              style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}
            >
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center text-xs font-bold"
                  style={{ background: (providerColors[key.provider] || '#6366F1') + '20', color: providerColors[key.provider] || '#6366F1' }}
                >
                  {key.provider.slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                    {key.displayName || key.provider}
                  </p>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-[10px] px-1.5 py-0.5 rounded-md font-mono" style={{ background: 'var(--bg-surface)', color: 'var(--text-dim)' }}>
                      {key.provider}
                    </span>
                    <span className="text-[10px] font-mono" style={{ color: 'var(--text-dim)', opacity: 0.6 }}>
                      {key.apiKey}
                    </span>
                  </div>
                </div>
              </div>
              <button
                onClick={() => handleDelete(key.id)}
                className="px-3 py-1.5 rounded-lg text-xs font-medium transition-colors"
                style={{ background: 'rgba(244,63,94,0.1)', color: '#F43F5E' }}
              >
                {lang === 'zh' ? '删除' : 'Delete'}
              </button>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  )
}
