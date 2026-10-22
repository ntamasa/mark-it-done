# Page override — Auth (Login, Register, Forgot password, Reset password)

Overrides MASTER for routes `/login`, `/register`, `/forgot-password`, `/reset-password?token=…`.

## Layout
- No app shell (no top bar, tab bar, rail or FAB). The mesh backdrop stays visible.
- A single **glass card** is centred both ways: `max-width: 420px`, `width: calc(100% - 32px)`, padding 24 (mobile) / 32 (≥768), radius 16, `--shadow-glass`. **Override:** glass is allowed on this content card because it's the only element on screen and holds short form text on a solid input background.
- Inside the card: the wordmark "markitDONE" (22/700, `--color-primary-strong`), page title (18/600), then the form, primary button (full width, `.btn-accent`), and secondary links (14px, `--color-primary-strong`, underlined on hover/focus).
- The theme switch is an icon button in the top-right corner of the viewport (`pi pi-sun` / `pi pi-moon` / `pi pi-desktop`), `aria-label="Theme"`.

## Screens
| Screen | Fields | Primary CTA | Links |
|---|---|---|---|
| Login | Email, Password (`p-password [feedback]=false [toggleMask]`), "Remember me" checkbox | "Log in" | "Forgot password?" · "Create account" |
| Register | Display name, Email, Password (with live rules checklist), Confirm password | "Create account" | "Already have an account? Log in" |
| Forgot password | Email | "Send reset link" | "Back to log in" |
| Forgot — sent | Static text: "If an account exists for {email}, we've sent a reset link. It expires in 30 minutes." | — | "Back to log in" |
| Reset password | New password (live checklist), Confirm | "Set new password" | — |
| Reset — invalid/expired token | Text + `pi pi-exclamation-triangle` | "Request a new link" | — |

## States
- Submit: the button shows `[loading]=true` and the form is disabled. Errors from the server appear in a `p-message severity="error"` at the top of the card (focus moves to it), e.g. "Email or password is incorrect."
- Field errors appear under each field on blur.
- After registering, go straight to the app (auto-login) and show the toast "Welcome, {name}!".
- After a password reset, go to `/login` with the toast "Password updated. Please log in."
