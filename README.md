# Worn Ground

NeoForge 1.21.1 mod: players and animals wear desire paths into the ground (grass → dirt → coarse dirt → dirt path). Data-driven via a block data map and tags; skips Sable / Create Aeronautics sub-level blocks by default.

Player-facing description: [docs/MODRINTH.md](docs/MODRINTH.md).

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.x
- No dependencies. Only needed on the server.

## Development

Toolchain comes from the nix flake (`direnv allow` or prefix commands with `nix develop -c`).

```sh
nix develop -c ./gradlew build              # compile + jar
nix develop -c ./gradlew runGameTestServer  # headless gametests (src/testmod)
nix develop -c ./gradlew runGameTestServer -PpackMods=<instance>/mods -PpackModNames=sable-neoforge
nix develop -c python3 scripts/gen_icon.py  # regenerate icon
nix develop -c ./gradlew modrinth -PmodrinthDryRun
```

## Layout

- `WearHandler` — per-step wear logic (entity tick → supporting block → roll rules → apply).
- `WearRule` — data map value codec.
- `src/main/resources/data/worn_ground` — default wear rules and tags.
