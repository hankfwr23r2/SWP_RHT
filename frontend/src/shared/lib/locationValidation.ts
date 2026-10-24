const LOCATION_DETAIL_PATTERN = /^[\p{L}\p{M}\p{N}\s]*$/u
const LOCATION_DETAIL_SPECIAL_CHARACTERS_PATTERN = /[^\p{L}\p{M}\p{N}\s]/gu

export function isValidLocationDetail(value: string): boolean {
  return LOCATION_DETAIL_PATTERN.test(value)
}

export function removeLocationDetailSpecialCharacters(value: string): string {
  return value.replace(LOCATION_DETAIL_SPECIAL_CHARACTERS_PATTERN, '')
}
