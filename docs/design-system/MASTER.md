# markitDONE — Design System (MASTER)

> **How to use:** When building a screen, first check `pages/<screen>.md`. Rules there **override** this file. Otherwise follow this file exactly.
> Generated with `ui-ux-pro-max` (`--design-system`, query "productivity todo task planner calendar minimal", Motion 2/10, Density 6/10) and then adjusted to the decisions confirmed by the product owner (glassy minimalist Swiss style, mobile-first, light + dark).
> Where this file departs from the skill's raw output, the reason is noted as **[Decision]** or **[Fallback]** (the skill database had no matching entry, so general guidance was used).

**Product:** markitDONE — personal todo planner with recurring tasks
**Category:** Productivity tool (web app, logged-in only, no landing page)
**Stack:** Angular 22 (standalone, signals, signal forms) + PrimeNG 22 (Aura preset, customised) + PrimeIcons 8
**Language:** English only

---

## 1. Style direction

**Name:** Glassy Swiss Minimalism **[Decision]**

- **Base:** Minimalism & Swiss Style (skill style id `minimalism-and-swiss-style`): grid-based, plenty of white space, strong type hierarchy, only the essential elements, one accent colour per view.
- **Glass layer [Decision, Fallback]:** frosted glass (translucent fill + `backdrop-filter: blur`) is used **only for app chrome and overlays**: top bar, bottom tab bar / side rail, drawers and bottom sheets, dialogs, popovers/menus, toasts. Content surfaces (todo cards, day columns, tables, forms inside sheets) stay **solid**, so text is always readable. The skill database has no glassmorphism entry, so this layer follows general guidance plus the skill's `blur-purpose` rule: blur marks a layer that floats above the content.
- **Shape [Decision]:** 16px corner radius on panels, cards, sheets and dialogs; soft shadows (see §5).
- **Backdrop [Decision]:** a fixed, static, low-saturation teal/orange mesh gradient behind the whole app, so the glass has something to blur. No animation.
- **Density:** standard (16–64px spacing scale).
- **Motion:** subtle (see §7).

**Anti-patterns for this product** (from the skill + product decisions):
- ❌ Glass on todo cards or long text: hurts readability.
- ❌ Gradients on buttons or text; gradients appear only in the page backdrop.
- ❌ Emoji as icons; use PrimeIcons only (`pi pi-*`). **[Decision]** PrimeIcons override the skill's Heroicons/Lucide suggestion, because the spec requires PrimeNG for icons.
- ❌ Colour as the only signal (priority, overdue, recurring all need text or an icon too).
- ❌ Layout-shifting hovers (no `scale` on cards; use colour, shadow or translateY of at most 1px).
- ❌ Complex onboarding, slow performance.
- ❌ The skill's "Product Demo + Features" landing pattern and GSAP scroll reveal. They don't apply because there is no marketing page **[Decision]**.

---

## 2. Colour tokens

Every component uses **semantic CSS variables**, never raw hex. Defined in `frontend/src/styles/tokens.scss` on `:root` (light) and `:root.app-dark` (dark). PrimeNG is themed from the same values (see §10).

### 2.1 Light theme (skill palette "Productivity Tool")

| Token | Value | Use |
|---|---|---|
| `--color-primary` | `#0D9488` | Primary buttons, active nav, focus ring, selected states |
| `--color-primary-strong` | `#0F766E` | **Text** in primary colour (links, active tab label) **[Decision]**: `#0D9488` on white is only 3.74:1 |
| `--color-on-primary` | `#000000` | Text/icons on primary fills (5.61:1) |
| `--color-secondary` | `#14B8A6` | Secondary highlights, today marker |
| `--color-on-secondary` | `#0F172A` | |
| `--color-accent` | `#EA580C` | **The single CTA per view** (FAB "+", "Save") |
| `--color-on-accent` | `#000000` | Text on accent (5.90:1). Never use white on accent (3.56:1 fails) |
| `--color-background` | `#F0FDFA` | Base under the mesh backdrop |
| `--color-foreground` | `#134E4A` | Body text (9.09:1 on background) |
| `--color-card` | `#FFFFFF` | Solid content surfaces |
| `--color-card-foreground` | `#134E4A` | (9.48:1) |
| `--color-muted` | `#E8F1F4` | Subtle fills, disabled backgrounds, skeletons |
| `--color-muted-foreground` | `#475569` | Secondary text, meta (7.27:1) |
| `--color-border` | `#99F6E4` | Accent borders (selected, focus-adjacent) |
| `--color-border-subtle` | `rgba(19, 78, 74, 0.12)` | Default card/input borders **[Decision]** |
| `--color-destructive` | `#DC2626` | Delete, errors, overdue |
| `--color-on-destructive` | `#FFFFFF` | (4.83:1) |
| `--color-ring` | `#0D9488` | Focus outline |
| `--glass-fill` | `rgba(255, 255, 255, 0.62)` | Glass panels |
| `--glass-border` | `rgba(255, 255, 255, 0.55)` | 1px glass edge |
| `--glass-fallback` | `#FFFFFF` | Solid fill when blur is unsupported or reduced transparency is requested |
| `--scrim` | `rgba(4, 47, 46, 0.32)` | Overlay behind dialogs/sheets |

