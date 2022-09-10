package com.tosan.plugin.oras.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Saeed Hashemi
 * @since 25/08/2022
 */
public class Compression {
    private static final Log logger = new SystemStreamLog();

    public void compress(String workingDirectory, Path archive, String[] artifacts) {
        logger.info("Compressing Artifacts to " + archive);
        try (OutputStream fOut = Files.newOutputStream(archive);
             BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
             GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut);
             TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {

            for (String artifact : artifacts) {
                //find absolute path of artifacts
                Path artifactAbsPath = Paths.get(workingDirectory, artifact).toAbsolutePath();
                if (Files.isRegularFile(artifactAbsPath)) {
                    addFileToTarGzip(tOut, artifactAbsPath);
                } else if (Files.isDirectory(artifactAbsPath)) {
                    addFolderToTarGzip(tOut, artifactAbsPath);
                }
            }
            tOut.finish();
        } catch (IOException e) {
            throw new RuntimeException("Unable to archive.", e);
        }
    }

    // add a file to tgz
    private void addFileToTarGzip(TarArchiveOutputStream tOut, Path path) throws IOException {
        TarArchiveEntry tarEntry = new TarArchiveEntry(path.toFile(), path.getFileName().toString());
        tOut.putArchiveEntry(tarEntry);
        // copy file to TarArchiveOutputStream
        Files.copy(path, tOut);
        tOut.closeArchiveEntry();
    }

    //add a folder (with all containing files and sub-folders) to tgz
    private void addFolderToTarGzip(TarArchiveOutputStream tOut, Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                // only copy files, no symbolic links
                if (attributes.isSymbolicLink()) {
                    return FileVisitResult.CONTINUE;
                }

                // get filename
                Path targetFile = path.getParent().relativize(file);
                try {
                    TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), targetFile.toString());
                    tOut.putArchiveEntry(tarEntry);
                    Files.copy(file, tOut);
                    tOut.closeArchiveEntry();
                } catch (IOException e) {
                    logger.error("Unable to archive: " + file, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = path.getParent().relativize(dir);
                TarArchiveEntry tarEntry = new TarArchiveEntry(dir.toFile(), targetDir.toString());
                tOut.putArchiveEntry(tarEntry);
                tOut.closeArchiveEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.error("Unable to archive: " + file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
