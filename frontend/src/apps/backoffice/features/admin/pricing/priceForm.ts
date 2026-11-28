export function formText(form: FormData, name: string) {
  return String(form.get(name) ?? '').trim()
}

export function optionalNumber(value: string) {
  return value.trim() === '' ? undefined : Number(value)
}
