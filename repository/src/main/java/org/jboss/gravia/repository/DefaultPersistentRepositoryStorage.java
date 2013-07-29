/*
 * #%L
 * JBossOSGi Repository
 * %%
 * Copyright (C) 2012 - 2013 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.gravia.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.jboss.gravia.repository.Repository.ConfigurationPropertyProvider;
import org.jboss.gravia.repository.spi.AbstractPersistentRepositoryStorage;
import org.jboss.gravia.repository.spi.RepositoryContentHelper;
import org.jboss.gravia.resource.ResourceBuilder;

/**
 * A simple {@link RepositoryStorage} that uses
 * the local file system.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jan-2012
 */
public class DefaultPersistentRepositoryStorage extends AbstractPersistentRepositoryStorage {

    public static final String REPOSITORY_XML_NAME = "repository.xml";

    private final File storageDir;
    private final File repoFile;

    public DefaultPersistentRepositoryStorage(PersistentRepository repository, ConfigurationPropertyProvider propertyProvider) {
        super(repository, propertyProvider);
        if (propertyProvider == null)
            throw new IllegalArgumentException("Null propertyProvider");

        String filename = propertyProvider.getProperty(Repository.PROPERTY_REPOSITORY_STORAGE_FILE, REPOSITORY_XML_NAME);
        String dirname = propertyProvider.getProperty(Repository.PROPERTY_REPOSITORY_STORAGE_DIR, null);
        if (dirname == null)
            throw new IllegalArgumentException("Cannot obtain property: " + Repository.PROPERTY_REPOSITORY_STORAGE_DIR);

        storageDir = new File(dirname).getAbsoluteFile();
        repoFile = new File(dirname + File.separator + filename).getAbsoluteFile();

        initRepositoryStorage();
    }

    @Override
    public RepositoryReader getPersistentRepositoryReader() throws RepositoryStorageException {
        try {
            return repoFile.exists() ? new DefaultRepositoryXMLReader(new FileInputStream(repoFile)) : null;
        } catch (IOException ex) {
            throw new RepositoryStorageException(ex);
        }
    }

    @Override
    public RepositoryWriter getPersistentRepositoryWriter() throws RepositoryStorageException {
        try {
            repoFile.getParentFile().mkdirs();
            return new DefaultRepositoryXMLWriter(new FileOutputStream(repoFile));
        } catch (IOException ex) {
            throw new RepositoryStorageException(ex);
        }
    }

    @Override
    public ResourceBuilder createResourceBuilder() {
        return new DefaultRepositoryResourceBuilder();
    }

    @Override
    protected void addResourceContent(InputStream input, Map<String, Object> atts) throws RepositoryStorageException {
        try {
            // Copy the input stream to temporary storage
            File tempFile = new File(storageDir.getAbsolutePath() + File.separator + "temp-content");
            Long size = copyResourceContent(input, tempFile);
            atts.put(ContentNamespace.CAPABILITY_SIZE_ATTRIBUTE, size);
            // Calculate the SHA-256
            String sha256;
            String algorithm = RepositoryContentHelper.DEFAULT_DIGEST_ALGORITHM;
            try {
                sha256 = RepositoryContentHelper.getDigest(new FileInputStream(tempFile), algorithm);
                atts.put(ContentNamespace.CONTENT_NAMESPACE, sha256);
            } catch (NoSuchAlgorithmException ex) {
                throw new RepositoryStorageException("No such digest algorithm: " + algorithm, ex);
            }
            // Move the content to storage location
            String contentPath = sha256.substring(0, 2) + File.separator + sha256.substring(2) + File.separator + "content";
            File targetFile = new File(storageDir.getAbsolutePath() + File.separator + contentPath);
            targetFile.getParentFile().mkdirs();
            tempFile.renameTo(targetFile);
            URL url = targetFile.toURI().toURL();
            atts.put(ContentNamespace.CAPABILITY_URL_ATTRIBUTE, url.toExternalForm());
        } catch (IOException ex) {
            throw new RepositoryStorageException(ex);
        }
    }

    @Override
    protected URL getBaseURL() {
        try {
            return storageDir.toURI().toURL();
        } catch (MalformedURLException e) {
            // ignore
            return null;
        }
    }

    private long copyResourceContent(InputStream input, File targetFile) throws IOException {
        int len = 0;
        long total = 0;
        byte[] buf = new byte[4096];
        targetFile.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(targetFile);
        while ((len = input.read(buf)) >= 0) {
            out.write(buf, 0, len);
            total += len;
        }
        input.close();
        out.close();
        return total;
    }
}
