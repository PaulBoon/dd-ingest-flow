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
package nl.knaw.dans.ingest.core;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class Inbox implements Managed {
    private static final Logger log = LoggerFactory.getLogger(Inbox.class);
    private final Path inboxDir;
    private final DatasetEditorMap datasetEditorMap;

    public Inbox(Path path, DatasetEditorMap datasetEditorMap) {
        this.inboxDir = path;
        this.datasetEditorMap = datasetEditorMap;
    }

    @Override
    public void start() throws Exception {
        log.trace("Starting inbox {}", inboxDir);
        List<Path> files = Files.list(inboxDir).filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        log.debug("Found files: {}", files);
        for (Path f : files) {
            Thread.sleep(20);
            datasetEditorMap.enqueueDeposit(new Deposit(f));
        }
    }

    @Override
    public void stop() throws Exception {
        log.trace("Stopping inbox {}", inboxDir);
    }
}
