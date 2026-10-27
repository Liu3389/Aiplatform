/**
 * Recover a compact table where the model placed header, separator, and body
 * cells on one physical line. A separator row by itself must stay untouched:
 * treating it as a compact table creates a synthetic empty header row and
 * makes the real first row disappear from GFM's table.
 */
export function normalizeCompactTableLine(line: string): string {
  if (!line.includes('|') || !/\|\s*:?-{3,}:?\s*(?=\|)/.test(line)) return line

  const firstPipe = line.indexOf('|')
  if (firstPipe < 0) return line
  const prefix = line.slice(0, firstPipe).trim()
  const tablePart = line.slice(firstPipe)
  const delimiterOffset = tablePart.search(/\|\s*:?-{3,}:?\s*(?=\|)/)
  if (delimiterOffset < 0) return line

  const headerPart = tablePart.slice(0, delimiterOffset)
  const delimiterAndBody = tablePart.slice(delimiterOffset)
  const headers = headerPart.split('|').map(cell => cell.trim()).filter(Boolean)
  // A normal GFM delimiter row has no preceding header cells. Preserve it so
  // remark-gfm can pair it with the actual header row immediately above.
  if (headers.length < 2) return line

  const delimiterMatch = delimiterAndBody.match(/^(?:\|\s*:?-{3,}:?\s*)+\|?/)
  if (!delimiterMatch) return line

  const separatorCount = (delimiterMatch[0].match(/-{3,}/g) || []).length
  const columnCount = Math.max(headers.length, separatorCount)
  if (columnCount < 2) return line

  const body = delimiterAndBody.slice(delimiterMatch[0].length)
  const bodyCells = body.split('|').map(cell => cell.trim()).filter(Boolean)
  const rows: string[][] = []
  for (let index = 0; index + columnCount <= bodyCells.length; index += columnCount) {
    rows.push(bodyCells.slice(index, index + columnCount))
  }

  const tableLines = [
    `| ${Array.from({ length: columnCount }, (_, index) => headers[index] || '').join(' | ')} |`,
    `| ${Array.from({ length: columnCount }, () => '---').join(' | ')} |`,
    ...rows.map(row => `| ${row.join(' | ')} |`),
  ]
  const table = tableLines.join('\n')
  return prefix ? `${prefix}\n\n${table}` : table
}

/** Recover multiple hyphenated bullets emitted on one line without splitting URLs. */
export function normalizeCompactListLine(line: string): string {
  if (!/^\s*-\s*\S/.test(line) || (line.match(/-/g) || []).length < 2) return line
  return line
    .replace(/^\s*-\s*/, '- ')
    // A compact bullet marker has whitespace after its hyphen. URL slugs such
    // as lingdian-ai.top do not, so they remain intact.
    .replace(/([^\n])\s*-\s+(?=[\u4e00-\u9fffA-Za-z*`])/g, '$1\n- ')
}
