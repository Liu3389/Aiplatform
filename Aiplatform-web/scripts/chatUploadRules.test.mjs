import test from 'node:test'
import assert from 'node:assert/strict'
import { isImageFile, partitionFilesForModel } from '../src/utils/chatUploadRules.ts'

const upload = (name, type = '') => ({ name, file: { type } })

test('non-vision models block images detected by MIME type or extension', () => {
  const pngByMime = upload('clipboard-image', 'image/png')
  const jpgByExtension = upload('photo.JPG')
  const document = upload('notes.pdf', 'application/pdf')

  const result = partitionFilesForModel([pngByMime, jpgByExtension, document], false)

  assert.deepEqual(result.unsupportedImages, [pngByMime, jpgByExtension])
  assert.deepEqual(result.acceptedFiles, [document])
  assert.equal(isImageFile(pngByMime), true)
})

test('vision models accept images and mixed uploads without filtering', () => {
  const image = upload('diagram.webp', 'image/webp')
  const document = upload('notes.pdf', 'application/pdf')

  const result = partitionFilesForModel([image, document], true)

  assert.deepEqual(result.unsupportedImages, [])
  assert.deepEqual(result.acceptedFiles, [image, document])
})

test('non-vision models keep document-only uploads unchanged', () => {
  const files = [upload('notes.pdf'), upload('source.ts')]

  const result = partitionFilesForModel(files, false)

  assert.deepEqual(result.unsupportedImages, [])
  assert.equal(result.acceptedFiles, files)
})
