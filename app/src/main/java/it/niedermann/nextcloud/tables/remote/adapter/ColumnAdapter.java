package it.niedermann.nextcloud.tables.remote.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import it.niedermann.nextcloud.tables.database.entity.Column;
import it.niedermann.nextcloud.tables.model.types.EDataType;
import it.niedermann.nextcloud.tables.remote.util.TablesSerializationUtil;

public class ColumnAdapter {

    private final TablesSerializationUtil util;

    public ColumnAdapter() {
        this(new TablesSerializationUtil());
    }

    private ColumnAdapter(@NonNull TablesSerializationUtil util) {
        this.util = util;
    }

    @Nullable
    public String serializeSelectionDefault(@NonNull Column column) {
        final var type = EDataType.findByColumn(column);

        if (type == EDataType.SELECTION_MULTI) {
            return util.serializeArray(column.getSelectionDefault());
        }

        return column.getSelectionDefault();
    }

    @Nullable
    public String deserializeSelectionDefault(@NonNull Column column) {
        final var type = EDataType.findByColumn(column);

        if (type == EDataType.SELECTION_MULTI) {
            final var selectionDefault = column.getSelectionDefault();

            if (selectionDefault == null) {
                return null;
            }

            if ("null".equals(selectionDefault)) {
                return null; // API returns this as String...
            }

            return util.deserializeArray(selectionDefault.replaceAll("\"", ""));
        }

        return column.getSelectionDefault();
    }
}
