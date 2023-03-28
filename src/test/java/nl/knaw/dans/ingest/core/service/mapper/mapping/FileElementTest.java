/*
 * Copyright (C) 2022 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.ingest.core.service.mapper.mapping;

import gov.loc.repository.bagit.reader.BagReader;
import nl.knaw.dans.ingest.core.domain.Deposit;
import nl.knaw.dans.ingest.core.service.XPathEvaluator;
import nl.knaw.dans.ingest.core.service.XmlReaderImpl;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static nl.knaw.dans.ingest.core.service.mapper.mapping.FileElement.toFileMeta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileElementTest extends BaseTest {

    private final String ns = ""
        + "xmlns='http://easy.dans.knaw.nl/schemas/bag/metadata/files/' "
        + "xmlns:dcterms='http://purl.org/dc/terms/' "
        + "xmlns:afm='http://easy.dans.knaw.nl/schemas/bag/metadata/afm/'";

    @Test
    void toFileMetadata_should_include_metadata_from_child_elements() throws Exception {
        var doc = readDocumentFromString(String.format(""
                + "<file filepath='data/leeg.txt' %s>\n"
                + "    <dcterms:format>text/plain</dcterms:format>\n"
                + "    <dcterms:hardware>Hardware</dcterms:hardware>\n"
                + "    <dcterms:description>Empty file</dcterms:description>\n"
                + "    <dcterms:time_period>Classical</dcterms:time_period>\n"
                + "</file>",ns));

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);

        assertEquals("leeg.txt", result.getLabel());
        assertEquals(" ", result.getDirectoryLabel());
        assertEquals("description: \"Empty file\"; time_period: \"Classical\"; hardware: \"Hardware\"", result.getDescription());
        assertEquals(true, result.getRestricted());
    }

    @Test
    void toFileMetadata_should_strip_data_prefix_from_path_to_get_directoryLabel() throws Exception {
        var doc = readDocumentFromString(String.format(""
                + "    <file filepath='data/this/is/the/directory/label/leeg.txt' %s>\n"
                + "    </file>",ns));

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("leeg.txt", result.getLabel());
        assertEquals("this/is/the/directory/label", result.getDirectoryLabel());
        assertTrue(result.getRestricted());
    }

    @Test
    void toFileMetadata_should_require_path_starting_with_data() throws Exception {
        var doc = readDocumentFromString(String.format(""
            + "    <file filepath='/this/is/the/directory/label/leeg.txt' %s>"
            + "    </file>",ns));

        assertThatThrownBy(() ->  FileElement.toFileMeta(doc.getDocumentElement(), true))
            .isInstanceOf(RuntimeException.class) // TODO shouldn't this be something like InvalidPathException?
            .hasMessage("file outside data folder: /this/is/the/directory/label/leeg.txt");
    }

    @Test
    void toFileMetadata_plain_description_CIT004() throws Exception {
        var doc = readDocumentFromString(String.format(""
            + "    <file filepath='data/this/is/the/directory/label/leeg.txt' %s>"
            + "         <dcterms:description>Empty file</dcterms:description>\n"
            + "    </file>",ns));

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("leeg.txt", result.getLabel());
        assertEquals("this/is/the/directory/label", result.getDirectoryLabel());
        assertEquals("Empty file", result.getDescription());
        assertTrue(result.getRestricted());
    }

    @Test
    void toFileMetadata_should_represent_keyvalue_pairs_in_the_description() throws Exception {
        String filePath = "data/this/is/the/directory/label/leeg.txt";
        var doc = readDocumentFromString(String.format(""
            + "<file filepath='%s' %s>"
            + "    <afm:othmat_codebook>FOTOBEST.csv; FOTOLST.csv</afm:othmat_codebook>"
            + "    <afm:keyvaluepair>"
            + "        <afm:key>FOTONR</afm:key>"
            + "        <afm:value>3</afm:value>"
            + "    </afm:keyvaluepair>"
            + "</file>", filePath, ns)
        );

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("leeg.txt", result.getLabel());
        assertEquals("this/is/the/directory/label", result.getDirectoryLabel());
        assertTrue(result.getRestricted());
        assertEquals("othmat_codebook: \"FOTOBEST.csv; FOTOLST.csv\"; FOTONR: \"3\"", result.getDescription()); // FIL002A/B (migration only)
    }

    @Test
    void toFileMetadata_should_include_original_filepath_if_directoryLabel_or_label_change_during_sanitation() throws Exception {
        String filePath = "data/directory/path/with/&lt;for'bidden&gt;/(chars)/strange?filename*.txt";
        String s = String.format("<file filepath=\"%s\" %s></file>", filePath, ns);
        var doc = readDocumentFromString(s);

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("strange_filename_.txt", result.getLabel());
        assertEquals("directory/path/with/_for_bidden_/_chars_", result.getDirectoryLabel());
        assertTrue(result.getRestricted());
        assertEquals("original_filepath: \"directory/path/with/<for'bidden>/(chars)/strange?filename*.txt\"", result.getDescription());
    }

    @Test
    void toFileMetadata_should_NOT_include_original_filepath_if_directoryLabel_or_label_stay_unchanged_during_sanitation() throws Exception {
        String filePath = "data/directory/path/with/all/legal/chars/normal_filename.txt";
        var doc = readDocumentFromString(String.format(
            "<file filepath='%s' %s></file>", filePath, ns));
        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("normal_filename.txt", result.getLabel());
        assertEquals("directory/path/with/all/legal/chars", result.getDirectoryLabel());
        assertTrue(result.getRestricted());
        assertNull(result.getDescription());
    }

    @Test
    void toFileMetadata_should_only_replace_nonASCII_chars_in_directory_names_during_sanitization() throws Exception {
        var originalFilePath = "data/directory/path/with/all/leg\u00e5l/chars/n\u00f8rmal_filename.txt";
        var doc = readDocumentFromString(String.format(
            "<file filepath='%s' %s></file>", originalFilePath, ns));

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("n\u00f8rmal_filename.txt", result.getLabel());
        assertEquals("directory/path/with/all/leg_l/chars", result.getDirectoryLabel());
        assertTrue(result.getRestricted());
        assertEquals("original_filepath: \"directory/path/with/all/leg\u00e5l/chars/n\u00f8rmal_filename.txt\"", result.getDescription());
    }

    @Test
    void FIL001_toFileMetadata_should_replace_each_forbidden_char_in_filename_with_underscore() throws Exception {
        /*
        Replace forbidden chars with underscore. (Forbidden chars are:
        : (colon)
        * (asterisk)
        ? (question mark)
        "" (double quote)
        < (lower than)
        > (greater than)
        | (pipe)
        ; (semicolon)
        # (hash)
        */
        // note that there are 7 invalid characters between 'test' and '.txt'
        var filename = "test**::?>>.txt";
        var filePath = "data/directory/path/with/all/leg\u00e5l/chars/" + filename;
        var doc = readDocumentFromString(String.format(
            "<file filepath='%s' %s></file>", filePath, ns));

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("directory/path/with/all/leg_l/chars", result.getDirectoryLabel());
        assertEquals("test_______.txt", result.getLabel());
    }

    @Test
    void FIL002_toFileMetadata_should_replace_each_forbidden_char_in_path_with_underscore() throws Exception {
        /*
        Replace forbidden chars with underscore. Only the following characters are allowed:
        alphanumeric chars (only ASCII)
        / slash
        \ backslash
        . dot
        - hyphen
         space (but not tab)
        */
        var filename = "dir()\t\t ^^^/xyz/\\a.b-c";
        var doc = readDocumentFromString(String.format(
            "<file filepath='data/%s/fil^e.txt' %s></file>", filename, ns));

        var result = FileElement.toFileMeta(doc.getDocumentElement(), true);
        assertEquals("dir__   ___/xyz/\\a.b-c", result.getDirectoryLabel());
        assertEquals("fil^e.txt", result.getLabel());
    }

    @Test
    void FIL006() throws Exception {
        var xmlReader = new XmlReaderImpl().getFactory().newDocumentBuilder();
        var deposit = new Deposit();
        Path bagDir = Paths.get("src/test/resources/examples/valid-easy-submitted/example-bag-medium");
        deposit.setBagDir(bagDir);
        deposit.setBag(new BagReader().read(bagDir));
        deposit.setFilesXml(xmlReader.parse(bagDir.resolve("metadata/files.xml").toFile()));
        deposit.setDdm(xmlReader.parse(bagDir.resolve("metadata/dataset.xml").toFile()));

        // FIL001 - F005
        var files = XPathEvaluator.nodes(deposit.getFilesXml(), "/files:files/files:file")
            .map(node -> toFileMeta(node, true))
            .collect(Collectors.toList());
        assertThat(files).hasSize(5);
        String expected = "This description will be archived, but not displayed anywhere in the Web-UI";
        assertThat(files.get(1).getDescription()).isEqualTo(expected);

        // FIL006
        var fileInfoMap = FileElement.pathToFileInfo(deposit);
        assertThat(fileInfoMap).hasSize(5);
        assertThat(fileInfoMap.get(Paths.get("data/random images/image01.png")).getMetadata())
            .hasFieldOrPropertyWithValue("label","image01.png")
            .hasFieldOrPropertyWithValue("directoryLabel","random images")
            .hasFieldOrPropertyWithValue("restricted",true)
            .hasFieldOrPropertyWithValue("description",expected);
    }
}