import type { UserInfo } from '@/types'

export function isGeneratedOAuthUsername(username: string | null | undefined): boolean {
  return username?.startsWith('oauth_') === true
}

export function getUserDisplayName(
  user: Pick<UserInfo, 'username' | 'email'> | null | undefined,
  oauthFallback: string,
): string {
  if (!user) return ''
  if (isGeneratedOAuthUsername(user.username)) {
    return user.email?.trim() || oauthFallback
  }
  return user.username?.trim() || oauthFallback
}
