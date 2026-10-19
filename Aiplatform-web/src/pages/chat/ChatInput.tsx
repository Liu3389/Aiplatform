import { useState, useRef, useCallback, useEffect } from 'react'
import { aiDocApi } from '@/services/api'
import ImagePreviewDialog, { type PreviewImage } from '@/components/chat/ImagePreview'

export interface UploadedFile {
  file?: File
  fileId?: string
  url?: string
  previewUrl?: string
  name: string
  size: number
  status: 'uploading' | 'parsing' | 'parsed' | 'error'
  error?: string
}

interface Props {
  onSend: (text: string, parsedFiles: UploadedFile[]) => void
  onStop: () => void
  isStreaming: boolean
  disabled?: boolean
  placeholder?: string
  lang: string
  t: (zh: string, en: string) => string
}

// 允许上传的文件类型（需与后端 ALLOWED_EXTENSIONS 保持一致）
const ALLOWED_TYPES = '.txt,.md,.markdown,.text,.log,.rtf,' +
    '.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.tsv,' +
    '.pdf,.odt,.ods,.odp,.epub,' +
    '.c,.cpp,.cc,.cxx,.h,.hpp,.hxx,.java,.kt,.kts,.scala,.dart,.groovy,' +
    '.py,.pyw,.rb,.go,.rs,.swift,.php,.pl,.pm,.lua,' +
    '.sh,.bash,.zsh,.bat,.cmd,.ps1,.sql,.r,.m,.jl,.gradle,.cmake,' +
    '.html,.htm,.css,.scss,.less,.js,.jsx,.ts,.tsx,.mjs,.vue,.svelte,' +
    '.json,.xml,.yaml,.yml,.toml,.ini,.conf,.cfg,.properties,.env,' +
    '.png,.jpg,.jpeg,.gif,.webp'

function getUploadErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'string' && error) return error
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message) return message
  }
  return fallback
}

export function isImageFile(file: Pick<UploadedFile, 'file' | 'name'>) {
  if (file.file?.type?.startsWith('image/')) return true
  const extension = file.name.split('.').pop()?.toLowerCase()
  return extension === 'png' || extension === 'jpg' || extension === 'jpeg' || extension === 'gif' || extension === 'webp'
}

function createImagePreviewUrl(file: File) {
  if (!isImageFile({ file, name: file.name })) return undefined
  try {
    return URL.createObjectURL(file)
  } catch {
    return undefined
  }
}

