package com.example.autonomous_delivery_robot.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.models.Path;

import java.util.List;

public class PathAdapter extends ArrayAdapter<Path> {

    private final Context context;
    private final List<Path> paths;

    public PathAdapter(Context context, List<Path> paths) {
        super(context, R.layout.item_path, paths);
        this.context = context;
        this.paths = paths;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_path, parent, false);

            holder = new ViewHolder();
            holder.tvPathName = convertView.findViewById(R.id.tvPathName);
            holder.tvLocation = convertView.findViewById(R.id.tvLocation);
            holder.tvInstructions = convertView.findViewById(R.id.tvInstructions);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Path path = paths.get(position);

        holder.tvPathName.setText(path.getPathName());

        if (path.getLocation() != null) {
            holder.tvLocation.setText("Location: " + path.getLocation().getName());
        } else {
            holder.tvLocation.setText("No location");
        }

        holder.tvInstructions.setText(path.getInstructionSet());

        return convertView;
    }

    private static class ViewHolder {
        TextView tvPathName;
        TextView tvLocation;
        TextView tvInstructions;
    }
}