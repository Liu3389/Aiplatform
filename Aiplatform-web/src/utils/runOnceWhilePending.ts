const pendingRequests = new Map<string, Promise<unknown>>()

/** Share one in-flight operation when React StrictMode remounts a callback page. */
export function runOnceWhilePending<T>(key: string, operation: () => Promise<T>): Promise<T> {
  const existing = pendingRequests.get(key)
  if (existing) return existing as Promise<T>

  const request = Promise.resolve().then(operation)
  pendingRequests.set(key, request)
  const clear = () => {
    if (pendingRequests.get(key) === request) pendingRequests.delete(key)
  }
  void request.then(clear, clear)
  return request
}
