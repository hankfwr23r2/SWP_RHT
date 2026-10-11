// Tách câu mở đầu khỏi phần còn lại của đoạn dài, để giao diện chỉ hiện câu đầu và cất phần sau sau nút "Xem thêm".
export function splitLead(text: string): { lead: string; rest: string } {
  const i = text.search(/[.;] /)
  if (i < 0 || i + 2 >= text.length) return { lead: text, rest: '' }
  return { lead: text.slice(0, i + 1), rest: text.slice(i + 2) }
}
