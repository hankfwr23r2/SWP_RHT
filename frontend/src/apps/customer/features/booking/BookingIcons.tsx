import type { SVGProps } from 'react'

function Icon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="square"
      strokeLinejoin="miter"
      aria-hidden="true"
      {...props}
    />
  )
}

export function TruckIcon() {
  return (
    <Icon>
      <path d="M3 7h10v9H3z" />
      <path d="M13 10h4l3 3v3h-7" />
      <circle cx="7" cy="18" r="1.8" />
      <circle cx="17.5" cy="18" r="1.8" />
      <path d="M8.8 18h6.9" />
    </Icon>
  )
}

export function GlobeIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17" />
      <path d="M12 3.5c2.4 2.4 3.8 5.3 3.8 8.5S14.4 18.1 12 20.5c-2.4-2.4-3.8-5.3-3.8-8.5S9.6 5.9 12 3.5Z" />
    </Icon>
  )
}

export function RouteIcon() {
  return (
    <Icon>
      <rect x="4" y="16" width="4" height="4" />
      <rect x="16" y="4" width="4" height="4" />
      <path d="M8 18h4V8h4" />
    </Icon>
  )
}

export function CalendarIcon() {
  return (
    <Icon>
      <rect x="3.5" y="5" width="17" height="15" />
      <path d="M8 3v4M16 3v4M3.5 9.5h17" />
    </Icon>
  )
}

export function SenderIcon() {
  return (
    <Icon>
      <path d="M3 20V9l9-5 9 5v11" />
      <path d="M3 20h18" />
      <path d="M7 20v-7h10v7" />
      <path d="M7 13h10" />
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
