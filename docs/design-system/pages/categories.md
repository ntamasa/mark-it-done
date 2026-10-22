# Page override — Categories (`/categories`)

## Layout
- Header: "Categories" + a "New category" button (`pi pi-plus`).
- A list of categories (mobile: full-width rows; ≥768: cards in a 2-col grid; ≥1024: 3-col). Each row shows the colour chip with the name, the todo count ("12 todos"), and actions Edit (`pi pi-pencil`) and Delete (`pi pi-trash`).

## Create / edit category
`p-dialog` (glass), title "New category" / "Edit category". Fields:
- Name (required, 1–40 chars, unique per user, case-insensitive). The error "You already have a category with this name." appears under the field.
- Colour: a radio group of 10 swatches (MASTER §2.3), shown as 40px circles with a visible checkmark on the selected one; each has `aria-label` set to the colour name.
- A live preview of the chip.
- Buttons: Cancel / Save (`.btn-accent`).

## Delete category — reassignment dialog **[Decision]**
If the category has **no** todos, show a plain `p-confirmdialog`: "Delete “Work”?"

If it **has** todos, open a `p-dialog` (glass, max-width 640px; full screen on mobile):
- Title: "Delete “Work”"
- Intro: "12 items use this category. Choose a new category for each, or leave them without a category."
- A bulk row: "Set all to: [p-select of other categories + “No category”] [Apply]".
- A list (mobile stacked, ≥768 `p-table`) with **one row per one-off todo and one row per recurring series** (the series row shows the `Recurring` tag and "applies to all occurrences, including past"). Each row has its own `p-select` defaulting to **"No category"**.
- Footer: "Cancel" (text) / "Delete category" (danger).
- On confirm: one API call with the delete and every reassignment; toast "Category deleted · 5 moved, 7 left without category".
