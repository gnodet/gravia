/*
 * #%L
 * Gravia :: Runtime :: API
 * %%
 * Copyright (C) 2013 - 2014 JBoss by Red Hat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.jboss.gravia.runtime.spi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;

import org.jboss.gravia.runtime.Module;
import org.jboss.gravia.utils.IllegalArgumentAssertion;

/**
 * A provider for module entries that delegates to
 * the given module class loader
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
public final class ClassLoaderEntriesProvider implements ModuleEntriesProvider {

    private final ClassLoader classLoader;
    private final Map<URL, String> entries;

    public ClassLoaderEntriesProvider(Module module) {
        IllegalArgumentAssertion.assertNotNull(module, "module");
        classLoader = module.adapt(ClassLoader.class);
        entries = new LinkedHashMap<>();

        try {
            Map<String, URL> urls = new LinkedHashMap<>();
            for (URL url : findUrls(classLoader)) {
                urls.put(url.toExternalForm(), url);
            }
//            for (URL url : findUrls(classLoader.getParent())) {
//                urls.remove(url.toExternalForm());
//            }
            for (URL location : urls.values()) {
                if (location.getProtocol().equals("jar")) {
                    entries.putAll(jar(location));
                } else if (location.getProtocol().equals("file")) {
                    try {
                        // See if it's actually a jar
                        URL jarUrl = new URL("jar", "", location.toExternalForm() + "!/");
                        JarURLConnection juc = (JarURLConnection) jarUrl.openConnection();
                        juc.getJarFile();
                        entries.putAll(jar(jarUrl));
                    } catch (IOException e) {
                        entries.putAll(file(location));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error discovering module entries", e);
        }
    }

    @Override
    public URL getEntry(String path) {
        for (Map.Entry<URL, String> entry : entries.entrySet()) {
            if (entry.getValue().equals(path)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public List<String> getEntryPaths(String path) {
        List<String> result = new ArrayList<>();
        for (String entry : entries.values()) {
            if (entry.startsWith(path) && entry.indexOf('/', path.length()) < 0) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public List<URL> findEntries(String path, String filePattern, boolean recurse) {
        List<URL> result = new ArrayList<>();
        if (filePattern == null) {
            filePattern = "*";
        }
        if (!path.endsWith("/")) {
            path += "/";
        }
        Pattern pattern;
        if (recurse) {
            pattern = Pattern.compile(Pattern.quote(path) + "([^/]*/)*" + convertWildcardToRegEx(filePattern));
        } else {
            pattern = Pattern.compile(Pattern.quote(path) + convertWildcardToRegEx(filePattern));
        }
        for (Map.Entry<URL, String> entry : entries.entrySet()) {
            if (pattern.matcher(entry.getValue()).matches()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private String convertWildcardToRegEx(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        int i = 0, j = 0;
        while ((i = line.indexOf('*', i)) >= 0) {
            sb.append(Pattern.quote(line.substring(j, i)));
            sb.append("[^/]*");
            j = i + 1;
        }
        sb.append(Pattern.quote(line.substring(j, line.length())));
        return sb.toString();
    }

    private Map<URL, String> file(URL location) throws MalformedURLException, UnsupportedEncodingException {
        Map<URL, String> entryNames = new HashMap<>();
        File dir = new File(URLDecoder.decode(location.getPath(), "UTF-8"));
        if (dir.isDirectory()) {
            scanDir(dir, entryNames, "");
        }
        return entryNames;
    }

    private void scanDir(File dir, Map<URL, String> entryNames, String dirName) throws MalformedURLException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String entry = dirName + file.getName() + "/";
                    entryNames.put(file.toURI().toURL(), entry);
                    scanDir(file, entryNames, entry);
                } else {
                    String entry = dirName + file.getName();
                    entryNames.put(file.toURI().toURL(), entry);
                }
            }
        }
    }

    private Map<URL, String> jar(URL location) throws IOException {
        String jarPath = location.getFile();
        if (jarPath.contains("!")){
            jarPath = jarPath.substring(0, jarPath.indexOf("!"));
        }
        URL url = new URL(jarPath);
        try (InputStream in = url.openStream()) {
            JarInputStream jarStream = new JarInputStream(in);
            return jar(url, jarStream);
        }
    }

    private Map<URL, String> jar(URL url, JarInputStream jarStream) throws IOException {
        Map<URL, String> entryNames = new HashMap<>();

        JarEntry entry;
        while ((entry = jarStream.getNextJarEntry()) != null) {
            entryNames.put(new URL("jar:" + url + "!/" + entry.getName()), entry.getName());
        }

        return entryNames;
    }


    private static final ClassLoader SYSTEM = ClassLoader.getSystemClassLoader();

    private static Set<URL> findUrls(final ClassLoader classLoader) throws IOException {
        if (classLoader == null || (SYSTEM.getParent() != null && classLoader == SYSTEM.getParent())) {
            return Collections.emptySet();
        }

        final Set<URL> urls =  new HashSet<>();
        for (final URL url : findUrlFromResources(classLoader)) {
            urls.add(url);
        }

        return urls;
    }

    private static Set<URL> findUrlFromResources(final ClassLoader classLoader) throws IOException {
        final Set<URL> set = new HashSet<>();
        for (final URL url : Collections.list(classLoader.getResources("META-INF"))) {
            final String externalForm = url.toExternalForm();
            set.add(new URL(externalForm.substring(0, externalForm.lastIndexOf("META-INF"))));
        }
        set.addAll(Collections.list(classLoader.getResources("")));
        return set;
    }


    private static File toFile(final URL url) throws UnsupportedEncodingException {
        if ("jar".equals(url.getProtocol())) {
            try {
                final String spec = url.getFile();
                final int separator = spec.indexOf('!');
                if (separator == -1) {
                    return null;
                }
                return toFile(new URL(spec.substring(0, separator + 1)));
            } catch (final MalformedURLException e) {
                return null;
            }
        } else if ("file".equals(url.getProtocol())) {
            String path = URLDecoder.decode(url.getFile(), "UTF-8");
            if (path.endsWith("!")) {
                path = path.substring(0, path.length() - 1);
            }
            return new File(path);
        }
        return null;
    }

}
