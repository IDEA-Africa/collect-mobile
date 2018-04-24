package org.openforis.collect.android.collectadapter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openforis.collect.android.Settings;
import org.openforis.collect.android.viewmodel.*;
import org.openforis.collect.io.SurveyBackupInfo;
import org.openforis.collect.io.SurveyBackupJob;
import org.openforis.collect.io.data.BackupDataExtractor;
import org.openforis.collect.io.data.RecordFileBackupTask;
import org.openforis.collect.manager.RecordFileManager;
import org.openforis.collect.manager.SurveyManager;
import org.openforis.collect.model.CollectRecord;
import org.openforis.collect.model.CollectSurvey;
import org.openforis.collect.model.User;
import org.openforis.collect.persistence.xml.DataMarshaller;
import org.openforis.idm.model.FileAttribute;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Daniel Wiell
 */
public class SurveyExporter {
    private static final Logger LOG = Logger.getLogger(SurveyExporter.class.getName());
    private final UiSurvey uiSurvey;
    private final CollectSurvey collectSurvey;
    private final SurveyManager surveyManager;
    private final boolean excludeBinaries;
    private final CollectRecordProvider collectRecordProvider;
    private final RecordFileManager recordFileManager;
    private final DataMarshaller dataMarshaller;
    private ZipOutputStream zipOutputStream;

    public SurveyExporter(UiSurvey uiSurvey, CollectSurvey collectSurvey, SurveyManager surveyManager, boolean excludeBinaries, CollectRecordProvider collectRecordProvider, RecordFileManager recordFileManager) throws IOException {
        this.uiSurvey = uiSurvey;
        this.collectSurvey = collectSurvey;
        this.surveyManager = surveyManager;
        this.excludeBinaries = excludeBinaries;
        this.collectRecordProvider = collectRecordProvider;
        this.recordFileManager = recordFileManager;
        dataMarshaller = new DataMarshaller();
    }

    public void export(File outputFile) throws IOException, AllRecordKeysNotSpecified {
        assertAllRecordKeysSpecified();
        try {
            if (!outputFile.getParentFile().exists())
                outputFile.getParentFile().mkdirs();
            else
                removeOldExportFiles(outputFile);
            zipOutputStream = new ZipOutputStream(new FileOutputStream(outputFile));
            addInfoFile();
            addIdmFile();
            exportRecords();
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
        }
    }

    private void removeOldExportFiles(File outputFile) {
        for (File file : outputFile.getParentFile().listFiles()) {
            String suffix = "_\\d{4}\\-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}.collect-data";
            String prefix = outputFile.getName().replaceFirst(suffix, "");
            if (file.getName().replaceFirst(suffix, "").equals(prefix))
                file.delete();
        }
    }

    private void assertAllRecordKeysSpecified() {
        for (UiNode rc : uiSurvey.getChildren())
            for (UiNode rp : ((UiRecordCollection) rc).getChildren())
                for (UiAttribute keyAttribute : ((UiRecord.Placeholder) rp).getKeyAttributes())
                    if (keyAttribute.isEmpty())
                        throw new AllRecordKeysNotSpecified();
    }

    private void addInfoFile() throws IOException {
        try {
            zipOutputStream.putNextEntry(new ZipEntry(SurveyBackupJob.INFO_FILE_NAME));
            SurveyBackupInfo info = new SurveyBackupInfo();
            info.setSurveyName(collectSurvey.getName());
            info.setSurveyUri(collectSurvey.getUri());
            info.store(zipOutputStream);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    private void addIdmFile() throws IOException {
        try {
            zipOutputStream.putNextEntry(new ZipEntry(SurveyBackupJob.SURVEY_XML_ENTRY_NAME));
            surveyManager.marshalSurvey(collectSurvey, zipOutputStream, false, false, false);
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    private void exportRecords() throws IOException {
        Date now = new Date();
        User user = Settings.user();
        for (UiNode rc : uiSurvey.getChildren()) {
            UiRecordCollection recordCollection = (UiRecordCollection) rc;
            for (UiNode rp : recordCollection.getChildren()) {
                try {
                    UiRecord.Placeholder recordPlaceholder = (UiRecord.Placeholder) rp;
                    CollectRecord record = collectRecordProvider.record(recordPlaceholder.getId());
                    record.setCreatedBy(user);
                    record.setCreationDate(now);
                    record.setModifiedBy(user);
                    record.setModifiedDate(now);
                    record.setOwner(user);
                    exportRecord(record);
                    if (!excludeBinaries)
                        exportRecordFiles(record);
                } catch (Exception e) {
                    throw new IOException(String.format("Error exporting record %s with id %d: %s",
                            ((UiRecord.Placeholder) rp).getKeyAttributes(), rp.getId(), e.getMessage()), e);
                }
            }
        }
    }

    private void exportRecordFiles(CollectRecord record) throws IOException {
        List<FileAttribute> fileAttributes = record.getFileAttributes();
        for (FileAttribute fileAttribute : fileAttributes) {
            if (!fileAttribute.isEmpty() && fileAttribute.getFilename() != null) {
                File file = recordFileManager.getRepositoryFile(fileAttribute);
                if (file == null || !file.exists()) {
                    LOG.log(Level.WARNING, String.format("Record file not found for record %s (%d) attribute %s (%d)",
                            StringUtils.join(record.getRootEntityKeyValues(), ','), record.getId(), fileAttribute.getPath(), fileAttribute.getInternalId()));
                } else {
                    String entryName = RecordFileBackupTask.determineRecordFileEntryName(fileAttribute);
                    writeFile(file, entryName);
                }
            }
        }
    }

    private void writeFile(File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOutputStream.putNextEntry(entry);
        IOUtils.copy(new FileInputStream(file), zipOutputStream);
        zipOutputStream.closeEntry();
        zipOutputStream.flush();
    }

    private void exportRecord(CollectRecord record) throws IOException {
        BackupDataExtractor.BackupRecordEntry recordEntry = new BackupDataExtractor.BackupRecordEntry(CollectRecord.Step.CLEANSING, record.getId());
        ZipEntry entry = new ZipEntry(recordEntry.getName());
        try {
            zipOutputStream.putNextEntry(entry);
            OutputStreamWriter writer = new OutputStreamWriter(zipOutputStream);
            try {
                dataMarshaller.write(record, writer);
            } catch (XmlPullParserException e) {
                throw new IllegalStateException(e);
            }
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    interface CollectRecordProvider {
        CollectRecord record(int recordId);
    }

    public static class AllRecordKeysNotSpecified extends RuntimeException {

    }
}
