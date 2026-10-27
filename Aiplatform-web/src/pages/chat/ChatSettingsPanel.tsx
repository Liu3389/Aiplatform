import { useThemeStore } from '@/stores/themeStore'
import type { AvailableModel, SearchEngine } from '@/types'
import { canUseChatModel } from '@/utils/modelAvailability'

// ===== 可扩展的模型设置 Schema =====
// 按 model code 配置差异化字段；未命中的模型走 default
// 新增模型只需在此表加一条配置，无需改组件代码
interface SettingField {
  key: 'temperature' | 'maxTokens'
  label: string
  labelEn: string
  type: 'slider' | 'number'
  min: number
  max: number
  step: number
  default: number
}

const MODEL_SETTINGS_SCHEMA: Record<string, SettingField[]> = {
  default: [
    { key: 'temperature', label: '随机性', labelEn: 'Temperature', type: 'slider', min: 0, max: 2, step: 0.1, default: 0.7 },
    { key: 'maxTokens', label: '最大输出', labelEn: 'Max Tokens', type: 'number', min: 256, max: 32768, step: 256, default: 8192 },
  ],
}

const SEARCH_ENGINES: Array<{ value: SearchEngine; label: string; labelEn: string; desc: string; descEn: string }> = [
  { value: 'auto', label: '自动', labelEn: 'Auto', desc: 'Tavily(如有Key)→Bing→百度，智能回退', descEn: 'Tavily→Bing→Baidu fallback' },
  { value: 'tavily', label: 'Tavily', labelEn: 'Tavily', desc: 'AI平台同款LLM搜索，需配置Tavily API Key', descEn: 'LLM search, requires Tavily API key' },
  { value: 'searxng', label: 'SearXNG', labelEn: 'SearXNG', desc: '自建聚合搜索（百度+Bing）', descEn: 'Self-hosted (Baidu+Bing)' },
  { value: 'jina', label: 'Jina AI', labelEn: 'Jina AI', desc: '需要配置 Jina API Key', descEn: 'Requires a Jina API key' },
  { value: 'bing', label: 'Bing', labelEn: 'Bing', desc: '直连 Bing 中文搜索', descEn: 'Direct Bing search' },
  { value: 'baidu', label: '百度', labelEn: 'Baidu', desc: '直连百度中文搜索', descEn: 'Direct Baidu search' },
]

interface Props {
  model: string
  availableModels: AvailableModel[]
  onModelChange: (code: string) => void
  searchEngine: SearchEngine
  onSearchEngineChange: (e: SearchEngine) => void
  temperature: number
  onTemperatureChange: (t: number) => void
  maxTokens: number
  onMaxTokensChange: (n: number) => void
  onClose: () => void
  lang: string
  t: (zh: string, en: string) => string
}

