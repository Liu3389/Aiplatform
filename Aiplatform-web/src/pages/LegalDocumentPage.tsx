import { Link, useLocation } from 'react-router-dom'
import { useThemeStore } from '@/stores/themeStore'

type LegalSection = {
  id: string
  title: string
  paragraphs?: string[]
  bullets?: string[]
}

type LegalDocument = {
  eyebrow: string
  title: string
  intro: string
  icon: string
  sections: LegalSection[]
}

const privacy: LegalDocument = {
  eyebrow: '隐私与数据',
  title: '隐私政策',
  intro: '本草案说明凌点 AI 在提供账号、AI 对话和相关平台服务时可能处理的信息，以及目前已在代码中核实的处理方式。',
  icon: '◈',
  sections: [
    {
      id: 'operator',
      title: '1. 运营主体与适用范围',
      paragraphs: [
        '本服务的产品名称为“凌点 AI”。拟议运营主体名称为“武汉凌点人工智能科技有限公司”，这只是名称候选，尚未核实工商登记或其是否为本服务实际运营者；正式运营主体必须以真实登记信息及网站备案主体为准。确认前，本页仅供本地审阅，不构成正式发布的隐私政策。',
        '本政策适用于 lingdian-ai.top 网站及凌点 AI 提供的账号、模型调用、对话、文件处理和相关功能。',
      ],
    },
    {
      id: 'data',
      title: '2. 处理的信息',
      bullets: [
        '账号与登录：用户名、密码的安全校验信息、邮箱或手机号及其验证状态；使用 Google 登录时，平台接收 Google 的用户标识、名称以及已验证邮箱，不接收 Google 账号密码。',
        '服务内容：你提交的提示词、对话内容、模型选择、上传文件及其解析结果，以及用于保存、同步和删除对话所需的信息。',
        '使用与账务：模型调用用量、点数余额、计费及结算流水，用于展示用量、计算点数和处理账务问题。',
        '安全与运行：登录设备/会话标识、认证状态及必要的操作和错误日志，用于维持登录、排查故障和防止滥用。',
        '浏览器存储：网站会在本地或会话存储中保存登录状态、界面偏好、必要缓存，以及当前选用的 API Key 等会话信息；OAuth 登录会使用短期浏览器绑定信息。',
      ],
    },
    {
      id: 'purpose',
      title: '3. 使用目的',
      paragraphs: ['平台将上述信息用于创建和保护账号、响应 AI 请求、处理文件、同步对话、计算点数、提供安全控制、排查服务故障，以及回应用户请求。平台不会把 Google 登录信息用于与登录和账号管理无关的目的。'],
    },
    {
      id: 'ai-processing',
      title: '4. AI 请求与第三方服务',
      paragraphs: [
        '当你选择模型并发送请求时，平台会将完成该请求所需的提示词、上下文和附件内容发送给对应的模型服务提供方。当前可用模型及供应方以产品页面实际显示为准；不同供应方可能按其自身政策处理收到的内容。',
        'Google 仅用于你主动选择的 Google 身份验证流程。请勿在提示词、上传文件或对话中提交密码、API Key、支付信息或其他不必要的敏感资料。',
      ],
    },
    {
      id: 'retention',
      title: '5. 保存期限与删除',
      paragraphs: [
        '平台保存账号、对话、文件、使用和账务数据，以提供服务、处理结算与安全问题。具体留存期限、备份清理周期及依法保留的账务数据范围，仍需运营方根据实际部署和适用法律确认。',
        '产品提供删除对话和停用账号的操作。停用账号会阻止继续登录并撤销所有登录会话，但当前不会删除或匿名化账号资料、联系方式、第三方身份、历史对话、文件或账务记录。账号数据的删除范围、留存期限及用户请求处理流程仍待运营方确认。',
      ],
    },
    {
      id: 'browser-storage',
      title: '6. Cookie 与本地存储',
      paragraphs: [
        '网站使用浏览器本地存储和会话存储维持登录状态、记住主题与语言偏好，并保存部分界面缓存。Google OAuth 流程另使用短期 HttpOnly 浏览器绑定 Cookie 来校验发起登录的浏览器。',
        '退出登录会清除当前平台保存的登录 Token 和部分会话缓存。请不要在他人可访问的设备上保持登录；清除浏览器站点数据会使你退出登录并重置部分偏好。',
      ],
    },
    {
      id: 'security',
      title: '7. 安全措施与用户选择',
      paragraphs: [
        '平台使用 HTTPS、密码安全校验、会话撤销以及二次验证等措施保护账号。互联网传输和存储不存在绝对安全；如发现账号异常，请尽快退出其他设备并联系平台。',
        '你可以查看与绑定已验证联系方式、管理登录设备和二次验证，也可以删除对话或停用账号。停用会撤销登录会话并保留账号资料和历史数据；如需删除账号相关数据，当前处理范围和申请流程仍待运营方确认。',
      ],
    },
    {
      id: 'contact',
      title: '8. 联系方式与更新',
      paragraphs: [
        '隐私联系邮箱：liu771862@gmail.com。你可以通过该邮箱咨询个人信息处理、提出查阅、更正或删除请求；运营方应核验请求并依法处理。正式发布前仍需核实运营主体、数据留存期限、删除范围和第三方处理方。',
        '本政策的生效日期和更新记录将在运营方审核完成后填写。重大变更会通过网站或产品内公告说明。',
      ],
    },
  ],
}

