import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { formatSolarTermCountdown, formatSolarTermPosition, getCurrentSolarTermInfo, parseLocalDateTime } from '../src/utils/solarTerms.ts'

const seasons = [
  ['春季', [['立春', '02-04'], ['雨水', '02-19'], ['惊蛰', '03-05'], ['春分', '03-20'], ['清明', '04-05'], ['谷雨', '04-20']]],
  ['夏季', [['立夏', '05-05'], ['小满', '05-21'], ['芒种', '06-05'], ['夏至', '06-21'], ['小暑', '07-07'], ['大暑', '07-23']]],
  ['秋季', [['立秋', '08-07'], ['处暑', '08-23'], ['白露', '09-07'], ['秋分', '09-23'], ['寒露', '10-08'], ['霜降', '10-23']]],
  ['冬季', [['立冬', '11-07'], ['小雪', '11-22'], ['大雪', '12-07'], ['冬至', '12-22'], ['小寒', '01-05'], ['大寒', '01-20']]],
]
const ordinals = ['一', '二', '三', '四', '五', '六']
const infoAt = date => getCurrentSolarTermInfo(parseLocalDateTime(`${date}T12:00:00`))

test('all 24 solar terms show the correct season and ordinal, including January winter terms', () => {
  for (const [season, terms] of seasons) {
    terms.forEach(([name, date], index) => {
      const info = infoAt(`2026-${date}`)
      assert.equal(info.currentTerm.name, name)
      assert.equal(formatSolarTermPosition(info.currentTerm), `${name} · ${season}第${ordinals[index]}个节气`)
    })
  }
})

test('countdown changes at the next solar term and across the year boundary', () => {
  assert.equal(formatSolarTermCountdown(infoAt('2026-10-24')), '距立冬还有 14 天')
  assert.equal(formatSolarTermCountdown(infoAt('2026-11-06')), '距立冬还有 1 天')
  assert.equal(formatSolarTermCountdown(infoAt('2026-11-07')), '距小雪还有 15 天')
  assert.equal(formatSolarTermCountdown(infoAt('2026-12-31')), '距小寒还有 5 天')
  const newYear = infoAt('2027-01-01')
  assert.equal(newYear.currentDateText, '2026.12.22')
  assert.equal(newYear.nextDateText, '2027.01.05')
  assert.equal(formatSolarTermCountdown(newYear), '距小寒还有 4 天')
})

test('every seasonal character has a packaged ink image for offline mini-app rendering', () => {
  const characters = new Set(seasons.flatMap(([, terms]) => terms.flatMap(([name]) => [...name])))
  for (const character of characters) {
    const asset = new URL(`../src/static/solar-terms/ink-${character.codePointAt(0).toString(16)}.png`, import.meta.url)
    const png = readFileSync(asset)
    assert.equal(png.subarray(1, 4).toString(), 'PNG', character)
    assert.equal(png.readUInt32BE(16), 320, `${character}: width`)
    assert.equal(png.readUInt32BE(20), 320, `${character}: height`)
  }
})
