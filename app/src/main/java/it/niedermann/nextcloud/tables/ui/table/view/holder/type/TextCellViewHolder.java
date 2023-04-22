package it.niedermann.nextcloud.tables.ui.table.view.holder.type;

import android.view.Gravity;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import it.niedermann.nextcloud.tables.database.entity.Column;
import it.niedermann.nextcloud.tables.database.entity.Data;
import it.niedermann.nextcloud.tables.databinding.TableviewCellBinding;
import it.niedermann.nextcloud.tables.ui.table.view.holder.CellViewHolder;

public class TextCellViewHolder extends CellViewHolder {

    private final TableviewCellBinding binding;

    public TextCellViewHolder(@NonNull TableviewCellBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
    }

    @Override
    public void bind(@NonNull Data data, @NonNull Column column) {
        final var subtype = column.getSubtype();

        switch (subtype) {
            case "long":
            case "line": {
                binding.data.setText(String.valueOf(data.getValue()));
                break;
            }
            default: {
                binding.data.setText(String.valueOf(data.getValue()));
            }
        }
        binding.data.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        binding.data.getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
        binding.data.requestLayout();
    }
}
