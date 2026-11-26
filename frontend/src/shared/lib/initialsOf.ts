export function initialsOf(name = '') {
  let depth = 0
  let visible = ''
  for (const character of name.normalize()) {
    if (character === '(') depth++
    else if (character === ')' && depth > 0) {
      depth--
      if (depth === 0) visible += ' '
    } else if (depth === 0) visible += character
  }

  return visible.split(/\s+/u)
    .map(word => word.match(/\p{L}/u)?.[0])
    .filter(Boolean)
    .slice(-2)
    .join('')
    .toUpperCase()
}
