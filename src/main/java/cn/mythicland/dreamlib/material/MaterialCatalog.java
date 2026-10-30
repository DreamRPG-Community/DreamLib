package cn.mythicland.dreamlib.material;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Shared localized catalog for legacy Bukkit materials and data values.
 *
 * <p>The bundled catalog is generated from the LocaleLib legacy material-key
 * mapping and the versioned Minecraft 1.12.2 Chinese language asset. Keeping
 * the generated data here lets every dependent plugin use the same names and
 * avoids hand-maintained material tables in individual plugins.</p>
 */
public final class MaterialCatalog {

    private static final String RESOURCE = "/materials/zh_cn.tsv";
    private final List<MaterialEntry> entries;
    private final Map<String, MaterialEntry> byKey;
    private final Map<String, MaterialEntry> byBaseName;

    private MaterialCatalog(List<MaterialEntry> entries) {
        this.entries = List.copyOf(entries);
        this.byKey = new HashMap<>();
        this.byBaseName = new HashMap<>();
        for (MaterialEntry entry : entries) {
            byKey.put(entry.key(), entry);
            byBaseName.putIfAbsent(entry.materialName(), entry);
        }
    }

    /**
     * Loads the versioned catalog bundled with Lib.
     *
     * @return the shared material catalog
     * @throws IllegalStateException if the catalog resource is missing or malformed
     */
    public static MaterialCatalog bundled() {
        InputStream input = MaterialCatalog.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing Lib material catalog: " + RESOURCE);
        try (input) {
            return read(input);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load Lib material catalog", exception);
        }
    }

    /**
     * Reads a catalog from a UTF-8 tab-separated stream.
     *
     * @param input the stream containing material, id, data, and display-name columns
     * @return a parsed catalog
     * @throws IOException if the stream cannot be read
     */
    public static MaterialCatalog read(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        List<MaterialEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\\t", 4);
                if (columns.length != 4) {
                    throw new IOException("Invalid material catalog row at line " + lineNumber);
                }
                try {
                    entries.add(new MaterialEntry(
                            columns[0],
                            Integer.parseInt(columns[1]),
                            Integer.parseInt(columns[2]),
                            columns[3]
                    ));
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid material catalog row at line " + lineNumber, exception);
                }
            }
        }
        if (entries.isEmpty()) throw new IOException("Material catalog is empty");
        return new MaterialCatalog(entries);
    }

    /**
     * Returns every material and data-value entry.
     *
     * @return an immutable catalog entry list
     */
    public List<MaterialEntry> entries() {
        return entries;
    }

    /**
     * Finds an exact material and data-value entry.
     *
     * @param materialName the Bukkit material enum name
     * @param data         the legacy data value
     * @return the matching entry when present
     */
    public Optional<MaterialEntry> find(String materialName, int data) {
        if (materialName == null) return Optional.empty();
        return Optional.ofNullable(byKey.get(materialName.trim().toUpperCase(Locale.ROOT) + "." + data));
    }

    /**
     * Returns the exact localized name, or the base-data name when the exact
     * data value has no dedicated translation.
     *
     * @param materialName the Bukkit material enum name
     * @param data         the legacy data value
     * @return the localized name or the normalized Bukkit name as a final fallback
     */
    public String displayName(String materialName, int data) {
        if (materialName == null || materialName.isBlank()) return "未知材质";
        String normalized = materialName.trim().toUpperCase(Locale.ROOT);
        MaterialEntry exact = byKey.get(normalized + "." + data);
        if (exact != null) return exact.displayName();
        MaterialEntry base = byBaseName.get(normalized);
        return base == null ? normalized : base.displayName();
    }

    /**
     * Searches material enum names and localized display names.
     *
     * @param query a case-insensitive search fragment, or blank for all entries
     * @return matching entries in catalog order
     */
    public List<MaterialEntry> search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return entries;
        return entries.stream()
                .filter(entry -> entry.materialName().toLowerCase(Locale.ROOT).contains(normalized)
                        || entry.displayName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }
}
