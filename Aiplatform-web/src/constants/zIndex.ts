/**
 * 统一 z-index 层级常量。
 *
 * 层级约定（从低到高）：
 *  - SIDEBAR     : 侧边栏 / 抽屉
 *  - DROPDOWN    : 下拉菜单 / 弹出面板（如通知铃铛面板）
 *  - MODAL       : 普通模态框 / 对话框
 *  - NOTIFICATION: 通知铃铛面板（高于普通弹窗，确保消息可见）
 *  - ADMIN_MODAL : 管理员后台弹窗（高于通知，便于管理员操作不被遮挡）
 *  - TOAST       : 全局 Toast 提示（永远在最顶层）
 */
export const Z = {
  SIDEBAR: 20,
  DROPDOWN: 30,
  MODAL: 50,
  NOTIFICATION: 60,
  ADMIN_MODAL: 70,
  TOAST: 80,
} as const

export type ZIndex = (typeof Z)[keyof typeof Z]
