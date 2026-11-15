# Worn Ground

Walk the same way often enough and the ground remembers. Players and animals slowly wear **desire paths** into the world, using only vanilla blocks:

**grass → dirt → coarse dirt → dirt path**

Inspired by [TFC Desire Paths](https://modrinth.com/mod/tfc-desire-paths), built for non-TFC NeoForge packs — in particular Create-style engineering packs.

## How it works

- Every time a player or animal steps onto a new block, there is a small chance the block wears one stage.
- Grass grows back over plain dirt by itself, so a route only turns into a lasting path if it keeps being used.
- **Sprinting** wears the ground twice as fast. **Sneaking** leaves no trace, and neither do **leather boots**.
- Tall grass and ferns are trampled before the ground below them wears.
- Livestock, horses, villagers, golems and other heavy mobs make paths too (at half the rate), so pens and village routes get worn in over time. Respects the `mobGriefing` game rule.
- Podzol and mycelium wear to dirt as well.

## Plays nice with the rest of your pack

- **Never removes support**: ground under rails, Create tracks, machines, crops, flowers or anything else is only swapped for another full, plant-friendly block — a path block only ever forms under open air.
- **[Sable](https://modrinth.com/mod/sable) / [Create Aeronautics](https://modrinth.com/mod/create-aeronautics)**: decks and gardens of airships and other physics contraptions are left untouched by default (`wearOnContraptions` in the config).
- **[Dynamic Trees](https://modrinth.com/mod/dynamictrees)**: root soil blocks are never touched.
- **Farmland** keeps its vanilla trampling behaviour.
- **[Block Runner](https://modrinth.com/mod/block-runner)**: worn-in paths are real dirt path blocks, so they get its speed boost.
- Server-side: only the server (or the single-player world) needs the mod.

## Configuration

`config/worn_ground-common.toml`: toggle players / mobs, global chance multiplier, sprint and mob multipliers, sneaking, trampling, `mobGriefing`, contraptions.

## Datapacks

Everything is data-driven.

**Wear rules** are a block [data map](https://docs.neoforged.net/docs/1.21.1/resources/server/datamaps/) at `data/worn_ground/data_maps/block/wear.json`:

```json
{
  "values": {
    "minecraft:grass_block": { "result": "minecraft:dirt", "chance": 0.06 },
    "minecraft:dirt": [
      { "result": "minecraft:mud", "chance": 0.05, "weather": "rain" },
      { "result": "minecraft:coarse_dirt", "chance": 0.03 }
    ],
    "minecraft:coarse_dirt": { "result": "minecraft:dirt_path", "chance": 0.02 }
  }
}
```

`chance` is per step onto the block (0–1). `weather` is `any` (default), `rain` or `clear`. A block may have one rule or a list; the first rule that succeeds wins. Use `"replace": true` or `"remove": [...]` to drop the defaults.

**Tags**

| Tag | Purpose |
| --- | --- |
| `worn_ground:path_makers` (entity type) | Mobs that wear the ground. Players always do. |
| `worn_ground:trampleable` (block) | Plants that are flattened before the ground wears. |
| `worn_ground:soft_soles` (item) | Boots that prevent wear. |
