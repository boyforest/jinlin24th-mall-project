import { apiRequest } from '@/api/client'

export interface UserLoginDTO {
  code: string
  nickname?: string
  avatarUrl?: string
  encryptedData?: string
  iv?: string
  inviterUserId?: number | null
}

export interface UserLoginVO {
  token: string
  userId: number
}

export interface AppUserVO {
  id: number
  nickname?: string
  avatar?: string
  gender?: number
  phone?: string
  memberLevelId?: number
  memberLevelName?: string
  points?: number
  totalAmount?: number
  parentUserId?: number | null
  parentUserNickname?: string
  parentUserPhone?: string
  isDistributor?: number
  distributorEnabledTime?: string
  distributorDisabledTime?: string
  createTime?: string
}

export function login(dto: UserLoginDTO) {
  return apiRequest<UserLoginVO>('/user/appUser/login', { method: 'POST', data: dto, auth: false })
}

export function me() {
  return apiRequest<AppUserVO>('/user/appUser/me')
}

export function getRecommender() {
  return apiRequest<AppUserVO | null>('/user/appUser/recommender')
}

export function bindRecommender(recommenderUserId: number) {
  return apiRequest<AppUserVO>('/user/appUser/recommender', {
    method: 'POST',
    data: { recommenderUserId },
  })
}

export function updateProfile(params: { nickname?: string; avatar?: string }) {
  const query = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return apiRequest<AppUserVO>(`/user/appUser/me/profile?${query}`, { method: 'PUT' })
}