### 2.2 Dark theme **[Decision: derived teal dark]**

| Token | Value | Contrast check |
|---|---|---|
| `--color-primary` | `#2DD4BF` | 9.04:1 on surface |
| `--color-primary-strong` | `#2DD4BF` | (same) |
| `--color-on-primary` | `#0B1414` | 10.03:1 |
| `--color-secondary` | `#5EEAD4` | |
| `--color-on-secondary` | `#0B1414` | |
| `--color-accent` | `#FB923C` | |
| `--color-on-accent` | `#0B1414` | 8.25:1 |
| `--color-background` | `#0B1414` | |
| `--color-foreground` | `#E6FFFA` | 17.83:1 |
| `--color-card` | `#10201F` | |
| `--color-card-foreground` | `#E6FFFA` | 16.06:1 |
| `--color-muted` | `#16302E` | |
| `--color-muted-foreground` | `#94A3B8` | 6.56:1 on card |
| `--color-border` | `rgba(153, 246, 228, 0.32)` | |
| `--color-border-subtle` | `rgba(153, 246, 228, 0.16)` | |
| `--color-destructive` | `#F87171` | 6.08:1 on card |
| `--color-on-destructive` | `#0B1414` | 6.75:1 |
| `--color-ring` | `#2DD4BF` | |
| `--glass-fill` | `rgba(16, 32, 31, 0.58)` | |
| `--glass-border` | `rgba(153, 246, 228, 0.14)` | |
| `--glass-fallback` | `#10201F` | |
| `--scrim` | `rgba(0, 0, 0, 0.55)` | |

### 2.3 Domain colours

**Priority, shown as a PrimeNG `p-tag` chip [Decision].** The label text is always visible.

| Priority | `p-tag` severity | Icon | Label |
|---|---|---|---|
| LOW | `secondary` | `pi pi-angle-down` | "Low" |
| MEDIUM | `info` | `pi pi-minus` | "Medium" |
| HIGH | `warn` | `pi pi-angle-up` | "High" |
| URGENT | `danger` | `pi pi-angle-double-up` | "Urgent" |

**Category, shown as a coloured chip [Decision].** The user picks the colour from a fixed palette of 10 swatches. Each swatch has a defined text colour that passes 4.5:1, and the chip's text colour comes from the swatch, never from the theme:

| Swatch | Fill | Text |
|---|---|---|
| teal | `#99F6E4` | `#134E4A` |
| sky | `#BAE6FD` | `#0C4A6E` |
| indigo | `#C7D2FE` | `#312E81` |
| violet | `#DDD6FE` | `#4C1D95` |
| pink | `#FBCFE8` | `#831843` |
| rose | `#FECDD3` | `#881337` |
| orange | `#FED7AA` | `#7C2D12` |
| amber | `#FDE68A` | `#78350F` |
| lime | `#D9F99D` | `#365314` |
| slate | `#E2E8F0` | `#1E293B` |

"No category" shows no chip at all.

**Recurring todos get a tag/badge [Decision]:** a `p-tag` with `severity="contrast"`, icon `pi pi-sync` and the text "Recurring", plus a tooltip with the rule summary (e.g. "Every Mon, Wed · until Dec 31").

**Overdue (not done and past its expiration) [Decision]:** the card gets a 3px left border in `--color-destructive`, the due time turns `--color-destructive` with the prefix icon `pi pi-exclamation-circle`, and the screen-reader text reads "Overdue". **A done todo is never marked overdue.**

**Done:** the checkbox is checked, the title has a line-through and switches to `--color-muted-foreground`, and the card opacity drops to 0.72. Text stays ≥4.5:1, because opacity is applied to the decoration only, not to the text colour token.

---

## 3. Backdrop (mesh gradient)

