# Mapas offline desde OpenStreetMap (sin Huawei)

Vía limpia, verificada por análisis de código, para poner mapas en el reloj sin Huawei Health.
Es el plan C de la spec para mapas. **Cero Huawei: datos de OpenStreetMap, herramientas libres.**

## Por qué funciona

- Gadgetbridge ya **sube mapas offline** al reloj: `HuaweiP2PMapkitService` transfiere ficheros
  `<mapId>.bin` y `<mapId>_contour.bin`, reconocidos por `HuaweiFwHelper.parseAsOfflineMap()` e
  instalados por `HuaweiInstallHandler`. Se activan con las capacidades offlineMap (bit 201) y
  contour (bit 202).
- Soporte confirmado en nuestros relojes: **GT Runner 2** (mapa + contorno), **GT7** (solo mapa).
- `offvmp` (Me7c7, licencia MIT, `codeberg.org/Me7c7/HDataResearch`, carpeta `offvmp`) convierte
  extractos de OpenStreetMap a ese mismo formato `.bin`.

## Cadena de construcción

```
OSM .pbf  --osmium-->  GeoJSON  --build_country.py-->  encode.py  -->  <mapId>.bin
```

`build_country.py` también acepta un GeoJSON de Overpass directamente (sin osmium) si se prefiere.

### Dependencias

- `osmium-tool` (`sudo dnf install osmium-tool`) — o usar Overpass para el GeoJSON.
- Python 3. Opcional: `pyhgtmap` (contornos), `gdal-bin` (ruta ogr2ogr).

### Ejemplo (Andorra, pequeño)

```
git clone https://codeberg.org/Me7c7/HDataResearch   # MIT
cd HDataResearch/offvmp/converter
scripts/demo_andorra.sh            # produce build_andorra/446513991765216720.bin
```

El `<mapId>` debe ser el id numérico que el reloj espera para esa región (tabla en
`offvmp/docs/filenames.md`); con otro nombre el reloj puede rechazar el fichero.

### Instalar en el reloj

En UltimateGadget: instalar el `.bin` como cualquier fichero (igual que una esfera). La app lo
reconoce como mapa offline por el nombre y lo sube por el canal mapkit.

> Nota de seguridad: ejecutar `offvmp` es código de terceros descargado; revísalo antes de correrlo
> y hazlo fuera de cualquier sandbox restringido. Un `.bin` mal formado, en el peor caso, podría
> hacer que la app de mapas del reloj se comporte mal; empieza por una región pequeña.

## Relación con el golf (hipótesis a verificar en Fase 2)

En el GT Runner 2 (reloj vectorial) el mapa de campo de golf se transfiere **tal cual** (sin cifrar a
nivel de golf). Si ese `.bin` usa el mismo contenedor vectorial que los mapas offline de `offvmp`,
se podrían generar campos desde OSM (`leisure=golf_course`, `golf=*`) sin Huawei. Falta una muestra
de un mapa de campo real para confirmarlo.
