# Worn Ground

NeoForge 1.21.1 mod: players and animals wear desire paths into the ground (grass → dirt → coarse dirt → dirt path). Data-driven via a block data map and tags. With Sable installed, vehicles wear ruts; blocks on sub-levels themselves are skipped by default.

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

# Measurements (headless server, real regrowth / cows / Sable physics) -> build/lab/*.json
nix develop -c ./gradlew runLab                       # foot traffic + cow pens (~40 min)
nix develop -c ./gradlew runLab -Plab=pens
nix develop -c ./gradlew runLab -Plab=vehicle -PpackMods=<instance>/mods -PpackModNames=sable-neoforge,create-1.21.1,create-aeronautics-bundled
python3 scripts/lab_report.py build/lab/all.json build/lab/vehicle.json [--summary docs/measurements.json]

# Gallery (client under Xvfb: no window, no sound) -> build/showcase/screenshots -> docs/gallery
nix develop -c python3 scripts/headless.py ./gradlew runShowcase
nix develop -c python3 scripts/headless.py ./gradlew runShowcase -PshowcaseMode=vehicle -PpackMods=... -PpackModNames=...
cp build/showcase/screenshots/*.png build/gallery-src/ && nix develop -c python3 scripts/gallery.py
nix develop -c ./gradlew modrinth -PmodrinthDryRun
```

## Layout

- `WearHandler` — per-step wear logic (entity tick → supporting block → roll rules → apply).
- `WearRule` — data map value codec.
- `compat/SubLevelWear` — Sable sub-levels (vehicles, hulls) wearing the ground; only loaded with Sable.
- `src/testmod` — gametests, `Lab` (measurements) and `Showcase` (gallery scenes).
- `src/main/resources/data/worn_ground` — default wear rules and tags.
