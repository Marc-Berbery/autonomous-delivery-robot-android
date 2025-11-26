package com.example.autonomous_delivery_robot.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.models.Location;

import java.util.List;

public class LocationAdapter extends ArrayAdapter<Location> {

    private final Context context;
    private final List<Location> locations;

    public LocationAdapter(Context context, List<Location> locations) {
        super(context, R.layout.item_location, locations);
        this.context = context;
        this.locations = locations;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_location, parent, false);

            holder = new ViewHolder();
            holder.tvLocationName = convertView.findViewById(R.id.tvLocationName);
            holder.tvOwner = convertView.findViewById(R.id.tvOwner);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Location location = locations.get(position);

        holder.tvLocationName.setText(location.getName());

        if (location.getUser() != null) {
            holder.tvOwner.setText("Owner: " + location.getUser().getEmail());
        } else {
            holder.tvOwner.setText("No owner");
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvLocationName;
        TextView tvOwner;
    }
}