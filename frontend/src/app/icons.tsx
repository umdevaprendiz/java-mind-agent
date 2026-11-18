const svgProps = {
  width: '1.2em',
  height: '1.2em',
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
} as const

export const SunIcon = () => (
  <svg {...svgProps}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4" />
  </svg>
)
export const MoonIcon = () => (
  <svg {...svgProps}>
    <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
  </svg>
)
export const ChevronIcon = () => (
  <svg {...svgProps} width="1em" height="1em">
    <path d="M6 9l6 6 6-6" />
  </svg>
)
export const SendIcon = () => (
  <svg {...svgProps}>
    <path d="M12 19V6M6 12l6-6 6 6" />
  </svg>
)
export const StopIcon = () => (
  <svg {...svgProps}>
    <rect x="7" y="7" width="10" height="10" rx="2" />
  </svg>
)
export const ClipIcon = () => (
  <svg {...svgProps}>
    <path d="M20 11l-8 8a5 5 0 0 1-7-7l8-8a3.5 3.5 0 0 1 5 5l-8 8a2 2 0 0 1-3-3l7-7" />
  </svg>
)
export const ArrowIcon = () => (
  <svg {...svgProps}>
    <path d="M5 12h14M13 6l6 6-6 6" />
  </svg>
)
export const DownloadIcon = () => (
  <svg {...svgProps}>
    <path d="M12 4v11M7 11l5 5 5-5M5 20h14" />
  </svg>
)
export const CheckIcon = () => (
  <svg {...svgProps}>
    <path d="M5 12.5l4.5 4.5L19 7.5" />
  </svg>
)
export const UploadIcon = () => (
  <svg {...svgProps} width="2em" height="2em">
    <path d="M12 16V4M7 9l5-5 5 5M5 20h14" />
  </svg>
)
