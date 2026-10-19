type ApiErrorPayload = {
  message?: unknown
  data?: unknown
}

export async function readHttpApiError(response: Response): Promise<{ message: string; data?: unknown }> {
  try {
    const body = await response.clone().json() as ApiErrorPayload
    if (typeof body.message === 'string' && body.message.trim()) {
      return { message: body.message, data: body.data }
    }
  } catch {}

  return { message: `HTTP ${response.status}: ${response.statusText}` }
}
