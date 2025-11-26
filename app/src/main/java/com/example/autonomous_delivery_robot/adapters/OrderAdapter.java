package com.example.autonomous_delivery_robot.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autonomous_delivery_robot.R;
import com.example.autonomous_delivery_robot.models.Order;
import com.example.autonomous_delivery_robot.models.User;

import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private List<Order> orderList;
    private Context context;
    private OnOrderClickListener listener;

    // Interface for click events
    public interface OnOrderClickListener {
        void onOrderClick(Order order, int position);
    }

    public OrderAdapter(Context context, List<Order> orderList) {
        this.context = context;
        this.orderList = orderList;
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);

        holder.tvItem.setText(order.getItem());
        holder.tvStatus.setText(order.getStatus());

        // Handle Location object
        if (order.getLocation() != null) {
            String locationText = order.getLocation().getName();
            User locationUser = order.getLocation().getUser();

            if (locationUser != null) {
                locationText += " (Owner: " + locationUser.getEmail() + ")";
            }

            holder.tvLocation.setText(locationText);
        } else {
            holder.tvLocation.setText("No location");
        }

        // Handle Path object
        if (order.getPath() != null) {
            holder.tvPath.setText(order.getPath().getPathName());
        } else {
            holder.tvPath.setText("No path");
        }

        // Handle User object
        if (order.getUser() != null) {
            holder.tvUser.setText(order.getUser().getEmail() + " (" + order.getUser().getRole() + ")");
        } else {
            holder.tvUser.setText("No user");
        }

        holder.tvStartDate.setText(order.getStartDate());
        holder.tvEndDate.setText(order.getEndDate());

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOrderClick(order, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList != null ? orderList.size() : 0;
    }

    // Update data
    public void updateOrders(List<Order> newOrders) {
        this.orderList = newOrders;
        notifyDataSetChanged();
    }

    // Add a single order
    public void addOrder(Order order) {
        orderList.add(order);
        notifyItemInserted(orderList.size() - 1);
    }

    // ViewHolder class
    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvItem, tvStatus, tvLocation, tvPath, tvUser, tvStartDate, tvEndDate;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);

            // Initialize TextViews
            tvItem = itemView.findViewById(R.id.tv_item);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvPath = itemView.findViewById(R.id.tv_path);
            tvUser = itemView.findViewById(R.id.tv_user);
            tvStartDate = itemView.findViewById(R.id.tv_start_date);
            tvEndDate = itemView.findViewById(R.id.tv_end_date);
        }
    }
}