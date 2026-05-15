export interface SolarTerm {
  name: string
  chars: [string, string]
  english: string
  month: number
  day: number
  dateText: string
  seasonText: string
  poem: string
  poemEn: string
}

const SOLAR_TERMS: SolarTerm[] = [
  { name: '小寒', chars: ['小', '寒'], english: 'Lesser Cold', month: 1, day: 5, dateText: '01.05', seasonText: '冬藏', poem: '寒气初深，万物安藏', poemEn: 'Cold deepens softly as all things rest.' },
  { name: '大寒', chars: ['大', '寒'], english: 'Greater Cold', month: 1, day: 20, dateText: '01.20', seasonText: '冬藏', poem: '岁末大寒，静待春归', poemEn: 'At year-end cold, spring waits in silence.' },
  { name: '立春', chars: ['立', '春'], english: 'Start of Spring', month: 2, day: 4, dateText: '02.04', seasonText: '春生', poem: '东风解冻，万物始生', poemEn: 'East wind thaws the earth, life begins again.' },
  { name: '雨水', chars: ['雨', '水'], english: 'Rain Water', month: 2, day: 19, dateText: '02.19', seasonText: '春生', poem: '春雨润物，草木萌新', poemEn: 'Spring rain nourishes every tender sprout.' },
  { name: '惊蛰', chars: ['惊', '蛰'], english: 'Awakening of Insects', month: 3, day: 5, dateText: '03.05', seasonText: '春生', poem: '春雷初动，生机渐醒', poemEn: 'First thunder wakes the hidden vitality.' },
  { name: '春分', chars: ['春', '分'], english: 'Spring Equinox', month: 3, day: 20, dateText: '03.20', seasonText: '春生', poem: '昼夜均分，春意正浓', poemEn: 'Day and night balance in the fullness of spring.' },
  { name: '清明', chars: ['清', '明'], english: 'Pure Brightness', month: 4, day: 5, dateText: '04.05', seasonText: '春生', poem: '风清景明，草木含香', poemEn: 'Clear air and bright fields carry herbal fragrance.' },
  { name: '谷雨', chars: ['谷', '雨'], english: 'Grain Rain', month: 4, day: 20, dateText: '04.20', seasonText: '春生', poem: '雨生百谷，春味渐盈', poemEn: 'Rain feeds the grains as spring grows full.' },
  { name: '立夏', chars: ['立', '夏'], english: 'Start of Summer', month: 5, day: 5, dateText: '05.05', seasonText: '夏长', poem: '告别春的酝酿，迎接夏的生长', poemEn: "Farewell to spring's brewing, welcome the growth of summer." },
  { name: '小满', chars: ['小', '满'], english: 'Grain Buds', month: 5, day: 21, dateText: '05.21', seasonText: '夏长', poem: '小得盈满，万物渐丰', poemEn: 'Small fullness gathers as all things ripen.' },
  { name: '芒种', chars: ['芒', '种'], english: 'Grain in Ear', month: 6, day: 5, dateText: '06.05', seasonText: '夏长', poem: '有芒之谷，顺时而种', poemEn: 'Grains with awns meet their proper season.' },
  { name: '夏至', chars: ['夏', '至'], english: 'Summer Solstice', month: 6, day: 21, dateText: '06.21', seasonText: '夏长', poem: '日长至极，阳气丰盛', poemEn: 'The longest light carries summer abundance.' },
  { name: '小暑', chars: ['小', '暑'], english: 'Lesser Heat', month: 7, day: 7, dateText: '07.07', seasonText: '夏长', poem: '暑气初蒸，清养身心', poemEn: 'First heat rises; nourish with clarity.' },
  { name: '大暑', chars: ['大', '暑'], english: 'Greater Heat', month: 7, day: 23, dateText: '07.23', seasonText: '夏长', poem: '暑热正盛，宜静宜养', poemEn: 'Great heat peaks; calm care suits the body.' },
  { name: '立秋', chars: ['立', '秋'], english: 'Start of Autumn', month: 8, day: 7, dateText: '08.07', seasonText: '秋收', poem: '凉风初起，秋意渐生', poemEn: 'Cool wind begins, and autumn quietly arrives.' },
  { name: '处暑', chars: ['处', '暑'], english: 'End of Heat', month: 8, day: 23, dateText: '08.23', seasonText: '秋收', poem: '暑气渐止，清润入秋', poemEn: 'Heat withdraws as gentle moisture enters autumn.' },
  { name: '白露', chars: ['白', '露'], english: 'White Dew', month: 9, day: 7, dateText: '09.07', seasonText: '秋收', poem: '白露凝珠，秋色澄明', poemEn: 'White dew forms as autumn turns clear.' },
  { name: '秋分', chars: ['秋', '分'], english: 'Autumn Equinox', month: 9, day: 23, dateText: '09.23', seasonText: '秋收', poem: '昼夜平分，收养有度', poemEn: 'Day and night balance; harvest with measure.' },
  { name: '寒露', chars: ['寒', '露'], english: 'Cold Dew', month: 10, day: 8, dateText: '10.08', seasonText: '秋收', poem: '寒露生凉，润养肺脾', poemEn: 'Cold dew brings coolness and gentle nourishment.' },
  { name: '霜降', chars: ['霜', '降'], english: "Frost's Descent", month: 10, day: 23, dateText: '10.23', seasonText: '秋收', poem: '霜华渐降，温养其身', poemEn: 'Frost descends; warmth returns to daily care.' },
  { name: '立冬', chars: ['立', '冬'], english: 'Start of Winter', month: 11, day: 7, dateText: '11.07', seasonText: '冬藏', poem: '冬气始立，收藏养元', poemEn: 'Winter begins; store vitality within.' },
  { name: '小雪', chars: ['小', '雪'], english: 'Lesser Snow', month: 11, day: 22, dateText: '11.22', seasonText: '冬藏', poem: '小雪初临，温润相宜', poemEn: 'First snow comes softly; warmth and moisture suit.' },
  { name: '大雪', chars: ['大', '雪'], english: 'Greater Snow', month: 12, day: 7, dateText: '12.07', seasonText: '冬藏', poem: '雪意渐深，宜补宜藏', poemEn: 'Snow deepens; nourish and preserve.' },
  { name: '冬至', chars: ['冬', '至'], english: 'Winter Solstice', month: 12, day: 22, dateText: '12.22', seasonText: '冬藏', poem: '阴极阳生，静养待春', poemEn: 'At deepest winter, new yang quietly begins.' },
]

export function getCurrentSolarTerm(now = new Date()): SolarTerm {
  const year = now.getFullYear()
  const currentDay = dayKey(now)
  let current = SOLAR_TERMS[SOLAR_TERMS.length - 1]

  for (const term of SOLAR_TERMS) {
    const termDay = dayKey(new Date(year, term.month - 1, term.day))
    if (currentDay >= termDay) {
      current = term
    } else {
      break
    }
  }

  return current
}

export function formatSolarTermDate(term: SolarTerm, now = new Date()) {
  return `${now.getFullYear()}.${term.dateText}`
}

function dayKey(date: Date) {
  return (date.getMonth() + 1) * 100 + date.getDate()
}
