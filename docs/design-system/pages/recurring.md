# Page override — Recurring list (`/recurring`)

## Purpose
One row per **series** **[Decision]**. You can see all recurring todos, edit a series, or cancel its recurrence.

## Filters
A `p-selectbutton` with: **Active** (default) · **Ended** · **Cancelled** · **All**. Search input: "Search recurring todos" (filters by name, 300ms debounce).

## Mobile (<768): stacked cards
```
┌──────────────────────────────────────────┐
│ Gym                       [⟳ Active]  ⋮  │
│ Every Mon, Wed, Fri · 18:00              │
│ Next: Wed, Oct 22 · Ends after 20 (7 left)│
│ [High] (Health)                          │
└──────────────────────────────────────────┘
```
## ≥768: `p-table`
Columns: Name · Rule ("Every 3 days", "Weekly: Mon, Wed", "Monthly: 1st, 15th") · Next occurrence · Ends ("Never" / "After 20 (7 left)" / "On Dec 31, 2026") · Priority tag · Category chip · Status tag · actions (`pi pi-pencil` "Edit series", `pi pi-ban` "Cancel recurrence"). Sorted by next occurrence by default; Name and Next are sortable.

## Status tags
Active = `success` "Active"; Ended (end reached) = `secondary` "Ended"; Cancelled = `contrast` "Cancelled".

## Actions
- **Edit series** opens the todo editor on the next upcoming occurrence with the scope preselected to "This and future" (scope selector still visible).
- **Cancel recurrence** opens `p-confirmdialog`:
  - Header: "Cancel recurrence?"
  - Message: "“Gym” will stop repeating. Today's and past occurrences are kept; 12 future occurrences will be removed."
  - Buttons: "Keep repeating" (text) / "Cancel recurrence" (danger).
- After cancelling: toast "Recurrence cancelled", the row moves to the Cancelled filter.

## Empty state
`pi pi-sync` + "No recurring todos yet. Create a todo and turn on “Repeat”." + "New todo" button.
