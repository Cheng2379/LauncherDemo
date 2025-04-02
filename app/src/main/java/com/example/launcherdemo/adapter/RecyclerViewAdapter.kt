package com.example.launcherdemo.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

/**
 *
 * @author cheng
 * @since 2025/4/1
 */
class RecyclerViewAdapter<T>(
    private var context: Context,
    private var dataList: ArrayList<T>,
    private var layoutId: Int,
    private var callBack: (viewHolder: ViewHolder, position: Int) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateAll(newDataList: ArrayList<T>) {
        dataList.clear()
        dataList.addAll(newDataList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(layoutId, parent, false))
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        callBack(holder, position)
    }

    class MyViewHolder(itemView: View) : ViewHolder(itemView)
}