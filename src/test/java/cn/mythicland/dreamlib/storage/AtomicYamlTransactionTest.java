package cn.mythicland.dreamlib.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtomicYamlTransactionTest {

    @Test
    void restoresEveryTouchedFileWhenTransactionIsNotCommitted(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("items.yml");
        Path second = directory.resolve("tags.yml");
        AtomicFileStore.writeUtf8(first, "before-items");
        AtomicFileStore.writeUtf8(second, "before-tags");

        try (AtomicYamlTransaction transaction = new AtomicYamlTransaction()) {
            transaction.writeText(first, "after-items");
            transaction.writeText(second, "after-tags");
        }

        assertEquals("before-items", Files.readString(first));
        assertEquals("before-tags", Files.readString(second));
    }

    @Test
    void keepsEveryTouchedFileAfterCommit(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("items.yml");
        Path second = directory.resolve("tags.yml");

        try (AtomicYamlTransaction transaction = new AtomicYamlTransaction()) {
            transaction.writeText(first, "committed-items");
            transaction.writeText(second, "committed-tags");
            transaction.commit();
        }

        assertEquals("committed-items", Files.readString(first));
        assertEquals("committed-tags", Files.readString(second));
    }
}