```scss
// frontend/src/styles/backdrop.scss
.app-backdrop {
  position: fixed; inset: 0; z-index: -1; pointer-events: none;
  background:
    radial-gradient(40rem 30rem at 10% 0%,   color-mix(in srgb, var(--color-primary) 18%, transparent), transparent 70%),
    radial-gradient(36rem 28rem at 95% 20%,  color-mix(in srgb, var(--color-accent) 12%, transparent), transparent 70%),
    radial-gradient(44rem 34rem at 50% 110%, color-mix(in srgb, var(--color-secondary) 14%, transparent), transparent 70%),
    var(--color-background);
}
@media (prefers-reduced-transparency: reduce) {
  .app-backdrop { background: var(--color-background); }
}
```

Static, no animation. Rendered once in `app.component.html` as `<div class="app-backdrop" aria-hidden="true"></div>`.

---

## 4. Glass recipe

```scss
// frontend/src/styles/glass.scss
@mixin glass {
  background: var(--glass-fill);
  border: 1px solid var(--glass-border);
  backdrop-filter: blur(16px) saturate(140%);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
  box-shadow: var(--shadow-glass);
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .glass { background: var(--glass-fallback); }
}
@media (prefers-reduced-transparency: reduce) {
  .glass { background: var(--glass-fallback); backdrop-filter: none; -webkit-backdrop-filter: none; }
}
.glass { @include glass; }
```

Use `.glass` (or the mixin) **only** on: top bar, bottom tab bar, side rail, `p-drawer`, `p-dialog`, `p-popover`, `p-menu`, `p-toast`, `p-confirmdialog`. Any text placed on glass must use `--color-foreground` (never muted text below 14px).

---

## 5. Shape, elevation, spacing

| Token | Value | Use |
|---|---|---|
| `--radius-sm` | `8px` | Inputs, buttons, chips |
| `--radius-md` | `12px` | Small cards, menus |
| `--radius-lg` | `16px` | Panels, todo cards, sheets, dialogs **[Decision]** |
| `--radius-pill` | `999px` | Tags, day chips, FAB |
| `--shadow-sm` | `0 1px 2px rgba(4, 47, 46, 0.06)` | Inputs |
| `--shadow-md` | `0 4px 12px rgba(4, 47, 46, 0.08)` | Todo cards |
| `--shadow-lg` | `0 12px 32px rgba(4, 47, 46, 0.12)` | Dialogs, sheets, FAB |
| `--shadow-glass` | `0 8px 32px rgba(4, 47, 46, 0.10)` | Glass panels |

Dark theme: the same shadows with `rgba(0, 0, 0, 0.35–0.5)`.

Spacing (Density 6/10, from the skill): `--space-xs 4px`, `--space-sm 8px`, `--space-md 16px`, `--space-lg 24px`, `--space-xl 32px`, `--space-2xl 48px`, `--space-3xl 64px`. All layout gaps use these tokens.

Grid: 4-column grid on mobile (16px gutters, 16px side margins), 8-column at ≥768px (24px gutters), 12-column at ≥1024px (24px gutters, content max-width 1440px).

---

## 6. Typography

- **Font:** Plus Jakarta Sans (headings and body), weights 400/500/600/700. Self-host via `@fontsource-variable/plus-jakarta-sans` (no runtime Google request), with `font-display: swap`.
- **Scale (mobile-first; ≥1024px in brackets):**

| Role | Size / line-height / weight |
|---|---|
| Display (view title, e.g. "Oct 6 – Oct 12") | 22/28/700 [28/36] |
| Title (sheet/dialog title) | 18/24/600 [20/28] |
| Subtitle (day header) | 15/20/600 |
| Body | 16/24/400 (never below 16px in inputs, which prevents iOS zoom) |
| Body small (card meta, description preview) | 14/20/400 |
| Label (form labels, tags, tabs) | 13/16/500, letter-spacing 0.01em |
| Caption (counts, helper text) | 12/16/400, only in `--color-muted-foreground` ≥ 4.5:1 |

- Swiss hierarchy: weight and size carry the hierarchy, not colour. Use tabular numbers (`font-variant-numeric: tabular-nums`) for dates and times.

---

## 7. Motion **[Decision: subtle]**

| Interaction | Duration | Easing |
|---|---|---|
| Hover/press colour change | 150ms | ease-out |
| Checkbox check, done strike-through | 200ms | ease-out |
| Bottom sheet / drawer enter | 250ms | `cubic-bezier(0.2, 0, 0, 1)` |
| Bottom sheet / drawer exit | 180ms | ease-in (exit faster than enter) |
| Dialog enter | 200ms fade + scale 0.98→1 | ease-out |
| Carousel day snap | native CSS scroll-snap | — |
| Toast | 200ms slide/fade | ease-out |

