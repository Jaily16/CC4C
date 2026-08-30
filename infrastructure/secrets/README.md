# Local secrets

Run `scripts/deployment/prepare-local.ps1` to create the ignored `deploy/secrets/local/`
directory. Files under `examples/` are deliberately invalid placeholders and
must never be mounted as runtime credentials.

External SMTP is optional. Copy and fill `smtp_username.example` and
`smtp_password.example` into the ignored `local/` directory only when using
`compose.smtp.yml`.

The default Compose project is `cc4c`; the secret mount remains
`deploy/secrets/local/` for compatibility. Host mode reads only the explicitly
provided ignored environment files and does not read or copy this directory.
See `docs/operations/host-runbook.md` and
`docs/operations/compose-identity-migration.md` for the deployment boundaries.
