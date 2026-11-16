# Changelog

## 0.3.0
- Landing from a jump or a drop is a heavier step: the block you land on wears four times as fast (`landingMultiplier`).
- Sprint-jumping along a route now wears it in about as fast as sprinting, instead of barely at all.
- Jumps are picked up reliably even when you only touch the ground for a moment between them, also in creative mode.

## 0.2.0
- Vehicles leave ruts: with Sable installed, Create Aeronautics / Offroad wheels and anything else a physics structure drags or lands on the ground now wear it (`contraptionsWearGround`, `contraptionMultiplier`; wheels come from the `worn_ground:wheels` block tag).
- Mobs now wear at a quarter of the player rate instead of half, so a pen takes about a week to wear in rather than three days.
- Measured wear rates and a gallery are on the mod page.

## 0.1.0
- Initial release: players and animals wear desire paths into the ground they walk on (grass → dirt → coarse dirt → dirt path).
- Tall grass and ferns get trampled first; flowers, crops, rails and anything else standing on the ground keep their support.
- Sneaking or wearing leather boots leaves no trace; sprinting wears faster.
- Wear rules, path-making mobs, trampleable plants and soft boots are all datapack-driven.
- Blocks of Sable / Create Aeronautics contraptions are left alone by default.
