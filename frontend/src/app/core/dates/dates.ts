/** Pure helpers on local calendar dates as YYYY-MM-DD strings. No time zone conversion. */

const pad = (n: number): string => String(n).padStart(2, '0');

/** Formats a Date's local calendar date as YYYY-MM-DD. */
export function toIso(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Parses YYYY-MM-DD into a Date at local noon (safe across DST changes). */
export function fromIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12);
}

export function todayIso(now: Date = new Date()): string {
  return toIso(now);
}

export function addDays(iso: string, days: number): string {
  const d = fromIso(iso);
  d.setDate(d.getDate() + days);
  return toIso(d);
}

/** The Monday of the week containing the date (weeks start on Monday). */
export function mondayOf(iso: string): string {
  const day = fromIso(iso).getDay();
  return addDays(iso, -((day + 6) % 7));
}

function fmt(iso: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('en-US', options).format(fromIso(iso));
}

/** "Oct 21" */
export function formatShort(iso: string): string {
  return fmt(iso, { month: 'short', day: 'numeric' });
}

/** "Wed, Oct 21" */
export function formatDay(iso: string): string {
  return fmt(iso, { weekday: 'short', month: 'short', day: 'numeric' });
}

/** "Wednesday, Oct 21" */
export function formatLong(iso: string): string {
  return fmt(iso, { weekday: 'long', month: 'short', day: 'numeric' });
}

/** "Oct 19 – Oct 25", with years when they differ: "Dec 29, 2025 – Jan 4, 2026". */
export function formatRange(from: string, to: string): string {
  if (fromIso(from).getFullYear() === fromIso(to).getFullYear()) {
    return `${formatShort(from)} – ${formatShort(to)}`;
  }
  const withYear = (iso: string) => fmt(iso, { month: 'short', day: 'numeric', year: 'numeric' });
  return `${withYear(from)} – ${withYear(to)}`;
}

/** "14:30" from HH:mm or HH:mm:ss; "All day" when there is no time. */
export function formatTime(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : 'All day';
}