function revokeImagePreviewUrl(file?: UploadedFile) {
  if (file?.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(file.previewUrl)
}

export default function ChatInput({ onSend, onStop, isStreaming, disabled, placeholder, lang, t }: Props) {
  const [text, setText] = useState('')
  const [files, setFiles] = useState<UploadedFile[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [isFocused, setIsFocused] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [previewImage, setPreviewImage] = useState<PreviewImage | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const autoCleanupTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)
  const filesRef = useRef<UploadedFile[]>([])

  const FILE_AUTO_CLEANUP_MS = 30 * 60 * 1000  // 30分钟：给用户充足思考时间，避免发送时文件已被清理

  const canSend = (text.trim().length > 0 || files.length > 0) && files.every(f => f.status === 'parsed')
  const isProcessing = files.some(f => f.status === 'uploading' || f.status === 'parsing')

  useEffect(() => {
    filesRef.current = files
  }, [files])

  useEffect(() => {
    return () => {
      if (autoCleanupTimerRef.current) clearTimeout(autoCleanupTimerRef.current)
      filesRef.current.forEach(revokeImagePreviewUrl)
    }
  }, [])

  const adjustHeight = useCallback(() => {
    const ta = textareaRef.current
    if (!ta) return
    ta.style.height = 'auto'
    ta.style.height = Math.min(ta.scrollHeight, 200) + 'px'
  }, [])

  useEffect(() => { adjustHeight() }, [text, adjustHeight])
  useEffect(() => {
    if (!disabled && !isStreaming && !isProcessing) textareaRef.current?.focus()
  }, [disabled, isStreaming, isProcessing])

  useEffect(() => { mountedRef.current = true; return () => { mountedRef.current = false } }, [])

  useEffect(() => {
    if (files.length > 0 && files.every(f => f.status === 'parsed')) {
      autoCleanupTimerRef.current = setTimeout(async () => {
        if (!mountedRef.current) return
        for (const f of files) {
          if (f.fileId) { try { await aiDocApi.deleteUpload(f.fileId) } catch {} }
          revokeImagePreviewUrl(f)
        }
        if (mountedRef.current) setFiles([])
      }, FILE_AUTO_CLEANUP_MS)
    }
    return () => { if (autoCleanupTimerRef.current) clearTimeout(autoCleanupTimerRef.current) }
  }, [files])

  const handleSend = useCallback(() => {
    const trimmed = text.trim()
    if ((!trimmed && files.length === 0) || isUploading || isProcessing || isStreaming) return
    // 修复：显式校验文件状态，防止 Ctrl+Enter 绕过按钮 disabled 带 error 文件发送
    if (!canSend) return
    if (autoCleanupTimerRef.current) clearTimeout(autoCleanupTimerRef.current)
    onSend(trimmed || t('请解析上传的文件并总结关键结论。', 'Analyze the uploaded files and summarize the key findings.'), files)
    files.forEach(revokeImagePreviewUrl)
    setPreviewImage(null)
    setText('')
    setFiles([])
  }, [text, files, isUploading, isProcessing, isStreaming, canSend, onSend, t])

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault()
      handleSend()
    }
    if (e.key === 'Escape') {
      if (isStreaming) { onStop() }
      else textareaRef.current?.blur()
    }
  }, [handleSend, isStreaming, onStop])

  // 公共文件上传逻辑（点击选择和拖拽放下共用）
  const uploadFiles = useCallback(async (fileList: File[]) => {
    if (isUploading || isProcessing) return
    setIsUploading(true)
    // 追加而非替换：支持多次上传，保留已上传的文件
    setFiles(prev => [...prev, ...fileList.map(f => ({
      file: f,
      previewUrl: createImagePreviewUrl(f),
      name: f.name,
      size: f.size,
      status: 'uploading' as const,
    }))])

    for (const f of fileList) {
      try {
        const result = await aiDocApi.upload(f)
        setFiles(prev => prev.map(item =>
          item.file === f
            ? { ...item, fileId: result.fileId, url: result.ossUrl, status: 'parsing' as const }
            : item
        ))
        const ctrl = new AbortController()
        aiDocApi.watchParseProgress(result.fileId, {
          onStatus: (s: any) => {
            if (mountedRef.current) setFiles(prev => prev.map(item =>
              item.fileId === result.fileId ? { ...item, status: s.status === 'SUCCESS' ? 'parsed' : 'parsing' as any } : item
            ))
          },
          onDone: () => {
            if (mountedRef.current) setFiles(prev => prev.map(item =>
              item.fileId === result.fileId ? { ...item, status: 'parsed' as const } : item
            ))
            ctrl.abort()
          },
          onError: (error: any) => {
            if (mountedRef.current) setFiles(prev => prev.map(item =>
              item.fileId === result.fileId
                ? { ...item, status: 'error' as const, error: getUploadErrorMessage(error, '解析失败') }
                : item
            ))
            ctrl.abort()
          },
        }, ctrl.signal).catch((err: any) => {
          if (err.name !== 'AbortError') {
            console.error(err)
            if (mountedRef.current) setFiles(prev => prev.map(item =>
              item.fileId === result.fileId
                ? { ...item, status: 'error' as const, error: getUploadErrorMessage(err, '解析失败') }
                : item
            ))
          }
        })
      } catch (err: any) {
        setFiles(prev => prev.map(item =>
          item.file === f
            ? { ...item, status: 'error' as const, error: getUploadErrorMessage(err, t('文件上传失败', 'Upload failed')) }
            : item
        ))
      }
    }
    if (mountedRef.current) setIsUploading(false)
  }, [isUploading, isProcessing, t])

  const handleFileSelect = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    // 先复制 FileList；随后清空 input 时，浏览器可能同步清空原始 FileList。
    const selectedFiles = Array.from(e.target.files ?? [])
    if (selectedFiles.length === 0) return
    if (fileInputRef.current) fileInputRef.current.value = ''
    await uploadFiles(selectedFiles)
  }, [uploadFiles])

  const handlePaste = useCallback(async (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pastedFiles = Array.from(e.clipboardData.items)
      .filter(item => item.kind === 'file')
      .map(item => item.getAsFile())
      .filter((file): file is File => Boolean(file))
    if (pastedFiles.length === 0) return

    e.preventDefault()
    const pastedText = e.clipboardData.getData('text/plain')
    if (pastedText) setText(prev => prev + pastedText)
    await uploadFiles(pastedFiles)
  }, [uploadFiles])

  // 拖拽上传：文件拖入时显示视觉反馈，放下时触发上传
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!isDragging) setIsDragging(true)
  }, [isDragging])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    // 仅当离开整个拖拽区域时才隐藏覆盖层（防止子元素切换闪烁）
    if (e.currentTarget === e.target) setIsDragging(false)
  }, [])

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
    const droppedFiles = Array.from(e.dataTransfer.files)
    if (droppedFiles.length === 0) return
    await uploadFiles(droppedFiles)
  }, [uploadFiles])

  const removeFile = useCallback(async (index: number) => {
    const file = files[index]
    if (!file) return
    if (file.fileId) { try { await aiDocApi.deleteUpload(file.fileId) } catch {} }
    setPreviewImage(current => current?.src === file.previewUrl ? null : current)
    revokeImagePreviewUrl(file)
    // 修复：用 fileId/name 唯一标识过滤而非 index，防止 async await 期间 files 变化导致删错文件
    setFiles(prev => prev.filter(f => (file.fileId ? f.fileId !== file.fileId : f.name !== file.name)))
  }, [files])

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  return (
    <div
      className="chat-composer-dock relative flex-shrink-0 border-t px-3 pt-2 sm:px-4"
      style={{ background: 'var(--bg-primary)', borderColor: 'var(--border-color)', paddingBottom: 'calc(0.5rem + env(safe-area-inset-bottom, 0px))' }}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* 拖拽上传覆盖层 */}
      {isDragging && (
        <div
          className="absolute inset-0 z-50 flex items-center justify-center rounded-2xl pointer-events-none"
          style={{
            background: 'rgba(139,92,246,0.08)',
            border: '2px dashed rgba(139,92,246,0.4)',
            backdropFilter: 'blur(4px)',
            WebkitBackdropFilter: 'blur(4px)',
          }}
        >
          <div className="flex flex-col items-center gap-2" style={{ animation: 'fade-in-up 0.3s ease-out' }}>
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="rgba(139,92,246,0.7)" strokeWidth="1.5">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
            <span className="text-sm font-medium" style={{ color: 'var(--accent-purple)' }}>
              {t('释放以上传文件', 'Drop files to upload')}
            </span>
          </div>
        </div>
      )}
      {/* Processing status bar */}
      {isProcessing && (
        <div className="flex items-center justify-center gap-2 py-2 mb-1 text-[11px] rounded-lg"
          style={{ color: 'var(--text-dim)', background: 'rgba(139,92,246,0.04)' }}>
          <div className="typing-indicator"><span /><span /><span /></div>
          {t('文件上传解析中，请稍候...', 'Uploading and parsing files...')}
        </div>
      )}

      {/* File chips */}
      {files.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {files.map((f, i) => (
            isImageFile(f) ? (
              <div key={i} className="relative w-[88px] flex-shrink-0">
                <button
                  type="button"
                  disabled={!f.previewUrl && !f.url}
                  onClick={() => {
                    const src = f.previewUrl || f.url
                    if (src) setPreviewImage({ src, alt: f.name })
                  }}
                  className="group relative block h-[88px] w-[88px] overflow-hidden rounded-xl border p-0 text-left transition-transform duration-200 hover:scale-[1.03] focus:outline-none focus:ring-2 focus:ring-violet-400 disabled:cursor-default"
                  style={{
                    background: 'var(--bg-surface)',
                    borderColor: f.status === 'error' ? 'rgba(244,63,94,0.35)' : 'var(--glass-border)',
                  }}
                  aria-label={t(`预览图片 ${f.name}`, `Preview image ${f.name}`)}
                  title={t('点击预览图片', 'Click to preview image')}
                >
                  {(f.previewUrl || f.url) ? (
                    <img src={f.previewUrl || f.url} alt={f.name} className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--text-dim)' }}>
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                        <rect x="3" y="3" width="18" height="18" rx="2" />
                        <circle cx="8.5" cy="8.5" r="1.5" />
                        <path d="m21 15-5-5L5 21" />
                      </svg>
                    </div>
                  )}
                  {(f.status === 'uploading' || f.status === 'parsing') && (
                    <span className="absolute inset-0 flex items-center justify-center" style={{ background: 'rgba(3,7,18,0.46)' }}>
                      <span className="typing-indicator" style={{ transform: 'scale(0.7)' }}><span /><span /><span /></span>
                    </span>
                  )}
                  {f.status === 'parsed' && (
                    <span className="absolute bottom-1 right-1 flex h-5 w-5 items-center justify-center rounded-full" style={{ background: 'rgba(16,185,129,0.92)', color: '#fff' }}>
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.7"><path d="m5 12 4 4L19 6" /></svg>
                    </span>
                  )}
                  {f.status === 'error' && (
                    <span className="absolute inset-x-1 bottom-1 truncate rounded px-1 py-0.5 text-center text-[9px]" style={{ background: 'rgba(159,18,57,0.88)', color: '#fff' }}>
                      {f.error || t('解析失败', 'Failed')}
                    </span>
                  )}
                </button>
                {!isStreaming && (
                  <button
                    type="button"
                    onClick={() => removeFile(i)}
                    className="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full border transition-opacity hover:scale-105 focus:outline-none focus:ring-2 focus:ring-violet-400"
                    style={{ background: 'var(--bg-card)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                    aria-label={t(`移除 ${f.name}`, `Remove ${f.name}`)}
                    title={t('移除图片', 'Remove image')}
                  >
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
                  </button>
                )}
                <span className="mt-1 block truncate text-[9px]" style={{ color: 'var(--text-dim)' }} title={f.name}>{f.name}</span>
              </div>
            ) : (
              <span
                key={i}
                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-[10px] rounded-lg border transition-all duration-200"
                style={{
                  background: f.status === 'parsed' ? 'var(--accent-emerald-soft)' :
                    f.status === 'error' ? 'var(--accent-rose-soft)' :
                    'var(--accent-purple-soft)',
                  borderColor: f.status === 'parsed' ? 'rgba(16,185,129,0.25)' :
                    f.status === 'error' ? 'rgba(244,63,94,0.25)' :
                    'rgba(139,92,246,0.25)',
                  color: 'var(--text-secondary)',
                }}
              >
                {(f.status === 'uploading' || f.status === 'parsing') && (
                  <div className="typing-indicator" style={{ transform: 'scale(0.6)' }}><span /><span /><span /></div>
                )}
                {f.status === 'parsed' && (
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--accent-emerald)" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                )}
                {f.status === 'error' && (
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--accent-rose)" strokeWidth="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                )}
                <span className="truncate max-w-[80px] font-medium">{f.name}</span>
                <span style={{ color: 'var(--text-dim)' }}>{formatSize(f.size)}</span>
                {f.status === 'error' && <span className="text-[9px]" style={{ color: 'var(--accent-rose)' }}>{f.error}</span>}
                {f.status === 'parsed' && <span className="text-[9px]" style={{ color: 'var(--accent-emerald)' }}>{t('已解析', 'Parsed')}</span>}
                {!isStreaming && (
                  <button type="button" onClick={() => removeFile(i)} className="ml-0.5 opacity-40 hover:opacity-80 transition-opacity" aria-label={t(`移除 ${f.name}`, `Remove ${f.name}`)}>
                    <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                  </button>
                )}
              </span>
            )
          ))}
        </div>
      )}

      {/* Input container — glass design */}
      <div className="relative rounded-2xl transition-all duration-300"
        style={{
          background: isFocused ? 'var(--surface-glass)' : 'var(--bg-surface)',
          border: `1px solid ${isFocused ? 'rgba(139,92,246,0.3)' : 'var(--glass-border)'}`,
          boxShadow: isFocused
            ? '0 0 0 3px rgba(139,92,246,0.06), 0 8px 32px rgba(0,0,0,0.06)'
            : '0 2px 12px rgba(0,0,0,0.04)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          opacity: isProcessing ? 0.7 : 1,
          pointerEvents: isProcessing ? 'none' : 'auto',
        }}
      >
        <div className="flex items-end gap-2 p-2">
          {/* File attach button */}
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={isStreaming || disabled || isUploading || isProcessing}
            className="p-2.5 rounded-xl transition-all duration-200 flex-shrink-0 disabled:opacity-30"
            style={{ color: 'var(--text-dim)' }}
            title={t('上传文件', 'Upload file')}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
              <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48" />
            </svg>
          </button>
          <input ref={fileInputRef} type="file" multiple accept={ALLOWED_TYPES} onChange={handleFileSelect} className="hidden" />

          {/* Textarea */}
          <textarea
            ref={textareaRef}
            value={text}
            onChange={e => setText(e.target.value)}
            onPaste={handlePaste}
            onKeyDown={handleKeyDown}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            disabled={isStreaming || disabled}
            placeholder={placeholder || t('输入消息, Ctrl+Enter 发送, Esc 停止', 'Type a message, Ctrl+Enter to send, Esc to stop')}
            rows={1}
            className="flex-1 resize-none bg-transparent text-sm outline-none placeholder:opacity-60 py-1.5"
            style={{
              color: 'var(--text-primary)',
              maxHeight: 200,
              fontFamily: 'var(--font-sans)',
            }}
          />

          {/* Send / Stop button */}
          {isStreaming ? (
            <button onClick={onStop}
              className="flex-shrink-0 px-4 py-2.5 rounded-xl text-xs font-semibold text-white transition-all duration-200 hover:opacity-90 active:scale-95"
              style={{ background: 'var(--accent-rose)', boxShadow: '0 2px 8px rgba(244,63,94,0.25)' }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="white" className="inline mr-1.5"><rect x="4" y="4" width="16" height="16" rx="3" /></svg>
              {t('停止', 'Stop')}
            </button>
          ) : (
            <button
              onClick={handleSend}
              disabled={disabled || !canSend}
              className="flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center transition-all duration-200 disabled:opacity-20 active:scale-90"
              style={{
                background: canSend
                  ? 'linear-gradient(135deg, #8B5CF6, #6366F1)'
                  : 'transparent',
                color: canSend ? '#fff' : 'var(--text-dim)',
                boxShadow: canSend ? '0 4px 14px rgba(139,92,246,0.3)' : 'none',
              }}
              title={t('发送', 'Send')}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="22" y1="2" x2="11" y2="13" />
                <polygon points="22 2 15 22 11 13 2 9 22 2" />
              </svg>
            </button>
          )}
        </div>

        {/* Bottom hint */}
        <div className="flex items-center justify-between px-4 pb-1.5">
          <span className="text-[10px]" style={{ color: 'var(--text-dim)', opacity: 0.55 }}></span>
          <div className="text-[10px]" style={{ color: 'var(--text-dim)', opacity: 0.55 }}>
            {text.length > 0 ? `${text.length} ${t('字符', 'chars')}` : ''}
          </div>
        </div>
      </div>
      <ImagePreviewDialog image={previewImage} onClose={() => setPreviewImage(null)} t={t} />
    </div>
  )
}