- Only animate `transform` and `opacity`.
- `@media (prefers-reduced-motion: reduce)`: every duration drops to 0ms except opacity fades of at most 100ms, and carousel scrolling uses `scroll-behavior: auto`.
- No GSAP.

---

## 8. Layout and navigation (mobile-first)

Breakpoints: **base 0–767** (mobile), **md ≥768** (tablet), **lg ≥1024** (desktop), **xl ≥1440**.

### 8.1 App shell
- **Top bar (glass, 56px, sticky):** app wordmark "markitDONE" (left) · view title + pagination (centre on lg, below the bar on mobile) · avatar menu (right): theme switch (System / Light / Dark), "Categories", "Log out".
- **Primary nav [Decision]:**
  - **Mobile/tablet (<1024):** glass **bottom tab bar** (64px + safe-area inset), with 4 tabs that each have an icon **and** a label: Week (`pi pi-calendar`), 2 days (`pi pi-clone`), Recurring (`pi pi-sync`), Categories (`pi pi-tags`).
  - **Desktop (≥1024):** glass **left side rail**, 88px wide, with the same 4 items stacked as icon + label.
  - The active item shows a `--color-primary` fill pill behind the icon, the label in `--color-primary-strong`, and `aria-current="page"`.
- **FAB:** a "+" button (56×56, `--color-accent`, `pi pi-plus`, `aria-label="New todo"`) bottom-right. It sits above the tab bar on mobile and at `bottom: 32px; right: 32px` on desktop. It opens the todo editor.
- The content area has padding that keeps it clear of the fixed top bar, bottom bar and safe areas (`env(safe-area-inset-*)`). Nothing is hidden behind fixed bars.

### 8.2 Todo editor surface **[Decision]**
- **Mobile:** `p-drawer position="bottom"`, full height minus 24px (rounded top 16px), with a drag handle, glass header, solid form body, and sticky footer with Cancel / Save.
- **≥1024:** `p-drawer position="right"`, 480px wide, same structure.
- If there are unsaved changes, dismissing asks for confirmation ("Discard changes?").

### 8.3 Pagination control (Weekly + 2-day)
`[ ‹ ]  Oct 6 – Oct 12  [ › ]  [Today]`. Icon buttons are 44×44 with `aria-label`s "Previous week" / "Next week" (or "Previous 2 days" / "Next 2 days"). The label is an `aria-live="polite"` region, and "Today" is disabled when the current range already contains today.

---

## 9. Components (PrimeNG mapping)

| UI element | PrimeNG | Notes |
|---|---|---|
| Buttons | `p-button` | Primary = `severity` default (teal fill, black text); the single CTA per view uses the `.btn-accent` class (orange). Height ≥44px. |
| Icon buttons | `p-button [rounded] [text]` + `aria-label` | 44×44 hit area |
| Text input | `p-inputtext` + `p-floatlabel variant="on"` **is not used**. Use a visible `<label>` above the input. | Skill rule `input-labels` |
| Textarea | `p-textarea autoResize` | |
| Select | `p-select` | Priority, category |
| Multi-select chips | `p-selectbutton [multiple]` | Weekdays (Mon–Sun) |
| Date picker | `p-datepicker` | Impossible dates are disabled (see recurrence rules); `showIcon`, `iconDisplay="input"` |
| Time | `p-datepicker [timeOnly]` | Optional "Add time" toggle |
| Checkbox | `p-checkbox [binary]` | Done toggle, 24px box with a 44px hit area |
| Tag | `p-tag` | Priority, Recurring |
| Chip | custom `app-category-chip` | Swatch colours from §2.3 |
| Dialog | `p-dialog [modal]` | Glass |
| Confirm | `p-confirmdialog` | All deletes and cancellations **[Decision]** |
| Toast | `p-toast position="top-center"` (mobile) / `"bottom-right"` (desktop) | Auto-dismiss after 4s, `aria-live="polite"` |
| Table/list | `p-table` (≥768) / stacked list (mobile) | Recurring list, category-delete dialog |
| Skeleton | `p-skeleton` | Loading states longer than 300ms |
| Menu | `p-menu [popup]` | Avatar menu, card overflow menu |
| Drawer | `p-drawer` | Editor sheet / right drawer |

