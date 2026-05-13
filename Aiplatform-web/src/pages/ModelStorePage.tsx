import { useState } from 'react'
import { motion } from 'framer-motion'
import { useI18nStore } from '@/stores/i18nStore'

const MOCK_PRODUCTS = [
  { id: 1, provider: 'OpenAI', name: 'GPT-4o API Key', price: 5.00, tokenPrice: '$0.005/1K input', desc: '最先进的多模态模型', features: ['128K上下文', '函数调用', '视觉识别'], icon: '🤖' },
  { id: 2, provider: 'Anthropic', name: 'Claude 3.5 API Key', price: 8.00, tokenPrice: '$3/1M input', desc: '安全可靠的对话模型', features: ['200K上下文', '代码生成', '安全对齐'], icon: '🧠' },
  { id: 3, provider: 'DeepSeek', name: 'DeepSeek V3 API Key', price: 2.00, tokenPrice: '$0.27/1M input', desc: '高性价比国产模型', features: ['64K上下文', '流式输出', '中文优化'], icon: '🇨🇳' },
  { id: 4, provider: 'Moonshot', name: 'Kimi API Key', price: 3.00, tokenPrice: '按量计费', desc: '超长上下文模型', features: ['128K上下文', '文档分析', '联网搜索'], icon: '🚀' },
  { id: 5, provider: '智谱', name: 'GLM-4 API Key', price: 3.50, tokenPrice: '¥0.1/1K tokens', desc: '清华智谱旗舰模型', features: ['128K上下文', '多模态', '工具调用'], icon: '🏫' },
  { id: 6, provider: '硅基流动', name: 'SiliconFlow API Key', price: 1.00, tokenPrice: '¥0.42/1M tokens', desc: '一站式模型聚合平台', features: ['多模型聚合', '统一计费', '低延迟'], icon: '⚡' },
]

export default function ModelStorePage() {
  const { lang } = useI18nStore()
  const [selected, setSelected] = useState<number | null>(null)

  return (
    <div className="p-4 lg:p-6 max-w-5xl mx-auto overflow-y-auto h-full">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? 'API Key 商城' : 'API Key Store'}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '选购各大 AI 服务商的 API Key，即买即用' : 'Purchase API keys from major AI providers'}
        </p>
      </motion.div>

      <div className="mt-6 mb-8 p-6 rounded-2xl border text-center" style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)' }}>
        <div className="text-4xl mb-3">🏗️</div>
        <h3 className="text-lg font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
          {lang === 'zh' ? '商城即将上线' : 'Store Coming Soon'}
        </h3>
        <p className="text-sm" style={{ color: 'var(--text-dim)' }}>
          {lang === 'zh' ? '我们正在对接各大 AI 服务商的 API Key 分销渠道，稍后您可以直接在这里购买' : 'We are integrating API key distribution channels. You will be able to purchase directly here soon.'}
        </p>
      </div>

      <h3 className="text-base font-semibold mb-4" style={{ color: 'var(--text-primary)' }}>
        {lang === 'zh' ? '即将上架' : 'Coming Soon'}
      </h3>

      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
        {MOCK_PRODUCTS.map((product, i) => (
          <motion.div
            key={product.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.05 }}
            onClick={() => setSelected(selected === product.id ? null : product.id)}
            className="rounded-2xl p-5 border cursor-pointer transition-all"
            style={{
              background: selected === product.id ? 'var(--bg-surface)' : 'var(--bg-card)',
              borderColor: selected === product.id ? 'var(--accent-purple)' : 'var(--border-color)',
            }}
          >
            <div className="flex items-start justify-between mb-3">
              <span className="text-2xl">{product.icon}</span>
              <span className="text-lg font-bold" style={{ color: 'var(--accent-emerald)' }}>${product.price}</span>
            </div>
            <h4 className="text-sm font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{product.name}</h4>
            <p className="text-xs mb-2" style={{ color: 'var(--text-dim)' }}>{product.desc}</p>
            <p className="text-[10px] mb-3 font-mono" style={{ color: 'var(--text-dim)', opacity: 0.6 }}>{product.tokenPrice}</p>
            {selected === product.id && (
              <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} className="space-y-2">
                <div className="flex flex-wrap gap-1.5">
                  {product.features.map((f, j) => (
                    <span key={j} className="text-[10px] px-2 py-0.5 rounded-md" style={{ background: 'var(--accent-purple)' + '15', color: 'var(--accent-purple)' }}>
                      {f}
                    </span>
                  ))}
                </div>
                <div className="pt-2 border-t" style={{ borderColor: 'var(--border-color)' }}>
                  <p className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {lang === 'zh' ? '暂未开放购买，敬请期待' : 'Not available for purchase yet'}
                  </p>
                </div>
              </motion.div>
            )}
            {!selected || selected !== product.id ? (
              <p className="text-[10px]" style={{ color: 'var(--text-dim)', opacity: 0.4 }}>
                {lang === 'zh' ? '点击查看详情' : 'Click for details'}
              </p>
            ) : null}
          </motion.div>
        ))}
      </div>
    </div>
  )
}
