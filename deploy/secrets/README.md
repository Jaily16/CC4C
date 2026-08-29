# Local secrets

Run `deploy/scripts/prepare-local.ps1` to create the ignored `local/`
directory. Files under `examples/` are deliberately invalid placeholders and
must never be mounted as runtime credentials.

External SMTP is optional. Copy and fill `smtp_username.example` and
`smtp_password.example` into the ignored `local/` directory only when using
`compose.smtp.yml`.
