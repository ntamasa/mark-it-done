# Page override — 2-day view (`/two-days`)

Inherits everything from `weekly.md` except the following.

- Range = 2 days, starting **today** by default. URL: `/two-days?start=2026-10-21`.
- Title: "Today & tomorrow" when the range is today + tomorrow; otherwise "Oct 23 – Oct 24". Pagination moves **2 days** at a time; labels "Previous 2 days" / "Next 2 days" / "Today".
- **Mobile (<768) [Decision: same swipe carousel]:** a 2-slide carousel with a strip of 2 day chips. Swiping past the 2nd slide loads the next 2-day range; swiping before the 1st loads the previous range. Shows "Day 1 of 2".
- **≥768:** 2 equal columns side by side (no carousel). Cards are in **comfortable** mode, showing the first line of the description (14/20 muted, truncated with an ellipsis and a `title` tooltip).
- Day header shows a relative label + date: "Today · Tue, Oct 21" and "Tomorrow · Wed, Oct 22"; other ranges show "Thu, Oct 23".
