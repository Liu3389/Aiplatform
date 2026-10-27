import { Link } from 'react-router-dom'

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
  intro: '本政策说明凌点 AI 在提供账号、AI 对话和相关平台服务时如何处理个人信息，以及你可以如何管理这些信息。',
  icon: '◈',
  sections: [
    {
      id: 'operator',
      title: '1. 运营主体与适用范围',
      paragraphs: [
        '本服务由个人运营，网站备案主体为个人；“凌点 AI”是产品名称。隐私联系邮箱为 liu771862@gmail.com。',
        '本政策适用于 lingdian-ai.top 网站及凌点 AI 提供的账号、模型调用、对话、文件处理和相关功能。',
      ],
    },
    {
      id: 'data',
      title: '2. 处理的信息',
      bullets: [
        '账号与登录：用户名、密码校验信息、邮箱或手机号及其验证状态；使用 Google 登录时，平台接收 Google 用户标识、名称和已验证邮箱；使用 QQ 登录时，平台接收 QQ OpenID，以及你授权提供的昵称和头像。平台不会接收 Google 或 QQ 账号密码。',
        '服务内容：你提交的提示词、对话内容、模型选择、上传文件及其解析结果，以及用于保存、同步和删除对话所需的信息。请勿上传你无权处理的文件或不必要的敏感信息。',
        '使用与账务：模型调用用量、点数余额、计费及结算流水，用于展示用量、计算点数和处理账务问题。',
        '安全与运行：登录设备和会话标识、访问时间、来源网络信息及必要的操作和错误日志，用于维持登录、排查故障和防止滥用。',
        '浏览器存储：网站使用本地存储保存登录 Token 和界面偏好；在你使用平台 API Key 时，会将当前选择的 Key 暂存于会话存储。OAuth 登录会使用短期浏览器绑定 Cookie。请在个人设备上使用并妥善管理浏览器数据。',
      ],
    },
    {
      id: 'purpose',
      title: '3. 使用目的',
      paragraphs: ['平台将上述信息用于创建和保护账号、响应 AI 请求、处理文件、保存和管理对话、计算点数、提供安全控制、排查服务故障，以及回应你的请求。第三方登录信息仅用于登录、账号绑定和账号管理。'],
    },
    {
      id: 'ai-processing',
      title: '4. AI 请求与第三方服务',
      paragraphs: [
        '当你选择模型并发送请求时，平台会将完成该请求所需的提示词、对话上下文和附件内容发送给该模型对应的服务提供方处理。可用模型及其供应方以产品页面实际显示为准；供应方会依其隐私规则处理收到的信息。请在提交前查看所选模型和供应方，并避免发送无关个人信息。',
        'Google 和 QQ 仅在你主动选择相应登录或绑定流程时处理身份验证信息。QQ 登录通过 QQ 互联取得 OpenID，并在你授权范围内读取昵称和头像；平台不会索取 Google 或 QQ 账号密码。',
      ],
    },
    {
      id: 'retention',
      title: '5. 保存期限与删除',
      paragraphs: [
        '平台仅在实现本政策所述目的所必需的期限内保存个人信息。对话和文件在你删除前用于提供服务；账号停用后，平台会保留账号资料、联系方式、第三方身份、历史对话、文件和账务记录。账务、安全或争议处理所需的信息，以及法律法规要求保存的信息，会在相应目的或法定义务期限内保留。',
        '你可以在产品内删除对话或停用账号。停用账号会阻止继续登录并撤销登录会话，但不会自动删除上述账号和历史数据。你可通过本页邮箱提出查阅、更正、删除或注销请求；平台核验身份后，会依法处理，并说明依法不能立即删除的信息及原因。',
      ],
    },
    {
      id: 'browser-storage',
      title: '6. Cookie 与本地存储',
      paragraphs: [
        '网站使用浏览器本地存储和会话存储维持登录状态、记住主题与语言偏好，并保存部分界面缓存。第三方 OAuth 流程另使用短期 HttpOnly 浏览器绑定 Cookie 来校验发起登录的浏览器。',
        '退出登录会清除当前平台保存的登录 Token 和部分会话缓存。请不要在他人可访问的设备上保持登录；清除浏览器站点数据会使你退出登录并重置部分偏好。',
      ],
    },
    {
      id: 'security',
      title: '7. 安全措施与用户选择',
      paragraphs: [
        '平台使用 HTTPS、密码安全校验、会话撤销以及可选的二次验证等措施保护账号，并按最小必要原则限制内部访问。互联网传输和存储不存在绝对安全；如发现账号异常或信息泄露，请尽快撤销其他设备并联系平台。',
        '你可以查看和绑定已验证联系方式、管理登录设备和二次验证、删除对话或停用账号。对于需要验证身份的个人信息请求，平台可能要求你提供必要信息以确认账号归属。',
      ],
    },
    {
      id: 'contact',
      title: '8. 联系方式与更新',
      paragraphs: [
        '隐私联系邮箱：liu771862@gmail.com。你可以通过该邮箱咨询个人信息处理或提出权利请求。',
        '本政策自 2026 年 10 月 20 日起生效。服务内容或信息处理方式发生重大变化时，平台会更新本页并通过网站或产品内公告提示。',
      ],
    },
    {
      id: 'minors',
      title: '9. 未成年人信息',
      paragraphs: ['未成年人应在监护人同意和指导下使用本服务。涉及不满十四周岁未成年人的个人信息时，平台将依法取得监护人同意并采取相应保护措施。监护人如对相关信息处理有疑问，可通过本页邮箱联系平台。'],
    },
  ],
}

