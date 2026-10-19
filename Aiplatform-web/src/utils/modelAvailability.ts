import type { AvailableModel } from '@/types'

export function canUseChatModel(
  model: Pick<AvailableModel, 'accessible' | 'chatEnabled'> | null | undefined,
): boolean {
  return Boolean(model?.accessible && model.chatEnabled)
}

export function selectChatModel(
  models: AvailableModel[],
  preferredModelCode: string,
): AvailableModel | null {
  const usableModels = models.filter(canUseChatModel)
  return usableModels.find(model =>
    model.modelCode === preferredModelCode || model.displayName === preferredModelCode,
  ) ?? usableModels[0] ?? null
}
