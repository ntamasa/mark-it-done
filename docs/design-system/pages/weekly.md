# Page override — Weekly view (`/week`, default route after login)

## Header
- View title: "Oct 6 – Oct 12" (Mon–Sun, same year → "Oct 6 – Oct 12"; across years → "Dec 29, 2025 – Jan 4, 2026"). Under it: the pagination control (MASTER §8.3) with the labels "Previous week" / "Next week" / "Today".
- URL keeps state: `/week?start=2026-10-05` (always a Monday), so deep links and the browser back button work.

## Mobile (<768): swipeable day carousel **[Decision]**
```
┌ glass top bar ─────────────────────────────┐
│ markitDONE                         (avatar)│
├────────────────────────────────────────────┤
│ ‹   Oct 6 – Oct 12   ›          [Today]    │
│ [Mon 6][Tue 7][Wed 8•][Thu 9][Fri][Sat][Sun]│ ← day-chip strip, horizontally scrollable, • = has todos
├────────────────────────────────────────────┤
│  Wednesday, Oct 8            Day 3 of 7    │
│  ┌ todo card ┐                             │
│  ┌ todo card ┐                             │   ← one day = one full-width slide
│  (empty state if none)                     │
├────────────────────────────────────────────┤
│                                     ( + )  │ FAB
│ [Week] [2 days] [Recurring] [Categories]   │ glass bottom tab bar
└────────────────────────────────────────────┘
```
- The slides are a horizontal `scroll-snap-type: x mandatory` container, one slide per day (`scroll-snap-align: start`, `width: 100%`). Each slide scrolls vertically on its own.
- On open, it jumps to **today** if today is in the week, otherwise to Monday.
- **Edge behaviour [Decision]:** swiping left past Sunday loads the **next week** and lands on its Monday. Swiping right before Monday loads the **previous week** and lands on its Sunday. This uses an `IntersectionObserver` on invisible sentinel slides at both ends, plus a 250ms debounce.
- The day-chip strip: chip 44px tall, pill radius. The active chip has a primary fill; today's chip has a 2px `--color-secondary` ring; a dot under the date means "has todos" (with sr-only text "3 todos"). Tapping a chip scrolls to that slide.
- Accessibility: the carousel is `role="region" aria-roledescription="carousel" aria-label="Week of Oct 6"`; slides are `role="group" aria-roledescription="slide" aria-label="Wednesday Oct 8, 3 of 7"`; ← / → keys move one day; the visible "Day 3 of 7" text helps orientation.

## Tablet (768–1023)
- The same carousel, but each slide is `width: 50%`, so 2 days show per screen. The chip strip stays.

## Desktop (≥1024)
- **7 equal columns** in a CSS grid (`grid-template-columns: repeat(7, minmax(0, 1fr))`, gap 16). No carousel and no swipe.
- Column header: "Mon" (13/500 muted) + "6" (22/700). Today's column header has a `--color-primary` pill behind the date, and the column has a 1px `--color-border` outline.
- Each column is a solid surface (`--color-card` at 70% opacity over the backdrop is **not** allowed, so use solid `--color-card`), radius 16, padding 12, scrolling vertically inside if it overflows (`max-height: calc(100vh - header - 32px)`).
- Cards are in compact mode: the description is hidden and the title is limited to 2 lines.

## Ordering inside a day
1. Not done before done.
2. Then timed todos by time ascending, with "All day" todos first.
3. Then priority (URGENT → LOW).
4. Then name A→Z.

## States
- Loading: 3 `p-skeleton` cards per visible day.
- Error: a `p-message severity="error"` above the grid saying "Couldn't load your week." plus a "Retry" button.
