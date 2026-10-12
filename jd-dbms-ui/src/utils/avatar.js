export const presetAvatars = Array.from({ length: 20 }, (_, index) =>
  `/avatar-presets/avatar-${String(index + 1).padStart(2, '0')}.svg`
)

export function avatarUrl(avatar) {
  if (!avatar) return require('@/assets/images/profile.jpg')
  if (presetAvatars.includes(avatar)) return avatar
  return process.env.VUE_APP_BASE_API + avatar
}
