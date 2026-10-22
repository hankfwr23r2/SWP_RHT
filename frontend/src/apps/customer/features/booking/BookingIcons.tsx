import type { SVGProps } from 'react'

function Icon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    />
  )
}

export function TruckIcon() {
  return (
    <Icon>
      <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6h8A1.5 1.5 0 0 1 14 7.5V16H3V7.5Z" />
      <path d="M14 9h3.1a2 2 0 0 1 1.7.9L21 13.2V16h-7V9Z" />
      <path d="M7.5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" />
      <path d="M17.5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" />
      <path d="M9.5 16h4" />
    </Icon>
  )
}

export function GlobeIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3c2.7 2.5 4.2 5.7 4.2 9S14.7 18.5 12 21c-2.7-2.5-4.2-5.7-4.2-9S9.3 5.5 12 3Z" />
    </Icon>
  )
}

export function RouteIcon() {
  return (
    <Icon>
      <circle cx="5.5" cy="18.5" r="2" />
      <circle cx="18.5" cy="5.5" r="2" />
      <path d="M7.5 18.5h4a3 3 0 0 0 3-3v-7a3 3 0 0 1 3-3" strokeDasharray="1 3" />
    </Icon>
  )
}

export function CalendarIcon() {
  return (
    <Icon>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M8 3v4M16 3v4M3 10h18" />
    </Icon>
  )
}

export function SenderIcon() {
  return (
    <Icon>
      <path d="m4 5 16 7-16 7 3-7-3-7Z" />
      <path d="M7 12h13" />
    </Icon>
  )
}

export function UserIcon() {
  return (
    <Icon width="16" height="16">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20a7 7 0 0 1 14 0" />
    </Icon>
  )
}

export function PinIcon() {
  return (
    <Icon>
      <path d="M12 21s7-5.4 7-11a7 7 0 1 0-14 0c0 5.6 7 11 7 11Z" />
      <circle cx="12" cy="10" r="2.5" />
    </Icon>
  )
}

export function SwapIcon() {
  return (
    <Icon width="18" height="18">
      <path d="M4 7h13" />
      <path d="m14 4 3 3-3 3" />
      <path d="M20 17H7" />
      <path d="m10 14-3 3 3 3" />
    </Icon>
  )
}

export function CheckIcon() {
  return (
    <Icon>
      <path d="m5 12 4 4L19 6" />
    </Icon>
  )
}

export function ChevronLeftIcon() {
  return (
    <Icon width="18" height="18">
      <path d="m15 18-6-6 6-6" />
    </Icon>
  )
}

export function ChevronRightIcon() {
  return (
    <Icon width="18" height="18">
      <path d="m9 18 6-6-6-6" />
    </Icon>
  )
}

export function ChevronDownIcon() {
  return (
    <Icon width="18" height="18">
      <path d="m6 9 6 6 6-6" />
    </Icon>
  )
}

export function ArrowRightIcon() {
  return (
    <Icon width="18" height="18">
      <path d="M4 12h15" />
      <path d="m14 7 5 5-5 5" />
    </Icon>
  )
}