const terms: LegalDocument = {
  eyebrow: '服务规则',
  title: '服务条款',
  intro: '本草案列出使用凌点 AI 账号、模型调用、文件处理和点数功能时的基本规则，正式版本需由运营方审核确认。',
  icon: '✦',
  sections: [
    {
      id: 'operator',
      title: '1. 服务提供方与接受条款',
      paragraphs: [
        '本服务以“凌点 AI”为产品名称。拟议运营主体名称为“武汉凌点人工智能科技有限公司”，这只是名称候选，尚未核实工商登记或其是否为本服务实际运营者；正式运营主体必须以真实登记信息及网站备案主体为准，因此本页面目前是本地审阅草案。注册、登录或使用服务前，请阅读正式发布的条款。',
        '本服务包括账号管理、AI 模型调用、对话保存、文件解析、API 调用和与之相关的功能；实际开放范围以产品页面和账号权限为准。',
      ],
    },
    {
      id: 'account',
      title: '2. 账号与安全',
      bullets: [
        '请提供真实、可用且有权使用的账号资料，并妥善保管登录凭据、API Key 和二次验证方式。',
        '发现未经授权的使用或安全风险时，请及时修改凭据、撤销登录设备并联系平台。',
        '你不得转让账号、规避访问控制、滥用接口或通过自动化方式损害平台及其他用户。',
      ],
    },
    {
      id: 'acceptable-use',
      title: '3. 使用规范',
      paragraphs: ['你应遵守适用法律法规及第三方模型服务规则，不得利用本服务生成或传播违法、有害、侵犯隐私或知识产权的内容，也不得上传无权处理的文件、恶意代码或他人机密信息。平台可对涉嫌滥用或危害服务安全的请求采取限制措施。'],
    },
    {
      id: 'ai-output',
      title: '4. AI 输出与用户审核',
      paragraphs: [
        'AI 输出由模型自动生成，可能不准确、不完整、过时或不适合特定用途。你应自行核验重要信息，并在医疗、法律、金融、安全等高影响场景中寻求合格专业人士意见。',
        '平台不保证模型始终可用、输出满足特定要求或第三方模型不会调整其服务。',
      ],
    },
    {
      id: 'content',
      title: '5. 用户内容与授权',
      paragraphs: ['你保留对自己提交内容依法享有的权利，并确认有权将其提交给平台及为完成请求所需的模型服务提供方处理。你授权平台仅为提供、维护、安全保障和改进本服务所必要地处理这些内容。模型输出的权利归属应结合适用法律、第三方模型规则和具体生成内容判断。'],
    },
    {
      id: 'points',
      title: '6. 点数与计费',
      paragraphs: [
        '模型调用可能按产品页面展示的规则消耗点数；具体扣点以请求所选模型和服务端计费记录为准。请在发送请求前查看模型状态和点数提示。',
        '当前平台直充、优惠券充值和兑换码入口未开放；本草案不构成收款或退款承诺。后续如开放支付功能，平台会另行展示价格、支付方式、退款规则和生效条款。',
      ],
    },
    {
      id: 'availability',
      title: '7. 服务变更、暂停与账号停用',
      paragraphs: ['平台可能因维护、安全、法律要求或第三方服务变化而调整、暂停部分功能。你可以按产品提供的流程停用账号；停用会阻止继续登录并撤销登录会话，当前不会自动删除账号资料、对话、文件或账务数据。账号数据的删除范围与申请流程仍待运营方确认。'],
    },
    {
      id: 'contact',
      title: '8. 联系方式与条款更新',
      paragraphs: [
        '客服与服务联系邮箱：liu771862@gmail.com。正式发布前仍需核实运营主体全称，并审核点数、内容授权、服务暂停及争议处理条款。',
        '条款生效日期将在正式审核后填写。平台会在本页面更新版本，并对重大变更作出合理提示。',
      ],
    },
  ],
}

