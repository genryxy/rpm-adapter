/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.rpm.meta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * Xml maid.
 * @since 0.3
 */
public interface XmlMaid {

    /**
     * Cleans xml by ids (checksums) and returns actual package count.
     * @param ids Checksums
     * @return Packages count
     * @throws IOException When something wrong
     */
    long clean(List<String> ids) throws IOException;

    /**
     * Cleans xml by pkgid attribute in package tag.
     * @since 0.3
     */
    final class ByPkgidAttr implements XmlMaid {

        /**
         * Package tag name.
         */
        private static final String TAG = "package";

        /**
         * File to clear.
         */
        private final Path file;

        /**
         * Ctor.
         * @param file What to clear
         */
        public ByPkgidAttr(final Path file) {
            this.file = file;
        }

        @Override
        public long clean(final List<String> ids) throws IOException {
            final Path tmp = this.file.getParent().resolve(
                String.format("%s.part", this.file.getFileName().toString())
            );
            final long res;
            try (InputStream in = Files.newInputStream(this.file);
                OutputStream out = Files.newOutputStream(tmp)) {
                final XMLEventReader reader =
                    XMLInputFactory.newInstance().createXMLEventReader(in);
                final XMLEventWriter writer =
                    XMLOutputFactory.newInstance().createXMLEventWriter(out);
                try {
                    res = ByPkgidAttr.process(ids, reader, writer);
                } finally {
                    writer.close();
                    reader.close();
                }
            } catch (final XMLStreamException ex) {
                throw new XmlException(ex);
            }
            Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
            return res;
        }

        /**
         * Process lines.
         * @param ids Not valid ids list
         * @param reader Reader
         * @param writer Writes
         * @return Valid packages count
         * @throws XMLStreamException When error occurs
         */
        private static long process(final List<String> ids, final XMLEventReader reader,
            final XMLEventWriter writer) throws XMLStreamException {
            boolean valid = true;
            long cnt = 0;
            XMLEvent event;
            while (reader.hasNext()) {
                event = reader.nextEvent();
                if (event.isStartElement()
                    && event.asStartElement().getName().getLocalPart().equals(ByPkgidAttr.TAG)
                ) {
                    if (ids.contains(
                        event.asStartElement().getAttributeByName(new QName("pkgid")).getValue()
                    )) {
                        valid = false;
                    } else {
                        valid = true;
                        cnt = cnt + 1;
                    }
                }
                if (valid) {
                    writer.add(event);
                }
                if (event.isEndElement()
                    && event.asEndElement().getName().getLocalPart().equals(ByPkgidAttr.TAG)) {
                    valid = true;
                }
            }
            return cnt;
        }

    }
}
