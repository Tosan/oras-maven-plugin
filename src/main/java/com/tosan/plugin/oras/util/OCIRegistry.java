package com.tosan.plugin.oras.util;

import lombok.Data;
import lombok.ToString;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * POJO for oci registry
 *
 * @author Saeed Hashemi
 * @since 25/08/2022
 */
@Data
public class OCIRegistry {

    /**
     * Name of registry. If no username/password is configured this name is interpreted as serverId and used to obtain
     * username/password from server list in <code>settings.xml</code>-
     */
    @Parameter(property = "oci.repo.name", required = true)
    private String name;

    @Parameter(property = "oci.repo.url", required = true)
    private String url;

    /**
     * Username for basic authentication. If present, credentials in server list will be ignored.
     */
    @Parameter(property = "oci.repo.username")
    @ToString.Exclude
    private String username;

    /**
     * Password for basic authentication. If present, credentials in server list will be ignored.
     */
    @Parameter(property = "oci.repo.password")
    @ToString.Exclude
    private String password;
}
