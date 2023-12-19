package com.tosan.plugin.oras;

import com.tosan.plugin.oras.util.OCIRegistry;
import lombok.Setter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for push artifacts to oci registry
 *
 * @author Saeed Hashemi
 * @since 25/08/2022
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
@Setter
public class PushMojo extends AbstractOrasMojo {

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

        String artifacts = String.join(" ", getArtifacts());
        upload(registry, artifacts);
    }

    private void upload(OCIRegistry registry, String artifacts) throws MojoExecutionException {
        getLog().debug("Uploading to " + registry.getUrl());
        oras("push", String.format(PUSH_TEMPLATE, registry.getUrl(), getUploadName(), getUploadVersion(), artifacts), "Upload failed");
    }
}