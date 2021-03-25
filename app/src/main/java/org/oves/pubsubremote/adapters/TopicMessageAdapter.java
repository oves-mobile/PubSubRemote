package org.oves.pubsubremote.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.oves.pubsubremote.R;
import org.oves.pubsubremote.models.TopicMessageModel;

import java.util.List;

import es.dmoral.toasty.Toasty;

public class TopicMessageAdapter extends RecyclerView.Adapter<TopicMessageAdapter.ViewHolder> implements View.OnClickListener {
    //Fields:
    private final List<TopicMessageModel> topicMessageModelList;
    private final Context context;

    //Constructor:
    public TopicMessageAdapter(List<TopicMessageModel> topicMessageModelList, Context context) {
        this.topicMessageModelList = topicMessageModelList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.topic_message_item_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TopicMessageModel topicMessageModel = topicMessageModelList.get(position);
        holder.topic.setText(topicMessageModel.getTopic());
        holder.message.setText(topicMessageModel.getMessage());

        //Set Tags
        holder.itemView.setTag(topicMessageModel);
    }

    @Override
    public int getItemCount() {
        return topicMessageModelList.size();
    }

    @Override
    public void onClick(View view) {
        TopicMessageModel topicMessageModel = (TopicMessageModel) view.getTag();
        String topicMessage = topicMessageModel.getMessage();
        //Toast
        Toasty.success(context.getApplicationContext(), topicMessage, Toasty.LENGTH_SHORT).show();
    }

    //ViewHolder
    public class ViewHolder extends RecyclerView.ViewHolder {
        //Variables
        private final TextView topic;
        private final TextView message;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            //Init views
            topic = itemView.findViewById(R.id.topic);
            message = itemView.findViewById(R.id.message);
        }
    }
}
