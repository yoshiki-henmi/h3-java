/*
 * Copyright 2017-2018 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uber.h3core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Extracts the native H3 core library to the local filesystem and loads it.
 */
final class H3CoreLoader {
    H3CoreLoader() {
        // Prevent instantiation
    }

    // Supported H3 architectures
    private static final String ARCH_X64 = "x64";
    private static final String ARCH_X86 = "x86";

    private static volatile File libraryFile = null;

    /**
     * Read all bytes from <code>in</code> and write them to <code>out</code>.
     */
    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[4096];

        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
    }

    /**
     * Copy the resource at the given path to the file. File will be made readable,
     * writable, and executable.
     *
     * @param resourcePath Resource to copy
     * @param newH3LibFile File to write
     * @throws UnsatisfiedLinkError The resource path does not exist
     */
    static void copyResource(String resourcePath, File newH3LibFile) throws IOException {
        // Set the permissions
        newH3LibFile.setReadable(true);
        newH3LibFile.setWritable(true, true);
        newH3LibFile.setExecutable(true, true);

        // Shove the resource into the file and close it
        try (InputStream resource = H3CoreLoader.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                throw new UnsatisfiedLinkError(String.format("No native resource found at %s", resource));
            }

            try (FileOutputStream outFile = new FileOutputStream(newH3LibFile)) {
                copyStream(resource, outFile);
            }
        }
    }

    /**
     * For use when the H3 library should be unpacked from the JAR and loaded.
     *
     * @throws SecurityException Loading the library was not allowed by the
     *                           SecurityManager.
     * @throws UnsatisfiedLinkError The library could not be loaded
     * @throws IOException Failed to unpack the library
     */
    public synchronized static NativeMethods loadNatives() throws IOException {
        // This is synchronized because if multiple threads were writing and
        // loading the shared object at the same time, bad things could happen.

        if (libraryFile == null) {
            final OperatingSystem os = detectOs(System.getProperty("java.vendor"), System.getProperty("os.name"));
            final String arch = detectArch(System.getProperty("os.arch"));

            final String dirName = String.format("%s-%s", os.getDirName(), arch);
            final String libName = String.format("libh3-java%s", os.getSuffix());

            final File newLibraryFile = File.createTempFile("libh3-java", os.getSuffix());

            newLibraryFile.deleteOnExit();

            copyResource(String.format("/%s/%s", dirName, libName), newLibraryFile);

            libraryFile = newLibraryFile;
        }

        System.load(libraryFile.getCanonicalPath());

        return new NativeMethods();
    }

    /**
     * For use when the H3 library is installed system-wide and Java is able to locate it.
     *
     * @throws SecurityException Loading the library was not allowed by the
     *                           SecurityManager.
     * @throws UnsatisfiedLinkError The library could not be loaded
     */
    public static NativeMethods loadSystemNatives() {
        System.loadLibrary("h3-java");

        return new NativeMethods();
    }

    /**
     * Operating systems supported by H3-Java.
     */
    enum OperatingSystem {
        ANDROID(".so"),
        DARWIN(".dylib"),
        WINDOWS(".dll"),
        LINUX(".so");

        private final String suffix;

        OperatingSystem(String suffix) {
            this.suffix = suffix;
        }

        /**
         * Suffix for native libraries.
         */
        public String getSuffix() {
            return suffix;
        }

        /**
         * How this operating system's name is rendered when extracting the native library.
         */
        public String getDirName() {
            return name().toLowerCase();
        }
    }

    /**
     * Detect the current operating system.
     *
     * @param javaVendor Value of system property "java.vendor"
     * @param osName Value of system property "os.name"
     */
    static final OperatingSystem detectOs(String javaVendor, String osName) {
        // Detecting Android using the properties from:
        // https://developer.android.com/reference/java/lang/System.html
        if (javaVendor.toLowerCase().contains("android")) {
            return OperatingSystem.ANDROID;
        }

        String javaOs = osName.toLowerCase();
        if (javaOs.contains("mac")) {
            return OperatingSystem.DARWIN;
        } else if (javaOs.contains("win")) {
            return OperatingSystem.WINDOWS;
        } else {
            // Only other supported platform
            return OperatingSystem.LINUX;
        }
    }

    /**
     * Detect the system architecture.
     *
     * @param osArch Value of system property "os.arch"
     */
    static final String detectArch(String osArch) {
        if (osArch.equals("amd64") || osArch.equals("x86_64")) {
            return ARCH_X64;
        } else if (osArch.equals("i386") ||
                   osArch.equals("i486") ||
                   osArch.equals("i586") ||
                   osArch.equals("i686") ||
                   osArch.equals("i786") ||
                   osArch.equals("i886")) {
            return ARCH_X86;
        } else {
            return osArch;
        }
    }
}