### 9.1 Todo card (solid surface)
```
┌───────────────────────────────────────────┐
│ ☐  Buy groceries                    ⋮     │  ← checkbox · title (16/600, 2 lines max) · overflow menu (Edit, Delete)
│    14:30 · [High] [⟳ Recurring]           │  ← time or "All day" · priority p-tag · recurring tag
│    (Work)                                 │  ← category chip
└───────────────────────────────────────────┘
```
- Radius 16, `--shadow-md`, padding 12/16, background `--color-card`, border `1px solid --color-border-subtle`.
- Tapping the card body opens the editor; the checkbox toggles done; the overflow menu offers Edit / Delete.
- The whole card is reachable by keyboard: the card is a `<article>` with the title as a `<button>`; the checkbox and menu are separate tab stops.
- An overdue card gets the 3px destructive left border (§2.3).

### 9.2 Empty states
Every list or day without todos shows a muted icon (`pi pi-check-circle`), a short sentence ("Nothing planned for Tuesday") and, on full-view empties, a "New todo" text button.

### 9.3 Forms (global rules from the skill)
- A visible label on every field; helper text below; errors appear **below the field** on blur (`inline-validation`) and on submit.
- On a failed submit, focus the first invalid field; errors use `role="alert"`.
- Input height ≥44px; font-size 16px.
- Show the password rules as a live checklist under the password field (✓/✗ with text: "12+ characters", "Uppercase", "Lowercase", "Digit", "Symbol").

---

## 10. PrimeNG theme wiring

- Use the Aura preset via `definePreset(Aura, { semantic: { primary: { 50..950 teal scale }, colorScheme: { light: {...}, dark: {...} } } })` in `frontend/src/app/theme/markitdone-preset.ts`.
- Teal scale: 50 `#F0FDFA`, 100 `#CCFBF1`, 200 `#99F6E4`, 300 `#5EEAD4`, 400 `#2DD4BF`, 500 `#14B8A6`, 600 `#0D9488`, 700 `#0F766E`, 800 `#115E59`, 900 `#134E4A`, 950 `#042F2E`.
- Light `primary.color = {primary.600}`; dark `primary.color = {primary.400}`.
- `providePrimeNG({ theme: { preset: MarkitdonePreset, options: { darkModeSelector: '.app-dark', cssLayer: false } } })`.
- `formField.borderRadius = 8px`, `overlay.modal.borderRadius = 16px`, `content.borderRadius = 16px`.

## 11. Theme switching **[Decision]**
- Options: System / Light / Dark. The choice is stored on the user account (`users.theme_preference`) and cached in `localStorage['markitdone.theme']`.
- An inline script in `index.html` reads the cache before Angular boots and sets `class="app-dark"` on `<html>`, so there's no flash of the wrong theme. "System" listens to `matchMedia('(prefers-color-scheme: dark)')`.

---

## 12. Accessibility baseline (skill priorities 1–2, all CRITICAL)

- Text contrast ≥4.5:1 (all token pairs above verified). UI component contrast ≥3:1.
- Focus: `outline: 2px solid var(--color-ring); outline-offset: 2px`, on every interactive element. Never removed.
- Touch targets ≥44×44 with ≥8px spacing.
- Icon-only buttons always have an `aria-label`.
- Never rely on colour alone (priority/recurring/overdue all carry text or an icon).
- Keyboard: the full app works without a mouse; drawers and dialogs trap focus and return it to the trigger on close; Esc closes.
- **Swipe carousels:** the skill warns against horizontal swipe on main content (`gesture-conflicts`) and asks for clear affordances (`swipe-clarity`). You chose the swipe carousel, so it **must** have: the visible day-chip strip, prev/next buttons, keyboard arrows, a "Day X of 7" indicator, and no swipe hijacking on desktop.
- Toasts don't steal focus.
- `prefers-reduced-motion` and `prefers-reduced-transparency` are respected.

## 13. Pre-delivery checklist (every UI subtask)
- [ ] Only PrimeIcons, no emoji
- [ ] Semantic tokens only, no raw hex in component styles
- [ ] Light **and** dark checked
- [ ] 375 / 768 / 1024 / 1440 px checked, no horizontal page scroll
- [ ] Keyboard-only walkthrough works; focus visible
- [ ] Touch targets ≥44px
- [ ] Loading (skeleton), empty, error and success states implemented
- [ ] Reduced motion and reduced transparency respected
- [ ] Nothing hidden behind the top bar, bottom bar or FAB