export default function ChatSettingsPanel(props: Props) {
  const { theme } = useThemeStore()
  const dark = theme === 'dark'
  const fields = MODEL_SETTINGS_SCHEMA[props.model] ?? MODEL_SETTINGS_SCHEMA.default

  const tx = dark ? { p: '#F1F5F9', d: '#94A3B8', m: '#64748B' } : { p: '#1e293b', d: '#475569', m: '#64748b' }
  const bg = 'var(--bg-secondary)'
  const bgSurface = 'var(--bg-surface)'
  const brd = 'var(--border-color)'
  const accent = 'var(--accent-purple)'

  return (
    <div className="flex h-full flex-col" style={{ background: bg }}>
      {/* 头部 */}
      <div className="flex items-center justify-between px-4 h-12 border-b flex-shrink-0" style={{ borderColor: brd }}>
        <h2 className="text-sm font-semibold" style={{ color: tx.p }}>
          {props.t('设置', 'Settings')}
        </h2>
        <button
          onClick={props.onClose}
          className="p-1.5 rounded-lg transition-colors hover:bg-white/10"
          style={{ color: tx.d }}
          aria-label={props.t('关闭', 'Close')}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      {/* 滚动内容 */}
      <div className="page-scroll flex-1 overflow-y-auto px-4 py-4 space-y-6">
        {/* ===== 模型选择 ===== */}
        <section>
          <label className="text-[11px] font-medium uppercase tracking-wider" style={{ color: tx.m }}>
            {props.t('模型', 'Model')}
          </label>
          <div className="mt-2 space-y-1">
            {props.availableModels.length === 0 ? (
              <div className="text-xs py-2" style={{ color: tx.m }}>{props.t('加载中...', 'Loading...')}</div>
            ) : props.availableModels.map((m) => {
              const modelKey = m.displayName || m.modelCode
              const isSelected = modelKey === props.model
              const isLocked = !m.accessible
              const isUnavailable = !m.chatEnabled
              const isDisabled = !canUseChatModel(m)
              return (
                <button
                  key={m.modelId}
                  onClick={() => { if (!isDisabled) props.onModelChange(modelKey) }}
                  disabled={isDisabled}
                  className={`w-full text-left px-3 py-2 rounded-xl transition-all ${isDisabled ? 'opacity-50 cursor-not-allowed' : ''}`}
                  style={{
                    background: isSelected ? 'var(--bg-active)' : 'transparent',
                    border: `1px solid ${isSelected ? accent + '30' : 'transparent'}`,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div className="min-w-0 flex-1 mr-2">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-medium truncate" style={{ color: tx.p }}>{m.displayName}</span>
                        {isLocked && (
                          <span className="text-[9px] px-1 rounded flex-shrink-0" style={{ background: 'rgba(245,158,11,0.15)', color: '#F59E0B' }}>
                            {m.requiredLevelName || props.t('需升级', 'Upgrade')}
                          </span>
                        )}
                        {isUnavailable && (
                          <span className="text-[9px] px-1 rounded flex-shrink-0" style={{ background: 'var(--bg-surface)', color: tx.m }}>
                            {props.t('暂未接入', 'Not connected')}
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] mt-0.5" style={{ color: tx.m }}>
                        {m.provider}{m.fixedPoints > 0 ? <span style={{ color: accent }}> · {m.fixedPoints}{props.t('点/次', 'pts/call')}</span> : null}
                      </div>
                      {isUnavailable && (
                        <div className="text-[10px] mt-0.5" style={{ color: tx.m }}>
                          {props.t('模型服务尚未完成配置', 'Model service is not configured yet')}
                        </div>
                      )}
                    </div>
                    {isSelected && (
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={accent} strokeWidth="3" className="flex-shrink-0">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    )}
                  </div>
                </button>
              )
            })}
          </div>
        </section>

        {/* ===== 搜索引擎选择 ===== */}
        <section>
          <label className="text-[11px] font-medium uppercase tracking-wider" style={{ color: tx.m }}>
            {props.t('搜索引擎', 'Search Engine')}
          </label>
          <div className="mt-2 space-y-1.5">
            {SEARCH_ENGINES.map((opt) => {
              const isSelected = props.searchEngine === opt.value
              return (
                <button
                  key={opt.value}
                  onClick={() => props.onSearchEngineChange(opt.value)}
                  className="w-full text-left px-3 py-2 rounded-xl transition-all"
                  style={{
                    background: isSelected ? 'var(--bg-active)' : bgSurface,
                    border: `1px solid ${isSelected ? accent + '30' : brd}`,
                  }}
                >
                  <div className="flex items-center gap-2">
                    <div
                      className="w-4 h-4 rounded-full border-2 flex-shrink-0 flex items-center justify-center"
                      style={{ borderColor: isSelected ? accent : tx.m }}
                    >
                      {isSelected && <div className="w-2 h-2 rounded-full" style={{ background: accent }} />}
                    </div>
                    <div className="min-w-0">
                      <div className="text-xs font-medium" style={{ color: tx.p }}>
                        {props.lang === 'zh' ? opt.label : opt.labelEn}
                      </div>
                      <div className="text-[10px] mt-0.5" style={{ color: tx.m }}>
                        {props.lang === 'zh' ? opt.desc : opt.descEn}
                      </div>
                    </div>
                  </div>
                </button>
              )
            })}
          </div>
        </section>

        {/* ===== 生成参数（schema 驱动，按模型差异化） ===== */}
        {fields.map((f) => (
          <section key={f.key}>
            <div className="flex items-center justify-between">
              <label className="text-[11px] font-medium uppercase tracking-wider" style={{ color: tx.m }}>
                {props.lang === 'zh' ? f.label : f.labelEn}
              </label>
              <span className="text-xs font-bold tabular-nums" style={{ color: accent }}>
                {f.key === 'temperature' ? props.temperature.toFixed(1) : props.maxTokens}
              </span>
            </div>
            {f.type === 'slider' ? (
              <input
                type="range"
                min={f.min}
                max={f.max}
                step={f.step}
                value={props.temperature}
                onChange={(e) => props.onTemperatureChange(Number(e.target.value))}
                className="mt-2 w-full"
                style={{ accentColor: accent }}
              />
            ) : (
              <input
                type="number"
                min={f.min}
                max={f.max}
                step={f.step}
                value={props.maxTokens}
                onChange={(e) => props.onMaxTokensChange(Number(e.target.value))}
                className="mt-2 w-full px-3 py-1.5 rounded-lg text-xs outline-none"
                style={{ background: bgSurface, color: tx.p, border: `1px solid ${brd}` }}
              />
            )}
            <div className="flex justify-between mt-1 text-[9px]" style={{ color: tx.m }}>
              <span>{f.min}</span>
              <span>{f.max}</span>
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}
