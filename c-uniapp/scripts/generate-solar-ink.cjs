/**
 * Render the existing Huiwen solar-term font into small, transparent ink glyphs.
 * The texture is deterministic; no font downloads or runtime canvas are needed.
 * Regeneration requires Playwright and Chrome: node scripts/generate-solar-ink.cjs
 */
const fs = require('node:fs/promises')
const path = require('node:path')
const { chromium } = require('playwright')

async function main() {
  const root = path.resolve(__dirname, '..')
  const source = await fs.readFile(path.join(root, 'src/utils/solarTerms.ts'), 'utf8')
  const characters = [...new Set([...source.matchAll(/chars: \['(.)', '(.)'\]/g)].flatMap(match => [match[1], match[2]]))]
  if (characters.length === 0) throw new Error('No solar-term characters found')
  const font = await fs.readFile(path.join(root, 'src/static/fonts/HuiwenMinchoGBK-solar-subset.woff2'))
  const output = path.join(root, 'src/static/solar-terms')
  await fs.mkdir(output, { recursive: true })

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage()
    const glyphs = await page.evaluate(async ({ font, characters }) => {
      const face = new FontFace('SolarInk', `url(data:font/woff2;base64,${font})`)
      document.fonts.add(await face.load())
      return characters.map(character => {
        const canvas = document.createElement('canvas')
        canvas.width = canvas.height = 320
        const ctx = canvas.getContext('2d')
        ctx.font = '280px SolarInk'
        const measure = ctx.measureText(character)
        const width = measure.actualBoundingBoxLeft + measure.actualBoundingBoxRight
        const height = measure.actualBoundingBoxAscent + measure.actualBoundingBoxDescent
        const fontSize = 280 * Math.min(1, 276 / Math.max(width, height))
        ctx.font = `${fontSize}px SolarInk`
        const bounds = ctx.measureText(character)
        const x = (320 - bounds.actualBoundingBoxLeft - bounds.actualBoundingBoxRight) / 2 + bounds.actualBoundingBoxLeft
        const y = (320 + bounds.actualBoundingBoxAscent - bounds.actualBoundingBoxDescent) / 2
        const ink = ctx.createLinearGradient(30, 35, 280, 285)
        ink.addColorStop(0, '#16251c')
        ink.addColorStop(0.38, '#26372b')
        ink.addColorStop(0.62, '#14231b')
        ink.addColorStop(1, '#3e4b38')
        ctx.fillStyle = ink
        ctx.shadowColor = 'rgba(24, 39, 28, 0.16)'
        ctx.shadowBlur = 1.15
        ctx.fillText(character, x, y)

        const pixels = ctx.getImageData(0, 0, 320, 320)
        let seed = character.codePointAt(0)
        const random = () => {
          seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0
          return seed / 4294967296
        }
        for (let py = 0; py < 320; py++) {
          for (let px = 0; px < 320; px++) {
            const i = (py * 320 + px) * 4
            if (!pixels.data[i + 3]) continue
            const grain = random()
            // Uneven ink absorption and sparse dry-brush fibres, within the glyph.
            const wash = 0.9 + 0.065 * Math.sin(px * 0.055 + py * 0.026) + 0.025 * grain
            const fibre = Math.sin(px * 1.63 + py * 0.43) > 0.985 && grain > 0.55
            const density = 1 - Math.max(0, pixels.data[i + 1] - 35) / 125
            const alpha = pixels.data[i + 3] * wash * density * (fibre ? 0.68 : grain < 0.013 ? 0.5 : 1)
            // One pigment and 32 density levels preserve texture while compressing well.
            pixels.data[i] = 22
            pixels.data[i + 1] = 35
            pixels.data[i + 2] = 26
            pixels.data[i + 3] = Math.min(255, Math.round(alpha / 8) * 8)
          }
        }
        ctx.putImageData(pixels, 0, 0)
        return { code: character.codePointAt(0).toString(16), png: canvas.toDataURL('image/png').split(',')[1] }
      })
    }, { font: font.toString('base64'), characters })
    for (const glyph of glyphs) {
      await fs.writeFile(path.join(output, `ink-${glyph.code}.png`), Buffer.from(glyph.png, 'base64'))
    }
    console.log(`Rendered ${glyphs.length} solar-term glyphs from the existing font.`)
  } finally {
    await browser.close()
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
