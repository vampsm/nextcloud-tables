package it.niedermann.nextcloud.tables.repository.sync;

import static java.util.stream.Collectors.toUnmodifiableSet;

import android.util.Log;

import androidx.annotation.NonNull;

import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import it.niedermann.nextcloud.tables.database.DBStatus;
import it.niedermann.nextcloud.tables.database.TablesDatabase;
import it.niedermann.nextcloud.tables.database.entity.AbstractRemoteEntity;
import it.niedermann.nextcloud.tables.database.entity.Account;
import it.niedermann.nextcloud.tables.database.entity.Data;
import it.niedermann.nextcloud.tables.database.entity.Row;
import it.niedermann.nextcloud.tables.remote.adapter.DataAdapter;
import it.niedermann.nextcloud.tables.remote.api.TablesAPI;

public class RowSyncAdapter extends AbstractSyncAdapter {

    private static final String TAG = RowSyncAdapter.class.getSimpleName();
    private final DataAdapter dataAdapter;

    public RowSyncAdapter(@NonNull TablesDatabase db) {
        this(db, new DataAdapter());
    }

    private RowSyncAdapter(@NonNull TablesDatabase db,
                           @NonNull DataAdapter dataAdapter) {
        super(db);
        this.dataAdapter = dataAdapter;
    }

    @Override
    public void pushLocalChanges(@NonNull TablesAPI api, @NonNull Account account) throws IOException, NextcloudHttpRequestFailedException {
        final var rowsToDelete = db.getRowDao().getRows(account.getId(), DBStatus.LOCAL_DELETED);
        Log.v(TAG, "Pushing " + rowsToDelete.size() + " local row deletions for " + account.getAccountName());
        for (final var row : rowsToDelete) {
            Log.i(TAG, "→ DELETE: " + row.getRemoteId());
            final var remoteId = row.getRemoteId();
            if (remoteId == null) {
                db.getRowDao().delete(row);
            } else {
                final var response = api.deleteRow(row.getRemoteId()).execute();
                Log.i(TAG, "-→ HTTP " + response.code());
                if (response.isSuccessful()) {
                    db.getRowDao().delete(row);
                } else {
                    throw new NextcloudHttpRequestFailedException(response.code(), new RuntimeException("Could not delete row " + row.getRemoteId()));
                }
            }
        }

        final var rowsToUpdate = db.getRowDao().getRows(account.getId(), DBStatus.LOCAL_EDITED);
        Log.v(TAG, "Pushing " + rowsToDelete.size() + " local row changes for " + account.getAccountName());

        for (final var row : rowsToUpdate) {
            Log.i(TAG, "→ PUT/POST: " + row.getRemoteId());
            final var dataset = db.getDataDao().getDataForRow(row.getId());
            final var columns = db.getColumnDao().getColumns(Arrays.stream(dataset).map(Data::getColumnId).collect(toUnmodifiableSet()));

            row.setData(dataset);

            final var response = row.getRemoteId() == null
                    // TODO perf: fetch all remoteIds at once
                    ? api.createRow(db.getTableDao().getRemoteId(row.getTableId()), dataAdapter.serialize(columns, row.getData())).execute()
                    : api.updateRow(row.getRemoteId(), dataAdapter.serialize(columns, row.getData())).execute();
            Log.i(TAG, "-→ HTTP " + response.code());
            if (response.isSuccessful()) {
                row.setStatus(DBStatus.VOID);
                row.setRemoteId(response.body().getRemoteId());
                db.getRowDao().update(row);
            } else {
                throw new NextcloudHttpRequestFailedException(response.code(), new RuntimeException("Could not push local changes for row " + row.getRemoteId()));
            }
        }
    }

    @Override
    public void pullRemoteChanges(@NonNull TablesAPI api, @NonNull Account account) throws IOException, NextcloudHttpRequestFailedException {
        for (final var table : db.getTableDao().getTables(account.getId())) {
            final var fetchedRows = new HashSet<Row>();
            int offset = 0;

            fetchRowsLoop:
            while (true) {
                Log.v(TAG, "Pulling remote rows for " + table.getTitle() + " (offset: " + offset + ")");
                final var request = api.getRows(table.getRemoteId(), TablesAPI.DEFAULT_API_LIMIT_ROWS, offset);
                final var response = request.execute();
                switch (response.code()) {
                    case 200: {
                        final var rows = response.body();
                        if (rows == null) {
                            throw new RuntimeException("Response body is null");
                        }

                        for (final var row : rows) {
                            row.setAccountId(table.getAccountId());
                            row.setTableId(table.getId());
                            row.setETag(response.headers().get(HEADER_ETAG));
                        }

                        fetchedRows.addAll(rows);

                        if (rows.size() != TablesAPI.DEFAULT_API_LIMIT_ROWS) {
                            break fetchRowsLoop;
                        }

                        offset += rows.size();

                        break;
                    }

                    case 304: {
                        Log.v(TAG, "Pull remote rows: HTTP " + response.code() + " Not Modified");
                        break;
                    }

                    default: {
                        throw new NextcloudHttpRequestFailedException(response.code(), new RuntimeException("Could not fetch rows for table with remote ID " + table.getRemoteId()));
                    }
                }
            }

            final var rowRemoteIds = fetchedRows.stream().map(AbstractRemoteEntity::getRemoteId).collect(toUnmodifiableSet());
            final var rowIds = db.getRowDao().getRowRemoteAndLocalIds(table.getAccountId(), rowRemoteIds);

            for (final var row : fetchedRows) {
                final var rowId = rowIds.get(row.getRemoteId());
                if (rowId == null) {
                    Log.i(TAG, "→ Adding " + table.getTitle() + " to database");
                    row.setId(db.getRowDao().insert(row));
                } else {
                    row.setId(rowId);
                    Log.i(TAG, "→ Updating row " + row.getRemoteId() + " in database");
                    db.getRowDao().update(row);
                }

                final var columnRemoteIds = Arrays.stream(row.getData()).map(Data::getRemoteColumnId).collect(toUnmodifiableSet());
                final var columnIds = db.getColumnDao().getColumnRemoteAndLocalIds(table.getAccountId(), columnRemoteIds);
                final var columns = db.getColumnDao().getColumns(columnIds.values());

                for (final var data : row.getData()) {
                    final var columnId = columnIds.get(data.getRemoteColumnId());
                    if (columnId == null) {
                        Log.w(TAG, "Could not find remoteColumnId " + data.getRemoteColumnId() + ". Probably this column has been deleted but its data is still being responded by the server (See https://github.com/nextcloud/tables/issues/257)");
                    } else {
                        data.setAccountId(table.getAccountId());
                        data.setRowId(row.getId());
                        data.setColumnId(columnId);

                        final var type = dataAdapter.getTypeForData(columns, data);
                        data.setValue(dataAdapter.deserialize(type, data.getValue()));

                        final var existingData = db.getDataDao().getDataForCoordinates(data.getColumnId(), data.getRowId());
                        if (existingData == null) {
                            db.getDataDao().insert(data);
                        } else {
                            data.setId(existingData.getId());
                            db.getDataDao().update(data);
                        }

                        // Data deletion is handled by database constraints
                    }
                }
            }

            Log.i(TAG, "→ Delete all rows except remoteId " + rowRemoteIds);
            db.getRowDao().deleteExcept(table.getId(), rowRemoteIds);
        }
    }
}
