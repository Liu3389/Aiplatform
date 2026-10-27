type UploadCandidate = {
  file?: { type?: string }
  name: string
}

export function isImageFile(file: UploadCandidate) {
  if (file.file?.type?.startsWith('image/')) return true
  const extension = file.name.split('.').pop()?.toLowerCase()
  return extension === 'png' || extension === 'jpg' || extension === 'jpeg' || extension === 'gif' || extension === 'webp'
}

export function partitionFilesForModel<T extends UploadCandidate>(files: T[], supportsVision: boolean) {
  const unsupportedImages = supportsVision ? [] : files.filter(isImageFile)
  const acceptedFiles = unsupportedImages.length > 0
    ? files.filter(file => !isImageFile(file))
    : files
  return { acceptedFiles, unsupportedImages }
}
