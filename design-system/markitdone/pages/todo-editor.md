# Page override — Todo editor (bottom sheet / right drawer)

Overrides MASTER §8.2 for the create/edit surface. Also used by "Edit series".

## Structure
```
┌ glass header ───────────────────────────────┐
│ ▬ (drag handle, mobile)                     │
│ New todo / Edit todo                    ✕   │
├ solid body (scrolls) ───────────────────────┤
│ Name *                [_______________]     │
│ Description           [_______________]     │  textarea autoResize, max 2000
│ Expiration date *     [📅 Oct 21, 2026 ]    │
│   [ ] Add time  → Time [ 14:30 ]            │
│ Priority              [ Medium        ▾]    │  default MEDIUM
│ Category              [ No category   ▾]    │  + "Manage categories" link
│ ─────────────────────────────────────────── │
│ Repeat                [ toggle ]            │
│   Frequency  ( Every X days | Weekly | Monthly )  p-selectbutton
│   Every [ 3 ] days                (if days)  │
│   On   [Mo][Tu][We][Th][Fr][Sa][Su] (weekly) │
│   On days [1][2]…[31]  multi       (monthly) │
│   Starts [📅 Oct 21, 2026]                   │
│   Ends  ( Never | After [10] times | On [📅]) │
│   Summary: “Every Mon, Wed · 10 times”       │
├ sticky footer ──────────────────────────────┤
│ [Delete]                 [Cancel] [Save]    │
└─────────────────────────────────────────────┘
```

## Rules
- Fields appear in the order shown. Required fields carry " *" and `aria-required`.
- **Date validity [Decision]:** every `p-datepicker` disables impossible dates natively. The server rejects invalid dates too. "Ends on" must be ≥ "Starts"; "After N" allows N from 1 to 999.
- **Monthly days:** a 7-column grid of toggle buttons 1–31 (44px). Days 29–31 show the helper text: "In shorter months this falls on the last day of the month."
- The summary line updates live (`aria-live="polite"`).
- **Repeat toggle on an existing one-off todo:** allowed (one-off → recurring) **[Decision]**. On an existing recurring occurrence, the toggle is **locked on** with the helper text "To stop repeating, cancel the recurrence from the Recurring list."
- **Edit scope (recurring occurrence) [Decision]:** clicking Save opens a `p-dialog`: "Apply changes to…" ( • Only this occurrence  ○ This and all future occurrences ) → [Cancel] [Save]. Past occurrences are never changed. Helper under "future": "Occurrences you edited individually keep their changes."
- **Delete in editor:**
  - One-off: confirm "Delete “{name}”?"
  - Recurring occurrence: confirm "Delete this occurrence?" with the message "Only Oct 22 is removed. “Gym” keeps repeating."
- Unsaved changes plus close/Esc/swipe-down → confirm "Discard changes?"
- Save shows `[loading]`, then closes, then a toast ("Todo created" / "Todo updated" / "Occurrence updated" / "12 occurrences updated").
- Focus: on open, focus goes to the Name field; on close, it returns to the trigger (FAB or card).
