import { useState, useRef, useCallback, useEffect } from 'react'
import { aiDocApi } from '@/services/api'

export interface UploadedFile {
  file?: File
  fileId?: string
  url?: string
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

const ALLOWED_TYPES = '.txt,.md,.java,.py,.cpp,.js,.ts,.html,.css,.json,.xml,.xlsx,.docx'

export default function ChatInput({ onSend, onStop, isStreaming, disabled, placeholder, lang, t }: Props) {
  const [text, setText] = useState('')
  const [files, setFiles] = useState<UploadedFile[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [isFocused, setIsFocused] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const autoCleanupTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)

  const FILE_AUTO_CLEANUP_MS = 10 * 60 * 1000

  const canSend = (text.trim() && files.every(f => f.status === 'parsed')) || (text.trim() && files.length === 0)
  const isProcessing = files.some(f => f.status === 'uploading' || f.status === 'parsing')

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
        for (const f of files) { if (f.fileId) { try { await aiDocApi.deleteUpload(f.fileId) } catch {} } }
        if (mountedRef.current) setFiles([])
      }, FILE_AUTO_CLEANUP_MS)
    }
    return () => { if (autoCleanupTimerRef.current) clearTimeout(autoCleanupTimerRef.current) }
  }, [files])

  const handleSend = useCallback(() => {
    const trimmed = text.trim()
    if (!trimmed || isUploading || isProcessing || isStreaming) return
    if (autoCleanupTimerRef.current) clearTimeout(autoCleanupTimerRef.current)
    onSend(trimmed, files)
    setText('')
    setFiles([])
  }, [text, files, isUploading, isProcessing, isStreaming, onSend])

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

  const handleFileSelect = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = e.target.files
    if (!selectedFiles || selectedFiles.length === 0) return
    if (isUploading || isProcessing) return
    setIsUploading(true)
    const fileList = Array.from(selectedFiles)
    setFiles(fileList.map(f => ({ file: f, name: f.name, size: f.size, status: 'uploading' as const })))
    if (fileInputRef.current) fileInputRef.current.value = ''

    for (const f of fileList) {
      try {
        const result = await aiDocApi.upload(f)
        setFiles(prev => prev.map(item =>
          item.name === f.name && item.status === 'uploading'
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
              item.fileId === result.fileId ? { ...item, status: 'error' as const, error } : item
            ))
            ctrl.abort()
          },
        }, ctrl.signal).catch((err: any) => { if (err.name !== 'AbortError') console.error(err) })
      } catch (err: any) {
        setFiles(prev => prev.map(item =>
          item.name === f.name && item.status === 'uploading'
            ? { ...item, status: 'error' as const, error: err.message || 'Upload failed' }
            : item
        ))
      }
    }
    if (mountedRef.current) setIsUploading(false)
  }, [isUploading, isProcessing])

  const removeFile = useCallback(async (index: number) => {
    const file = files[index]
    if (file.fileId) { try { await aiDocApi.deleteUpload(file.fileId) } catch {} }
    setFiles(prev => prev.filter((_, i) => i !== index))
  }, [files])

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  return (
    <div className="flex-shrink-0 px-4 pb-2 pt-1" style={{ background: 'var(--bg-primary)' }}>
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
                <button onClick={() => removeFile(i)} className="ml-0.5 opacity-40 hover:opacity-80 transition-opacity">
                  <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              )}
            </span>
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
            disabled={isStreaming || disabled || isUploading || isProcessing || files.length > 0}
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
            onKeyDown={handleKeyDown}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            disabled={isStreaming || disabled}
            placeholder={placeholder || t('输入消息, Ctrl+Enter 发送, Esc 停止', 'Type a message, Ctrl+Enter to send, Esc to stop')}
            rows={1}
            className="flex-1 resize-none bg-transparent text-sm outline-none placeholder:opacity-35 py-1.5"
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
          <span className="text-[10px]" style={{ color: 'var(--text-dim)', opacity: 0.35 }}></span>
          <div className="text-[10px]" style={{ color: 'var(--text-dim)', opacity: 0.35 }}>
            {text.length > 0 ? `${text.length} ${t('字符', 'chars')}` : ''}
          </div>
        </div>
      </div>
    </div>
  )
}