package com.gotye.bibo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.gotye.bibo.R;

import java.util.List;
import java.util.Map;

/**
 * Created by Michael.Ma on 2016/8/11.
 */
public class FilterAdapter extends BaseAdapter {

    protected List<Map<String, Object>> data;
    protected Context context;
    protected LayoutInflater inflater;
    protected int id;

    public FilterAdapter(Context context, List<Map<String, Object>> data,
                         int resourceId) {
        super();

        this.context    = context;
        this.data       = data;
        this.id 		= resourceId;

        inflater = LayoutInflater.from(context);
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public String select(int index) {
        int size = data.size();
        String name = null;
        if (index < size) {
            for (int i=0;i<size;i++) {
                Map<String, Object> item = data.get(i);
                item.put("selected", i == index);

                if (i == index) {
                    name = (String) item.get("filter_name");
                }
            }

            this.notifyDataSetChanged();
        }

        return name;
    }

    private class ViewHolder {
        public ImageView thumb		= null;
        public TextView title		= null;
        public ImageView selected   = null;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Map<String, Object> getItem(int position) {
        if (position >= data.size())
            return null;

        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = inflater.inflate(id, null);

            holder.thumb = (ImageView) convertView.findViewById(R.id.thumb);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.selected = (ImageView) convertView.findViewById(R.id.filter_thumb_selected_icon);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // 为holder中的title tip和img设置内容
        Map<String, Object> item = data.get(position);
        String title = (String) item.get("title");
        int thumb = (Integer)item.get("thumb");
        boolean selected = (Boolean)item.get("selected");
        holder.title.setText(title);
        holder.thumb.setImageResource(thumb);
        holder.selected.setVisibility(selected ? View.VISIBLE : View.GONE);

        // 注意 默认为返回null,必须得返回convertView视图
        return convertView;
    }
}
