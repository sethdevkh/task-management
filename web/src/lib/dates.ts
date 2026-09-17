export function utcDateString(date = new Date()): string {
  return date.toISOString().slice(0, 10)
}

export function daysAgoIso(days: number): string {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString()
}
