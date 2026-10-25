import { useState } from 'react'
import Monogram from './Monogram'

/** Where an icon is too small to scale up cleanly, it's shown at its own size rather than blurred. */
const SMALL_ICON = { sm: 'size-4', md: 'size-4', lg: 'size-5' }

const SIZES = {
  sm: 'size-6 rounded-md p-0.5',
  md: 'size-9 rounded-lg p-1',
  lg: 'size-11 rounded-xl p-1.5',
}

/**
 * The company's logo, served by the app (which fetched it once), on a white tile so dark logos read in dark
 * mode too. Falls back to initials when there is no logo.
 */
export default function CompanyLogo({ companyId, name, size = 'md' }) {
  const [failed, setFailed] = useState(false)
  const [small, setSmall] = useState(false)
  if (companyId == null || failed) return <Monogram name={name} size={size} />
  return (
    <span className={`inline-flex shrink-0 items-center justify-center bg-white ring-1 ring-rule ${SIZES[size]}`}>
      <img
        src={`/api/companies/${companyId}/logo`}
        alt=""
        loading="lazy"
        decoding="async"
        onError={() => setFailed(true)}
        onLoad={(e) => setSmall(e.currentTarget.naturalWidth > 0 && e.currentTarget.naturalWidth < 32)}
        className={small ? SMALL_ICON[size] : 'size-full rounded-[inherit] object-contain'}
      />
    </span>
  )
}
