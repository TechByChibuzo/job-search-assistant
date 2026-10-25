/** Muted hues that read on both themes; a company always gets the same one. */
const HUES = [222, 262, 190, 152, 28, 340, 205, 95]

function hueFor(name) {
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return HUES[Math.abs(hash) % HUES.length]
}

/** A company's initials in a tinted square, standing in for a logo. */
export default function Monogram({ name, size = 'md' }) {
  const words = name.trim().split(/\s+/)
  const initials = (words.length > 1 ? words[0][0] + words[1][0] : name.slice(0, 2)).toUpperCase()
  const hue = hueFor(name)
  const box = size === 'lg' ? 'size-11 text-base rounded-xl' : size === 'sm' ? 'size-6 text-[10px] rounded-md' : 'size-9 text-xs rounded-lg'
  return (
    <span
      aria-hidden
      className={`inline-flex shrink-0 items-center justify-center font-semibold ${box}`}
      style={{
        background: `light-dark(hsl(${hue} 70% 94%), hsl(${hue} 35% 22%))`,
        color: `light-dark(hsl(${hue} 55% 32%), hsl(${hue} 70% 82%))`,
      }}
    >
      {initials}
    </span>
  )
}