function LegalDocumentPage({ kind }: { kind: 'privacy' | 'terms' }) {
  const { theme } = useThemeStore()
  const isDark = theme === 'dark'
  const document = kind === 'privacy' ? privacy : terms
  const otherPath = kind === 'privacy' ? '/terms' : '/privacy'
  const otherLabel = kind === 'privacy' ? '服务条款' : '隐私政策'

  return (
    <main className="min-h-screen px-4 py-8 sm:px-6 sm:py-12" style={{ background: 'var(--bg-primary)', color: 'var(--text-primary)' }}>
      <div className="mx-auto max-w-6xl">
        <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
          <Link to="/welcome" className="inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm transition-colors hover:border-violet-400" style={{ borderColor: 'var(--glass-border)', background: 'var(--bg-card)', color: 'var(--text-primary)' }}>
            <span aria-hidden="true">←</span> 返回凌点 AI
          </Link>
          <div className="text-xs" style={{ color: 'var(--text-dim)' }}>{document.eyebrow} · 法律信息</div>
        </div>

        <header className="relative overflow-hidden rounded-[2rem] border p-7 sm:p-10" style={{ borderColor: 'var(--glass-border)', background: 'radial-gradient(circle at 85% 0%, rgba(129, 91, 246, .28), transparent 35%), linear-gradient(135deg, var(--bg-card), var(--bg-secondary))' }}>
          <div className="pointer-events-none absolute -right-8 -top-16 h-56 w-56 rounded-full border opacity-20" style={{ borderColor: 'var(--accent-purple)' }} />
          <div className="relative max-w-3xl">
            <div className="mb-5 inline-flex h-12 w-12 items-center justify-center rounded-2xl border text-2xl" aria-hidden="true" style={{ color: 'var(--accent-purple)', borderColor: 'var(--glass-border)', background: 'var(--bg-surface)' }}>{document.icon}</div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-[.22em]" style={{ color: 'var(--accent-purple)' }}>LINGDIAN AI · {document.eyebrow}</p>
            <h1 className="text-3xl font-semibold tracking-tight sm:text-5xl">{document.title}</h1>
            <p className="mt-4 max-w-2xl text-sm leading-7 sm:text-base" style={{ color: 'var(--text-secondary)' }}>{document.intro}</p>
          </div>
        </header>

        <div className="mt-6 rounded-2xl border px-5 py-4" role="note" style={{ borderColor: 'rgba(245, 158, 11, .32)', background: 'rgba(245, 158, 11, .08)' }}>
          <div className="flex gap-3">
            <span className="mt-0.5 text-amber-500" aria-hidden="true">●</span>
            <div>
              <p className="text-sm font-semibold">本地草案，尚未具备正式发布条件</p>
              <p className="mt-1 text-sm leading-6" style={{ color: 'var(--text-secondary)' }}>公开联系邮箱已提供；运营主体登记信息、数据保留与删除范围仍待核实。请勿将此草案用于 Google OAuth 生产审核或替代正式法律审阅。</p>
            </div>
          </div>
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-[250px_minmax(0,1fr)]">
          <nav aria-label="文档目录" className="h-fit rounded-2xl border p-4 lg:sticky lg:top-6" style={{ borderColor: 'var(--glass-border)', background: 'var(--bg-card)' }}>
            <p className="mb-3 px-2 text-xs font-semibold tracking-wide" style={{ color: 'var(--text-dim)' }}>本页目录</p>
            <div className="space-y-1">
              {document.sections.map((section, index) => (
                <a key={section.id} href={`#${section.id}`} className="block rounded-xl px-3 py-2 text-sm leading-5 transition-colors hover:bg-violet-500/10" style={{ color: 'var(--text-secondary)' }}>
                  <span className="mr-2 text-xs" style={{ color: 'var(--accent-purple)' }}>{String(index + 1).padStart(2, '0')}</span>{section.title.replace(/^\d+\.\s*/, '')}
                </a>
              ))}
            </div>
          </nav>

          <article className="rounded-2xl border p-5 sm:p-8" style={{ borderColor: 'var(--glass-border)', background: 'var(--bg-card)' }}>
            <div className="mb-7 flex flex-wrap items-center justify-between gap-3 border-b pb-5" style={{ borderColor: 'var(--glass-border)' }}>
              <span className="rounded-full border px-3 py-1 text-xs" style={{ borderColor: 'var(--glass-border)', color: 'var(--text-dim)' }}>草案 · 2026-10-19</span>
              <Link to={otherPath} className="text-sm font-medium transition-colors hover:text-violet-400" style={{ color: 'var(--accent-purple)' }}>查看{otherLabel} →</Link>
            </div>

            <div className="space-y-9">
              {document.sections.map((section) => (
                <section key={section.id} id={section.id} className="scroll-mt-8">
                  <h2 className="text-lg font-semibold sm:text-xl">{section.title}</h2>
                  {section.paragraphs?.map((paragraph) => (
                    <p key={paragraph} className="mt-3 text-sm leading-7 sm:text-[15px]" style={{ color: 'var(--text-secondary)' }}>
                      {paragraph.split(/(liu771862@gmail\.com)/g).map((part, index) => part === 'liu771862@gmail.com'
                        ? <a key={index} href="mailto:liu771862@gmail.com" className="underline underline-offset-4" style={{ color: 'var(--accent-purple)' }}>{part}</a>
                        : part)}
                    </p>
                  ))}
                  {section.bullets && (
                    <ul className="mt-3 space-y-3">
                      {section.bullets.map((bullet) => (
                        <li key={bullet} className="flex gap-3 text-sm leading-7 sm:text-[15px]" style={{ color: 'var(--text-secondary)' }}>
                          <span className="mt-[10px] h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: 'var(--accent-purple)' }} />
                          <span>{bullet}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              ))}
            </div>

            <footer className="mt-10 border-t pt-5 text-xs leading-6" style={{ borderColor: 'var(--glass-border)', color: 'var(--text-dim)' }}>
              凌点 AI · 法律文档本地草案。正式生效日期、运营主体及联系渠道待确认。
            </footer>
          </article>
        </div>
      </div>
    </main>
  )
}

export function PrivacyPolicyPage() {
  return <LegalDocumentPage kind="privacy" />
}

export function TermsPage() {
  return <LegalDocumentPage kind="terms" />
}
