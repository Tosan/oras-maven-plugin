package com.tosan.plugin.oras;

import com.tosan.plugin.oras.util.Compression;
import com.tosan.plugin.oras.util.OCIRegistry;
import lombok.Setter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Mojo for packaging files as tgz and push to oci registry
 *
 * @author Saeed Hashemi
 * @since 25/08/2022
 */
@Mojo(name = "package-push", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
@Setter
public class PackagePushMojo extends AbstractOrasMojo {
    private static final Compression compression = new Compression();
    @Parameter(property = "oras.push.skip", defaultValue = "false")
    private boolean skipPush;

    @Override
    public void execute() throws MojoExecutionException {

        if (skip || skipPush) {
            getLog().info("Skip push");
            return;
        }

        try {
            checkArtifacts(getArtifacts());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        OCIRegistry registry = getUploadRepo();
        authenticate(registry);

        try {
            Files.createDirectories(Paths.get(getOutputDirectory()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Path archive = Paths.get(getOutputDirectory(), getArchiveName()).toAbsolutePath();
        compression.compress(getWorkingDirectory(), archive, getArtifacts());
        uploadArchive(registry, getOutputDirectory(), getArchiveName());
    }

    private void uploadArchive(OCIRegistry registry, String archiveDirectory, String archiveFile) throws MojoExecutionException {
        getLog().debug("Uploading to " + registry.getUrl());
        setWorkingDirectory(archiveDirectory);
        oras(String.format(PUSH_TEMPLATE, registry.getUrl(), getUploadName(), getUploadVersion(), archiveFile), "Upload failed");
    }
}