const terms: LegalDocument = {
  eyebrow: '服务规则',
  title: '服务条款',
  intro: '请在注册、登录或使用凌点 AI 前阅读本条款。使用服务即表示你理解并同意适用条款；如不同意，请停止使用。',
  icon: '✦',
  sections: [
    {
      id: 'operator',
      title: '1. 服务提供方与接受条款',
      paragraphs: [
        '本服务由个人运营，网站备案主体为个人；“凌点 AI”是产品名称。服务联系邮箱为 liu771862@gmail.com。',
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
      paragraphs: ['你应遵守适用法律法规及第三方模型服务规则，不得利用本服务生成或传播违法、有害、侵犯隐私或知识产权的内容，也不得上传无权处理的文件、恶意代码或他人机密信息。平台可对涉嫌滥用、违反条款或危害服务安全的请求采取必要限制；紧急情况下可先采取措施并在合理范围内说明。'],
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
      paragraphs: ['你保留对自己提交内容依法享有的权利，并确认有权将其提交给平台及为完成请求所需的模型服务提供方处理。你授权平台为提供请求、保存对话、维护服务和保障安全所必要地处理这些内容，并在完成模型请求时发送相关内容给所选模型供应方。模型输出的权利归属应结合适用法律、第三方模型规则和具体生成内容判断。'],
    },
    {
      id: 'points',
      title: '6. 点数与计费',
      paragraphs: [
        '模型调用可能按产品页面展示的规则消耗点数；具体扣点以请求所选模型和服务端计费记录为准。请在发送请求前查看模型状态和点数提示。',
        '当前平台直充、优惠券充值和兑换码入口未开放。现有点数余额与已完成账务记录按账号页面和平台账本展示；平台不会依据本条款新增收费项目。后续如开放支付功能，平台会在收费前另行展示价格、支付方式和退款规则。',
      ],
    },
    {
      id: 'availability',
      title: '7. 服务变更、暂停与账号停用',
      paragraphs: ['平台可能因维护、安全、法律要求或第三方服务变化而调整或暂停部分功能，并会在合理可行时提前告知。你可以按产品提供的流程停用账号；停用会阻止继续登录并撤销登录会话，但不会自动删除账号资料、对话、文件或账务数据。你可以通过隐私政策列出的邮箱提出个人信息请求。'],
    },
    {
      id: 'contact',
      title: '8. 联系方式与条款更新',
      paragraphs: [
        '客服与服务联系邮箱：liu771862@gmail.com。因使用本服务发生争议，双方应先友好协商；协商不成的，依法向有管辖权的人民法院提起诉讼。',
        '本条款自 2026 年 10 月 20 日起生效。平台会在本页面更新版本，并对重大变更作出合理提示。',
      ],
    },
    {
      id: 'minors',
      title: '9. 未成年人使用',
      paragraphs: ['未满十八周岁的用户应在监护人同意和指导下使用本服务；不满十四周岁的用户应由监护人协助使用。监护人应帮助未成年人遵守本条款和所选模型供应方的规则。'],
    },
  ],
}

function LegalDocumentPage({ kind }: { kind: 'privacy' | 'terms' }) {
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
              <span className="rounded-full border px-3 py-1 text-xs" style={{ borderColor: 'var(--glass-border)', color: 'var(--text-dim)' }}>生效 · 2026-10-20</span>
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
              凌点 AI · 运营者：个人 · 联系邮箱：liu771862@gmail.com
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
