# PSHB

This is the README file for standalone `pshb` model

1. The first Proof-of-Concept (POC) model is deployed at 2024-05-07 on Google Drive
2. 2026-02-19: (1) read vegAttribute table in the start() to improve performance (2) change the vegAttributeLoader format, using CSV loader (3) add the CSV library into the POM. This version's background image is from Jeff's png file, not the temperature map.
3. 2026-03-26: modify getVegMapPrHost() to make sure the patchID is not zero or null
4. 2026-04-08: debug modification - some agents make too many movements in their life because they never move into the next stage, which is the colonization stage. Fixed!