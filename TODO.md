# Project TODOs

## Startup configuration validation

- **SoundDefinitionRegistry / soundscape integrity** — At startup, optionally validate that every `signal-map` input resolves to a registered sound definition (and/or that declared soundscape paths exist on disk). Implement as a `StartupConfigContributor` in `manager.config.startup` so it runs with the rest of aggregated config validation and logs consistently via `StartupConfigValidation`.
