package com.ai.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssServicePathTraversalTest {

    @TempDir
    Path temporaryDirectory;

    private Path storageRoot;
    private Path outsideRoot;
    private OssService ossService;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createDirectories(temporaryDirectory.resolve("uploads"));
        outsideRoot = Files.createDirectories(temporaryDirectory.resolve("outside"));
        ossService = new OssService();
        ReflectionTestUtils.setField(ossService, "localStoragePath", storageRoot.toString());
        ossService.init();
    }

    @Test
    void validLocalFilesRemainReadableAndWritable() {
        byte[] content = "inside uploads".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String reference = ossService.uploadBytes(content, "note.txt", "documents/user-1/");

        assertArrayEquals(content, ossService.downloadFile(reference));
    }

    @Test
    void multipartUploadCanBeStoredAndReadWithoutOverwritingExistingPaths() {
        byte[] content = "multipart local upload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile("files", "note.txt", "text/plain", content);

        String reference = ossService.uploadFile(upload, "documents/user-1/");

        assertArrayEquals(content, ossService.downloadFile(reference));
    }

    @Test
    void rejectsTraversalAbsoluteAndWindowsStylePaths() throws IOException {
        Files.writeString(outsideRoot.resolve("sentinel.txt"), "outside");

        assertRejected("../outside/sentinel.txt");
        assertRejected("documents/../../outside/sentinel.txt");
        assertRejected(outsideRoot.resolve("sentinel.txt").toString());
        assertRejected("C:\\outside\\sentinel.txt");
        assertRejected("..\\outside\\sentinel.txt");
    }

    @Test
    void rejectsEncodedTraversalResolvedFromAccessibleUploadUrl() throws IOException {
        Files.writeString(outsideRoot.resolve("sentinel.txt"), "outside");
        String storageReference = ossService.toStorageReference(
                "http://localhost/api/uploads/%2e%2e/outside/sentinel.txt");

        assertEquals("local://../outside/sentinel.txt", storageReference);
        assertThrows(IllegalArgumentException.class, () -> ossService.downloadFile(storageReference));
    }

    @Test
    void rejectsSymlinksThatEscapeTheStorageRoot() throws IOException {
        Files.writeString(outsideRoot.resolve("sentinel.txt"), "outside");
        Files.createSymbolicLink(storageRoot.resolve("escape"), outsideRoot);

        assertRejected("escape/sentinel.txt");
        assertThrows(IllegalArgumentException.class,
                () -> ossService.uploadBytes("outside".getBytes(), "payload.txt", "escape/"));
    }

    private void assertRejected(String objectName) {
        assertThrows(IllegalArgumentException.class,
                () -> ossService.downloadFile("local://" + objectName), objectName);
    }
}
