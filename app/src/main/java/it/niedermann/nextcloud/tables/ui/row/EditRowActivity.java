package it.niedermann.nextcloud.tables.ui.row;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collection;

import it.niedermann.nextcloud.tables.R;
import it.niedermann.nextcloud.tables.database.entity.Account;
import it.niedermann.nextcloud.tables.database.entity.Row;
import it.niedermann.nextcloud.tables.database.entity.Table;
import it.niedermann.nextcloud.tables.databinding.ActivityEditRowBinding;
import it.niedermann.nextcloud.tables.model.types.EDataType;
import it.niedermann.nextcloud.tables.ui.exception.ExceptionHandler;

public class EditRowActivity extends AppCompatActivity {

    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_TABLE = "table";
    private static final String KEY_ROW = "row";
    private Account account;
    private Table table;
    private Row row;
    private EditRowViewModel editRowViewModel;
    private ActivityEditRowBinding binding;
    private final Collection<ColumnEditView> editors = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

        final var intent = getIntent();

        if (intent == null || !intent.hasExtra(KEY_ACCOUNT) || !intent.hasExtra(KEY_TABLE)) {
            throw new IllegalArgumentException(KEY_ACCOUNT + " and " + KEY_TABLE + " must be provided.");
        }

        this.account = (Account) intent.getSerializableExtra(KEY_ACCOUNT);
        this.table = (Table) intent.getSerializableExtra(KEY_TABLE);
        this.row = (Row) intent.getSerializableExtra(KEY_ROW);

        binding = ActivityEditRowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        binding.toolbar.setTitle(row == null ? R.string.add_row : R.string.edit_row);
        binding.toolbar.setSubtitle(table.getTitleWithEmoji());

        editRowViewModel = new ViewModelProvider(this).get(EditRowViewModel.class);
        editRowViewModel.getNotDeletedColumns(table).thenAcceptAsync(columns -> {
            binding.columns.removeAllViews();
            editors.clear();
            editRowViewModel.getData(row).thenAcceptAsync(values -> {
                for (final var column : columns) {
                    final var type = EDataType.findByType(column.getType(), column.getSubtype());
                    final var editor = type.createEditor(this, column, values.get(column.getId()), getSupportFragmentManager());
                    binding.columns.addView(editor);
                    editors.add(editor);
                }
            }, ContextCompat.getMainExecutor(this));
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_row, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.save) {
            if (row == null) {
                editRowViewModel.createRow(account, table, editors);
            } else {
                editRowViewModel.updateRow(account, row, editors);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static Intent createIntent(@NonNull Context context, @NonNull Account account, @NonNull Table table) {
        return new Intent(context, EditRowActivity.class)
                .putExtra(KEY_ACCOUNT, account)
                .putExtra(KEY_TABLE, table);
    }

    public static Intent createIntent(@NonNull Context context, @NonNull Account account, @NonNull Table table, @NonNull Row row) {
        return createIntent(context, account, table)
                .putExtra(KEY_ROW, row);
    }
}
