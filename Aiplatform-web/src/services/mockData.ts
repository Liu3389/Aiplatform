import type { SystemStats, ApiResult } from '@/types'

const rand = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min
const randFloat = (min: number, max: number) => parseFloat((Math.random() * (max - min) + min).toFixed(2))

const now = new Date()

export interface DailyUsage {
  date: string
  calls: number
  tokens: number
  cost: number
}

export interface ModelUsage {
  model: string
  calls: number
  tokens: number
  cost: number
  color: string
}

export interface HourlyTraffic {
  hour: string
  calls: number
}

export interface TopUser {
  rank: number
  username: string
  userId: number
  calls: number
  cost: number
}

export interface RevenueTrend {
  month: string
  revenue: number
  costs: number
}

export interface UserGrowth {
  month: string
  total: number
  active: number
  new: number
}

export interface AdminAnalytics {
  systemStats: SystemStats
  dailyUsage: DailyUsage[]
  modelUsage: ModelUsage[]
  hourlyTraffic: HourlyTraffic[]
  topUsers: TopUser[]
  revenueTrend: RevenueTrend[]
  userGrowth: UserGrowth[]
}

export interface UserAnalytics {
  dailyUsage: DailyUsage[]
  modelUsage: ModelUsage[]
  hourlyTraffic: HourlyTraffic[]
  totalCalls: number
  totalTokens: number
  totalCost: number
  todayCalls: number
  todayTokens: number
  todayCost: number
}

function generateDailyUsage(days: number): DailyUsage[] {
  return Array.from({ length: days }, (_, i) => {
    const d = new Date(now)
    d.setDate(d.getDate() - (days - 1 - i))
    const calls = rand(20, 200)
    const tokens = calls * rand(500, 3000)
    return {
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      calls,
      tokens,
      cost: parseFloat((tokens * 0.000002).toFixed(4)),
    }
  })
}

function generateModelUsage(): ModelUsage[] {
  const models = [
    { model: 'deepseek-chat', color: '#818cf8' },
    { model: 'deepseek-reasoner', color: '#34d399' },
    { model: 'gpt-3.5-turbo', color: '#f472b6' },
    { model: 'gpt-4o', color: '#fbbf24' },
  ]
  const totalCalls = rand(800, 2000)
  let remaining = totalCalls
  return models.map((m, i) => {
    const calls = i === models.length - 1 ? remaining : rand(Math.floor(remaining * 0.1), Math.floor(remaining * 0.4))
    remaining -= calls
    const tokens = calls * rand(800, 2500)
    return { ...m, calls, tokens, cost: parseFloat((tokens * 0.000002).toFixed(4)) }
  })
}

function generateHourlyTraffic(): HourlyTraffic[] {
  return Array.from({ length: 24 }, (_, i) => ({
    hour: `${i.toString().padStart(2, '0')}:00`,
    calls: rand(5, i >= 8 && i <= 22 ? 80 : 20),
  }))
}

function generateTopUsers(): TopUser[] {
  const names = ['Alice', 'Bob', 'Charlie', 'Diana', 'Eve', 'Frank', 'Grace', 'Henry', 'Iris', 'Jack']
  return names.map((name, i) => ({
    rank: i + 1,
    username: name,
    userId: 1000 + i,
    calls: rand(3000, 15000),
    cost: randFloat(5, 200),
  }))
}

function generateRevenueTrend(): RevenueTrend[] {
  return Array.from({ length: 12 }, (_, i) => {
    const revenue = randFloat(500, 5000)
    return {
      month: `${i + 1}月`,
      revenue,
      costs: parseFloat((revenue * randFloat(0.3, 0.7)).toFixed(2)),
    }
  })
}

function generateUserGrowth(): UserGrowth[] {
  let total = rand(50, 100)
  let active = Math.floor(total * 0.6)
  return Array.from({ length: 12 }, (_, i) => {
    const newU = rand(5, 30)
    total += newU
    active = Math.floor(total * (0.5 + Math.random() * 0.3))
    return { month: `${i + 1}月`, total, active, new: newU }
  })
}

export function mockAdminAnalytics(): AdminAnalytics {
  const totalCalls = rand(15000, 50000)
  const todayCalls = rand(200, 800)
  return {
    systemStats: {
      totalUsers: rand(200, 500),
      activeUsers: rand(80, 200),
      totalApiKeys: rand(300, 800),
      totalCalls,
      totalCost: randFloat(100, 2000),
      todayCalls,
      todayCost: randFloat(2, 50),
    },
    dailyUsage: generateDailyUsage(30),
    modelUsage: generateModelUsage(),
    hourlyTraffic: generateHourlyTraffic(),
    topUsers: generateTopUsers(),
    revenueTrend: generateRevenueTrend(),
    userGrowth: generateUserGrowth(),
  }
}

export function mockUserAnalytics(): UserAnalytics {
  const totalCalls = rand(500, 3000)
  const todayCalls = rand(20, 100)
  const todayTokens = todayCalls * rand(500, 4000)
  return {
    dailyUsage: generateDailyUsage(14),
    modelUsage: generateModelUsage().slice(0, 3),
    hourlyTraffic: generateHourlyTraffic(),
    totalCalls,
    totalTokens: totalCalls * rand(500, 3000),
    totalCost: randFloat(1, 50),
    todayCalls,
    todayTokens,
    todayCost: parseFloat((todayTokens * 0.000002).toFixed(4)),
  }
}

const MOCK_DELAY_MS = 400

async function mockDelay<T>(data: T): Promise<T> {
  await new Promise(r => setTimeout(r, MOCK_DELAY_MS))
  return data
}

export async function fetchUserAnalytics(): Promise<ApiResult<UserAnalytics>> {
  const data = await mockDelay(mockUserAnalytics())
  return { code: 200, message: 'ok', data, timestamp: Date.now() }
}

export async function fetchAdminAnalytics(): Promise<ApiResult<AdminAnalytics>> {
  const data = await mockDelay(mockAdminAnalytics())
  return { code: 200, message: 'ok', data, timestamp: Date.now() }
}
