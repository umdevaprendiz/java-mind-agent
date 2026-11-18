/** Xícara com orelhas de coruja: a marca do JMA (mesmo desenho do `public/favicon.svg`). */
export function Logo({ size = 30 }: { size?: number }) {
  return (
    <svg
      className="jma-logo"
      viewBox="0 0 64 64"
      width={size}
      height={size}
      aria-hidden="true"
      focusable="false"
    >
      <g style={{ fill: 'var(--coffee)' }}>
        <path d="M16 28H48V40A12 12 0 0 1 36 52H28A12 12 0 0 1 16 40Z" />
        <path d="M19 28 L23 16 L27 28 Z" />
        <path d="M37 28 L41 16 L45 28 Z" />
      </g>
      <path
        d="M48 32c7 0 11 4 11 8s-4 8-11 8"
        fill="none"
        strokeWidth="5"
        strokeLinecap="round"
        style={{ stroke: 'var(--coffee)' }}
      />
      <circle cx="25" cy="37" r="6" fill="#fff" />
      <circle cx="39" cy="37" r="6" fill="#fff" />
      <circle cx="25" cy="37" r="2.6" fill="#2f6fa8" />
      <circle cx="39" cy="37" r="2.6" fill="#2f6fa8" />
    </svg>
  )
}
