/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.database;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.system.Os;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.dao.DeletedMessageDao;
import com.radolyn.ayugram.database.dao.EditedMessageDao;
import com.radolyn.ayugram.database.dao.LastSeenDao;
import com.radolyn.ayugram.messages.AyuMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.IOException;

import top.nkbe.niagram.helpers.AppRestartHelper;
import top.nkbe.niagram.settings.NekoExperimentalSettingsActivity;
import top.nkbe.niagram.utils.AndroidUtil;

public class AyuData {
    private static final String IMPORT_DATABASE = AyuConstants.AYU_DATABASE + "-import";

    public static long dbSize, attachmentsSize, totalSize;
    private static AyuDatabase database;
    private static EditedMessageDao editedMessageDao;
    private static DeletedMessageDao deletedMessageDao;
    private static LastSeenDao lastSeenDao;

    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_messageId ON deletedmessage(userId, dialogId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_topicId_messageId ON deletedmessage(userId, dialogId, topicId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_replyMessageId_messageId ON deletedmessage(userId, dialogId, replyMessageId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_userId_dialogId_groupedId_messageId ON deletedmessage(userId, dialogId, groupedId, messageId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessage_dialogId ON deletedmessage(dialogId)");

            database.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_userId_dialogId_messageId_entityCreateDate ON editedmessage(userId, dialogId, messageId, entityCreateDate)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_userId_entityCreateDate ON editedmessage(userId, entityCreateDate)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_editedmessage_dialogId_messageId ON editedmessage(dialogId, messageId)");

            database.execSQL("CREATE INDEX IF NOT EXISTS index_deletedmessagereaction_deletedMessageId ON deletedmessagereaction(deletedMessageId)");
        }
    };

    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyQuote INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyQuoteText TEXT");
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyQuoteEntities BLOB");
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyFromSerialized BLOB");

            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyQuote INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyQuoteText TEXT");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyQuoteEntities BLOB");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyFromSerialized BLOB");
        }
    };

    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN replyMarkupSerialized BLOB");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN replyMarkupSerialized BLOB");
        }
    };

    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE DeletedMessage ADD COLUMN forwards INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE EditedMessage ADD COLUMN forwards INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS LastSeenEntity (userId INTEGER NOT NULL, lastSeen INTEGER NOT NULL, PRIMARY KEY(userId))");
        }
    };

    static {
        create();
    }

    public static synchronized void create() {
        database = createDatabase(AyuConstants.AYU_DATABASE);

        editedMessageDao = database.editedMessageDao();
        deletedMessageDao = database.deletedMessageDao();
        lastSeenDao = database.lastSeenDao();
    }

    private static AyuDatabase createDatabase(String name) {
        return Room.databaseBuilder(ApplicationLoader.applicationContext, AyuDatabase.class, name)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addMigrations(MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26)
                .build();
    }

    public static AyuDatabase getDatabase() {
        return database;
    }

    public static EditedMessageDao getEditedMessageDao() {
        return editedMessageDao;
    }

    public static DeletedMessageDao getDeletedMessageDao() {
        return deletedMessageDao;
    }

    public static LastSeenDao getLastSeenDao() {
        return lastSeenDao;
    }

    public static synchronized void clean() {
        if (database != null) {
            try {
                database.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        clearReferences();
        ApplicationLoader.applicationContext.deleteDatabase(AyuConstants.AYU_DATABASE);
    }

    private static void clearReferences() {
        database = null;
        editedMessageDao = null;
        deletedMessageDao = null;
        lastSeenDao = null;
    }

    public static void importAyuDatabase(BaseFragment fragment, File importFile) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider());
        progressDialog.setCanCancel(false);
        progressDialog.show();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                importDatabase(importFile);
                AndroidUtilities.runOnUIThread(() -> {
                    Context context = ApplicationLoader.applicationContext;
                    AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class));
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    if (fragment.getParentActivity() != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
                    }
                });
            }
        });
    }

    private static synchronized void importDatabase(File sourceFile) throws Exception {
        if (!sourceFile.isFile()) {
            throw new IOException("Ayu database import file does not exist");
        }

        Context context = ApplicationLoader.applicationContext;
        File importFile = context.getDatabasePath(IMPORT_DATABASE);
        File databaseFile = context.getDatabasePath(AyuConstants.AYU_DATABASE);

        context.deleteDatabase(IMPORT_DATABASE);

        try {
            if (!AndroidUtilities.copyFile(sourceFile, importFile)) {
                throw new IOException("Failed to stage Ayu database");
            }
            validateImportDatabase(importFile);

            AyuDatabase importDatabase = createDatabase(IMPORT_DATABASE);
            try {
                SupportSQLiteDatabase imported = importDatabase.getOpenHelper().getWritableDatabase();
                try (Cursor cursor = imported.query("PRAGMA wal_checkpoint(FULL)")) {
                    if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                        throw new IOException("Ayu database checkpoint is busy");
                    }
                }
            } finally {
                importDatabase.close();
            }

            checkpointDatabase();
            try {
                database.close();
            } finally {
                clearReferences();
            }
            deleteSidecars(databaseFile);
            Os.rename(importFile.getAbsolutePath(), databaseFile.getAbsolutePath());
        } catch (Exception e) {
            if (database == null) {
                try {
                    create();
                    database.getOpenHelper().getWritableDatabase();
                    AyuMessagesController.getInstance().refreshDaos();
                } catch (Exception restoreError) {
                    e.addSuppressed(restoreError);
                }
            }
            throw e;
        } finally {
            context.deleteDatabase(IMPORT_DATABASE);
        }
    }

    private static void validateImportDatabase(File importFile) throws IOException {
        try (SQLiteDatabase imported = SQLiteDatabase.openDatabase(importFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            if (!imported.isDatabaseIntegrityOk()) {
                throw new IOException("Ayu database integrity check failed");
            }
            int version = imported.getVersion();
            if (version < AyuDatabase.MIN_SUPPORTED_VERSION || version > AyuDatabase.VERSION) {
                throw new IOException("Unsupported Ayu database version: " + version);
            }
        }
    }

    private static void deleteSidecars(File databaseFile) throws IOException {
        String[] suffixes = {"-journal", "-shm", "-wal"};
        for (String suffix : suffixes) {
            File sidecar = new File(databaseFile.getAbsolutePath() + suffix);
            if (sidecar.exists() && !sidecar.delete()) {
                throw new IOException("Failed to delete Ayu database sidecar");
            }
        }
    }

    public static synchronized void checkpointDatabase() throws IOException {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)")) {
            if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                throw new IOException("Ayu database checkpoint is busy");
            }
        }
    }

    public static long getDatabaseSize() {
        long size = 0;
        try {
            File dbFile = ApplicationLoader.applicationContext.getDatabasePath(AyuConstants.AYU_DATABASE);
            File shmCacheFile = new File(dbFile.getAbsolutePath() + "-shm");
            File walCacheFile = new File(dbFile.getAbsolutePath() + "-wal");
            if (dbFile.exists()) {
                size = dbFile.length();
            }
            if (shmCacheFile.exists()) {
                size += shmCacheFile.length();
            }
            if (walCacheFile.exists()) {
                size += walCacheFile.length();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return size;
    }

    public static long getAttachmentsDirSize() {
        long size = 0;
        try {
            if (AyuMessagesController.attachmentsPath.exists()) {
                size = AndroidUtil.getDirectorySize(AyuMessagesController.attachmentsPath);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return size;
    }

    public static void loadSizes(Runnable callback) {
        Utilities.globalQueue.postRunnable(() -> {
            dbSize = getDatabaseSize();
            attachmentsSize = getAttachmentsDirSize();
            totalSize = dbSize + attachmentsSize;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback, 500);
            }
        });
    }
}
