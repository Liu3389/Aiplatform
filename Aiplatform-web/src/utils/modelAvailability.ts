import type { AvailableModel } from '@/types'

export function canUseChatModel(
  model: Pick<AvailableModel, 'accessible' | 'chatEnabled'> | null | undefined,
): boolean {
  return Boolean(model?.accessible && model.chatEnabled)
}

/** A display name identifies a model configuration; modelCode may be shared by aliases. */
export function modelSelectionKey(model: Pick<AvailableModel, 'displayName'>): string {
  return model.displayName
}

function normalizedModelName(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '')
}

function matchPreferredModel(models: AvailableModel[], preferredModelCode: string): AvailableModel | undefined {
  if (!preferredModelCode) return undefined
  const preferred = normalizedModelName(preferredModelCode)

  // Prefer the unique product name before the upstream model ID. Multiple
  // platform products can intentionally route through the same provider model.
  const byDisplayName = models.find(model => normalizedModelName(model.displayName) === preferred)
  if (byDisplayName) return byDisplayName

  const byId = models.find(model => String(model.modelId) === preferredModelCode)
  if (byId) return byId

  const byProviderModel = models.filter(model => normalizedModelName(model.modelCode) === preferred)
  if (byProviderModel.length === 1) return byProviderModel[0]
  if (byProviderModel.length > 1) {
    // A legacy localStorage value such as `deepseek-flash` did not identify the
    // vision product. Keep that old selection on the non-vision Flash entry.
    return byProviderModel.find(model => Number(model.supportsVision) !== 1) ?? byProviderModel[0]
  }
  return undefined
}

export function selectChatModel(
  models: AvailableModel[],
  preferredModelCode: string,
): AvailableModel | null {
  const usableModels = models.filter(canUseChatModel)
  return matchPreferredModel(usableModels, preferredModelCode) ?? usableModels[0] ?? null
}
