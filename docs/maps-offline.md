# Offline maps from OpenStreetMap (without Huawei)

A clean path, verified by code analysis, to put maps on the watch without Huawei Health.
It is the spec's plan C for maps. **Zero Huawei: OpenStreetMap data, free tools.**

## Why it works

- Gadgetbridge already **uploads offline maps** to the watch: `HuaweiP2PMapkitService` transfers
  `<mapId>.bin` and `<mapId>_contour.bin` files, recognized by `HuaweiFwHelper.parseAsOfflineMap()` and
  installed by `HuaweiInstallHandler`. They are enabled by the offlineMap (bit 201) and
  contour (bit 202) capabilities.
- Support confirmed on our watches: **GT Runner 2** (map + contour), **GT7** (map only).
- `offvmp` (Me7c7, MIT license, `codeberg.org/Me7c7/HDataResearch`, `offvmp` folder) converts
  OpenStreetMap extracts to that same `.bin` format.

## Build chain

```
OSM .pbf  --osmium-->  GeoJSON  --build_country.py-->  encode.py  -->  <mapId>.bin
```

`build_country.py` also accepts an Overpass GeoJSON directly (without osmium) if preferred.

### Dependencies

- `osmium-tool` (`sudo dnf install osmium-tool`) — or use Overpass for the GeoJSON.
- Python 3. Optional: `pyhgtmap` (contours), `gdal-bin` (the ogr2ogr path).

### Example (Andorra, small)

```
git clone https://codeberg.org/Me7c7/HDataResearch   # MIT
cd HDataResearch/offvmp/converter
scripts/demo_andorra.sh            # produces build_andorra/446513991765216720.bin
```

The `<mapId>` must be the numeric id the watch expects for that region (table in
`offvmp/docs/filenames.md`); with a different name the watch may reject the file.

### Install on the watch

In UltimateGadget: install the `.bin` like any file (the same as a watchface). The app
recognizes it as an offline map by its name and uploads it over the mapkit channel.

> Safety note: running `offvmp` is downloaded third-party code; review it before running and
> run it outside any restricted sandbox. A malformed `.bin` could, at worst, make the watch's
> map app misbehave; start with a small region.

## Relationship to golf (hypothesis to verify in Phase 2)

On the GT Runner 2 (vector watch) the golf course map is transferred **as-is** (not encrypted at the
golf level). If that `.bin` uses the same vector container as `offvmp`'s offline maps,
courses could be generated from OSM (`leisure=golf_course`, `golf=*`) without Huawei. A sample
of a real course map is still needed to confirm it.
